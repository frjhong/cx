package com.cx.airouter.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.cx.airouter.config.AgentRoutingProperties;
import com.cx.airouter.support.StubHttpServer;
import com.cx.airouter.support.StubHttpServer.StubResponse;
import com.cx.airouter.support.TestProperties;
import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/** Agent 呼叫：逾時、有限重試、副作用保護、並行與錯誤隔離。 */
class AgentInvokerTest {

    private StubHttpServer stub;
    private ExecutorService executor;
    private AgentRegistry registry;

    @BeforeEach
    void setUp() {
        stub = StubHttpServer.start();
        executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    @AfterEach
    void tearDown() {
        stub.close();
        executor.close();
    }

    private AgentInvoker invoker(boolean idempotent, int maxRetries, String financeUrl) {
        AgentRoutingProperties props = TestProperties.mock();
        for (AgentRoutingProperties.AgentProperties a : props.getAgents()) {
            a.setTimeout(Duration.ofMillis(500));
            a.setConnectTimeout(Duration.ofMillis(500));
            a.setIdempotent(idempotent);
            a.setMaxRetries(maxRetries);
            a.setUrl(a.getId().equals("FINANCE") ? financeUrl : stub.baseUrl() + "/production");
        }
        RestClient.Builder rb = RestClient.builder();
        registry = new AgentRegistry(props,
                List.of(new FinanceAgentAdapter(rb), new ProductionAgentAdapter(rb), new GenericHttpAgentAdapter(rb)));
        return new AgentInvoker(registry, executor);
    }

    private AgentDefinition agent(String id) {
        return registry.find(id).orElseThrow();
    }

    @Test
    @DisplayName("逾時：非冪等 Agent 不重試，避免重複執行副作用")
    void timeoutNotRetriedWhenNotIdempotent() {
        stub.on("/finance", req -> StubResponse.json("{\"answer\":\"slow\"}").delayed(1500));
        AgentInvoker invoker = invoker(false, 2, stub.baseUrl() + "/finance");
        AgentCallResult r = invoker.invoke(agent("FINANCE"), "req-1", "q", "user");
        assertThat(r.status()).isEqualTo(AgentCallStatus.TIMEOUT);
        assertThat(r.attempts()).isEqualTo(1);
        assertThat(stub.requests("/finance")).hasSize(1);
    }

    @Test
    @DisplayName("逾時：冪等 Agent 有限重試（max-retries=2 → 共 3 次）")
    void timeoutRetriedWhenIdempotent() {
        stub.on("/finance", req -> StubResponse.json("{\"answer\":\"slow\"}").delayed(1500));
        AgentInvoker invoker = invoker(true, 2, stub.baseUrl() + "/finance");
        AgentCallResult r = invoker.invoke(agent("FINANCE"), "req-2", "q", "user");
        assertThat(r.status()).isEqualTo(AgentCallStatus.TIMEOUT);
        assertThat(r.attempts()).isEqualTo(3);
    }

    @Test
    @DisplayName("5xx：冪等 Agent 重試後成功，重試使用相同 Idempotency-Key")
    void serverErrorRetriedThenSucceeds() {
        AtomicInteger count = new AtomicInteger();
        stub.on("/finance", req -> count.incrementAndGet() == 1
                ? StubResponse.status(503, "{}")
                : StubResponse.json("{\"answer\":\"ok\"}"));
        AgentInvoker invoker = invoker(true, 1, stub.baseUrl() + "/finance");
        AgentCallResult r = invoker.invoke(agent("FINANCE"), "req-3", "q", "user");
        assertThat(r.status()).isEqualTo(AgentCallStatus.SUCCESS);
        assertThat(r.attempts()).isEqualTo(2);
        assertThat(stub.requests("/finance")).extracting(x -> x.header("Idempotency-Key"))
                .containsOnly("req-3:FINANCE");
    }

    @Test
    @DisplayName("5xx：非冪等 Agent 不重試")
    void serverErrorNotRetriedWhenNotIdempotent() {
        stub.on("/finance", req -> StubResponse.status(500, "{}"));
        AgentInvoker invoker = invoker(false, 2, stub.baseUrl() + "/finance");
        AgentCallResult r = invoker.invoke(agent("FINANCE"), "req-4", "q", "user");
        assertThat(r.status()).isEqualTo(AgentCallStatus.ERROR);
        assertThat(stub.requests("/finance")).hasSize(1);
    }

    @Test
    @DisplayName("連線失敗：請求確定未送達，即使非冪等也可安全重試")
    void connectFailureRetried() {
        AgentInvoker invoker = invoker(false, 1, "http://127.0.0.1:" + closedPort() + "/finance");
        AgentCallResult r = invoker.invoke(agent("FINANCE"), "req-5", "q", "user");
        assertThat(r.status()).isEqualTo(AgentCallStatus.ERROR);
        assertThat(r.errorCode()).isEqualTo("AGENT_CONNECT_FAILURE");
        assertThat(r.attempts()).isEqualTo(2);
    }

    @Test
    @DisplayName("回應格式不符：不重試")
    void invalidResponse() {
        stub.on("/finance", req -> StubResponse.json("{\"unexpected\":true}"));
        AgentInvoker invoker = invoker(true, 2, stub.baseUrl() + "/finance");
        AgentCallResult r = invoker.invoke(agent("FINANCE"), "req-6", "q", "user");
        assertThat(r.errorCode()).isEqualTo("AGENT_INVALID_RESPONSE");
        assertThat(stub.requests("/finance")).hasSize(1);
    }

    @Test
    @DisplayName("並行：兩個各需 400ms 的 Agent，總耗時接近單一 Agent；一方失敗不影響另一方")
    void parallelAndIsolated() {
        stub.on("/finance", req -> StubResponse.json("{\"answer\":\"f\"}").delayed(400));
        stub.on("/production", req -> StubResponse.status(400, "{}").delayed(400));
        AgentInvoker invoker = invoker(true, 0, stub.baseUrl() + "/finance");
        long start = System.nanoTime();
        List<AgentCallResult> results = invoker.invokeAll(
                List.of(agent("FINANCE"), agent("PRODUCTION")), "req-7", "q", "user");
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        assertThat(results).extracting(AgentCallResult::status).containsExactly(AgentCallStatus.SUCCESS, AgentCallStatus.ERROR);
        assertThat(elapsedMs).isLessThan(780);
    }

    @Test
    @DisplayName("重試判斷規則")
    void retryRules() {
        assertThat(AgentInvoker.isRetryable(AgentInvocationException.Kind.CONNECT_FAILURE, false)).isTrue();
        assertThat(AgentInvoker.isRetryable(AgentInvocationException.Kind.TIMEOUT, false)).isFalse();
        assertThat(AgentInvoker.isRetryable(AgentInvocationException.Kind.TIMEOUT, true)).isTrue();
        assertThat(AgentInvoker.isRetryable(AgentInvocationException.Kind.CLIENT_ERROR, true)).isFalse();
    }

    private static int closedPort() {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
