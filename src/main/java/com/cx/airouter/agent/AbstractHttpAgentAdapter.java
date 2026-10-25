package com.cx.airouter.agent;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * 以 HTTP + JSON 呼叫既有 Agent 的共用實作。
 *
 * <p>假設的預設契約（實際規格提供後由子類別覆寫 {@link #buildRequestBody} / {@link #extractAnswer}）：</p>
 * <pre>
 * POST {agent.url}
 * Headers: X-Request-Id, Idempotency-Key, X-Caller-Id, [Authorization]
 * Body:    {"requestId": "...", "question": "...", "callerId": "..."}
 * 回應:    {"answer": "..."}
 * </pre>
 */
public abstract class AbstractHttpAgentAdapter implements AgentAdapter {

    private final RestClient.Builder restClientBuilder;
    private final Map<String, RestClient> clients = new ConcurrentHashMap<>();

    protected AbstractHttpAgentAdapter(RestClient.Builder restClientBuilder) {
        this.restClientBuilder = restClientBuilder;
    }

    @Override
    public AgentResponse invoke(AgentDefinition agent, AgentRequest request) {
        RestClient client = clients.computeIfAbsent(agent.id(), id -> createClient(agent));
        JsonNode body;
        try {
            body = client.post()
                    .uri(agent.uri())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .headers(h -> {
                        h.set("X-Request-Id", request.requestId());
                        h.set("Idempotency-Key", request.idempotencyKey());
                        h.set("X-Caller-Id", request.callerId());
                        if (agent.apiKey() != null && !agent.apiKey().isBlank()) {
                            String header = agent.apiKeyHeader();
                            h.set(header, "Authorization".equalsIgnoreCase(header) ? "Bearer " + agent.apiKey() : agent.apiKey());
                        }
                    })
                    .body(buildRequestBody(agent, request))
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status >= 500 || status == 429 || status == 408) {
                throw new AgentInvocationException(AgentInvocationException.Kind.SERVER_ERROR, "Agent 回應 HTTP " + status, e);
            }
            throw new AgentInvocationException(AgentInvocationException.Kind.CLIENT_ERROR, "Agent 回應 HTTP " + status, e);
        } catch (ResourceAccessException e) {
            throw classifyIoFailure(e);
        } catch (RestClientException e) {
            throw new AgentInvocationException(AgentInvocationException.Kind.INVALID_RESPONSE, "Agent 回應無法解析", e);
        }
        String answer = body == null ? null : extractAnswer(agent, body);
        if (answer == null || answer.isBlank()) {
            throw new AgentInvocationException(AgentInvocationException.Kind.INVALID_RESPONSE, "Agent 回應缺少答案欄位");
        }
        return new AgentResponse(answer);
    }

    /** 建立送往 Agent 的請求內容；子類別可依實際規格覆寫。 */
    protected Object buildRequestBody(AgentDefinition agent, AgentRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("requestId", request.requestId());
        body.put("question", request.question());
        body.put("callerId", request.callerId());
        return body;
    }

    /** 由 Agent 回應取出答案文字；子類別可依實際規格覆寫。 */
    protected String extractAnswer(AgentDefinition agent, JsonNode body) {
        JsonNode answer = body.get("answer");
        return answer != null && answer.isTextual() ? answer.asText() : null;
    }

    private RestClient createClient(AgentDefinition agent) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(agent.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER) // 不跟隨轉址，避免被導向允許清單以外的位址
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(agent.timeout());
        return restClientBuilder.clone().requestFactory(factory).build();
    }

    private static AgentInvocationException classifyIoFailure(ResourceAccessException e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof HttpConnectTimeoutException || t instanceof ConnectException || t instanceof UnknownHostException) {
                return new AgentInvocationException(AgentInvocationException.Kind.CONNECT_FAILURE, "無法連線至 Agent", e);
            }
            if (t instanceof HttpTimeoutException || t instanceof SocketTimeoutException) {
                return new AgentInvocationException(AgentInvocationException.Kind.TIMEOUT, "Agent 回應逾時", e);
            }
        }
        return new AgentInvocationException(AgentInvocationException.Kind.SERVER_ERROR, "Agent 連線發生 I/O 錯誤", e);
    }
}
