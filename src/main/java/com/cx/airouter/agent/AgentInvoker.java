package com.cx.airouter.agent;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * 統一處理 Agent 呼叫的逾時、有限重試、並行與錯誤隔離。
 *
 * <ul>
 *   <li>重試策略：連線失敗（請求確定未送達）一律可重試；逾時與 5xx 只在 Agent 標示為冪等時重試，
 *       避免重複執行具副作用的操作。4xx 與格式錯誤不重試。</li>
 *   <li>同一請求的所有重試使用相同的 Idempotency-Key。</li>
 *   <li>多 Agent 並行呼叫，任一 Agent 失敗不影響其他 Agent 的結果。</li>
 * </ul>
 */
@Component
public class AgentInvoker {

    private static final Logger log = LoggerFactory.getLogger(AgentInvoker.class);
    private static final Duration RETRY_BACKOFF = Duration.ofMillis(200);

    private final AgentRegistry registry;
    private final ExecutorService executor;

    public AgentInvoker(AgentRegistry registry, @Qualifier("agentExecutor") ExecutorService executor) {
        this.registry = registry;
        this.executor = executor;
    }

    /** 並行呼叫多個 Agent；回傳結果順序與輸入相同。 */
    public List<AgentCallResult> invokeAll(List<AgentDefinition> agents, String requestId, String question, String callerId) {
        if (agents.size() == 1) {
            return List.of(invoke(agents.getFirst(), requestId, question, callerId));
        }
        List<CompletableFuture<AgentCallResult>> futures = new ArrayList<>();
        for (AgentDefinition agent : agents) {
            futures.add(CompletableFuture
                    .supplyAsync(() -> invoke(agent, requestId, question, callerId), executor)
                    .completeOnTimeout(null, totalBudget(agent).toMillis(), TimeUnit.MILLISECONDS)
                    .exceptionally(ex -> null));
        }
        List<AgentCallResult> results = new ArrayList<>();
        for (int i = 0; i < agents.size(); i++) {
            AgentCallResult result = futures.get(i).join();
            if (result == null) {
                AgentDefinition agent = agents.get(i);
                result = new AgentCallResult(agent.id(), agent.name(), AgentCallStatus.TIMEOUT, null,
                        totalBudget(agent).toMillis(), 0, "AGENT_TIMEOUT");
            }
            results.add(result);
        }
        return results;
    }

    /** 呼叫單一 Agent（含有限重試），不會拋出例外。 */
    public AgentCallResult invoke(AgentDefinition agent, String requestId, String question, String callerId) {
        AgentAdapter adapter = registry.adapterFor(agent.id());
        AgentRequest request = new AgentRequest(requestId, question, callerId, requestId + ":" + agent.id());
        long start = System.nanoTime();
        int attempts = 0;
        AgentInvocationException last = null;
        while (attempts <= agent.maxRetries()) {
            attempts++;
            try {
                AgentResponse response = adapter.invoke(agent, request);
                return new AgentCallResult(agent.id(), agent.name(), AgentCallStatus.SUCCESS, response.answer(),
                        elapsedMs(start), attempts, null);
            } catch (AgentInvocationException e) {
                last = e;
                boolean retry = attempts <= agent.maxRetries() && isRetryable(e.kind(), agent.idempotent());
                log.warn("Agent 呼叫失敗 requestId={} agent={} attempt={} kind={} retry={}",
                        requestId, agent.id(), attempts, e.kind(), retry);
                if (!retry) {
                    break;
                }
                sleepQuietly(RETRY_BACKOFF.multipliedBy(attempts));
            } catch (RuntimeException e) {
                // 錯誤隔離：Adapter 的非預期錯誤不得影響其他 Agent 或整體請求
                log.error("Agent 呼叫發生非預期錯誤 requestId={} agent={} type={}", requestId, agent.id(), e.getClass().getSimpleName());
                return new AgentCallResult(agent.id(), agent.name(), AgentCallStatus.ERROR, null, elapsedMs(start), attempts, "AGENT_ERROR");
            }
        }
        boolean timeout = last != null && last.kind() == AgentInvocationException.Kind.TIMEOUT;
        return new AgentCallResult(agent.id(), agent.name(),
                timeout ? AgentCallStatus.TIMEOUT : AgentCallStatus.ERROR, null, elapsedMs(start), attempts,
                timeout ? "AGENT_TIMEOUT" : "AGENT_" + (last == null ? "ERROR" : last.kind().name()));
    }

    static boolean isRetryable(AgentInvocationException.Kind kind, boolean idempotent) {
        return switch (kind) {
            case CONNECT_FAILURE -> true;
            case TIMEOUT, SERVER_ERROR -> idempotent;
            case CLIENT_ERROR, INVALID_RESPONSE -> false;
        };
    }

    /** 單一 Agent（含重試）的總時間上限。 */
    static Duration totalBudget(AgentDefinition agent) {
        int attempts = agent.maxRetries() + 1;
        return agent.timeout().plus(agent.connectTimeout()).multipliedBy(attempts)
                .plus(RETRY_BACKOFF.multipliedBy((long) attempts * attempts))
                .plusSeconds(1);
    }

    private static long elapsedMs(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }

    private static void sleepQuietly(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
