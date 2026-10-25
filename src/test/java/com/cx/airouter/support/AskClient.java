package com.cx.airouter.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/** 以真實 HTTP 呼叫 POST /api/ai/ask 的測試用戶端。 */
public final class AskClient {

    public record Result(int status, JsonNode body) {
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().proxy(HttpClient.Builder.NO_PROXY).connectTimeout(Duration.ofSeconds(5)).build();
    private final String baseUrl;

    public AskClient(int port) {
        this.baseUrl = "http://localhost:" + port;
    }

    public Result ask(String question, String apiKey) {
        try {
            return post("/api/ai/ask", MAPPER.writeValueAsString(Map.of("question", question)), apiKey);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public Result post(String path, String json, String apiKey) {
        try {
            HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json));
            if (apiKey != null) {
                req.header("X-API-Key", apiKey);
            }
            HttpResponse<String> res = http.send(req.build(), HttpResponse.BodyHandlers.ofString());
            JsonNode body = res.body() == null || res.body().isBlank() ? MAPPER.nullNode() : MAPPER.readTree(res.body());
            return new Result(res.statusCode(), body);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
