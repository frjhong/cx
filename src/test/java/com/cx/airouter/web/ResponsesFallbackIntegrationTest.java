package com.cx.airouter.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.cx.airouter.support.AskClient;
import com.cx.airouter.support.OpenAiStubs;
import com.cx.airouter.support.StubHttpServer;
import com.cx.airouter.support.StubHttpServer.StubResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Decisions API 暫時不可用時，依設定切換至 Responses API + Structured Outputs（Mock 測試）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("mock")
class ResponsesFallbackIntegrationTest {

    static final StubHttpServer OPENAI = StubHttpServer.start();
    static final StubHttpServer AGENTS = StubHttpServer.start();
    static final ObjectMapper MAPPER = new ObjectMapper();

    @LocalServerPort
    int port;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("ai-router.classifier.primary", () -> "DECISIONS");
        r.add("ai-router.classifier.fallback", () -> "RESPONSES");
        r.add("ai-router.openai.api-key", () -> "test-key");
        r.add("ai-router.openai.base-url", () -> OPENAI.baseUrl() + "/v1");
        r.add("ai-router.openai.max-retries", () -> 0);
        r.add("agent-endpoints.finance-url", () -> AGENTS.baseUrl() + "/finance");
        r.add("agent-endpoints.production-url", () -> AGENTS.baseUrl() + "/production");
    }

    @AfterAll
    static void stop() {
        OPENAI.close();
        AGENTS.close();
    }

    @BeforeEach
    void reset() {
        OPENAI.reset();
        AGENTS.reset();
        AGENTS.on("/finance", req -> StubResponse.json("{\"answer\":\"財務 Agent 回覆（stub）\"}"));
        AGENTS.on("/production", req -> StubResponse.json("{\"answer\":\"生產 Agent 回覆（stub）\"}"));
    }

    private AskClient.Result ask(String q) {
        return new AskClient(port).ask(q, "demo-all-key");
    }

    @Test
    @DisplayName("Decisions 回傳 403（功能未開通）→ 自動改用 Responses Structured Outputs")
    void fallbackToResponses() throws Exception {
        OPENAI.on("/v1/decisions", req -> StubResponse.status(403, OpenAiStubs.error("feature not enabled")));
        OPENAI.on("/v1/responses", req -> StubResponse.json(OpenAiStubs.responsesOutput(
                "{\"route\":\"PRODUCTION\",\"agents\":[\"PRODUCTION\"],\"confidence\":0.9,\"reason\":\"詢問產量\"}")));

        AskClient.Result r = ask("昨天生產多少噸？");
        assertThat(r.status()).isEqualTo(200);
        JsonNode b = r.body();
        assertThat(b.get("route").asText()).isEqualTo("PRODUCTION");
        assertThat(b.get("classifier").asText()).isEqualTo("RESPONSES");
        assertThat(b.get("warnings").get(0).asText()).contains("備援").contains("OPENAI_HTTP_403");

        JsonNode sent = MAPPER.readTree(OPENAI.requests("/v1/responses").getFirst().body());
        assertThat(sent.get("model").asText()).isEqualTo("gpt-6-luna");
        assertThat(sent.get("store").asBoolean()).isFalse();
        assertThat(sent.get("input").asText()).isEqualTo("昨天生產多少噸？");
        assertThat(sent.get("instructions").asText()).contains("待分類的資料").doesNotContain("昨天生產多少噸");
        JsonNode format = sent.get("text").get("format");
        assertThat(format.get("type").asText()).isEqualTo("json_schema");
        assertThat(format.get("strict").asBoolean()).isTrue();
        assertThat(format.get("schema").get("additionalProperties").asBoolean()).isFalse();
        assertThat(format.get("schema").get("properties").get("route").get("enum").toString())
                .isEqualTo("[\"FINANCE\",\"PRODUCTION\",\"BOTH\",\"OTHER\",\"UNCERTAIN\"]");
    }

    @Test
    @DisplayName("Decisions 回傳 401（金鑰錯誤）→ 不切換備援，直接 503")
    void noFallbackOnUnauthorized() {
        OPENAI.on("/v1/decisions", req -> StubResponse.status(401, OpenAiStubs.error("bad key")));
        AskClient.Result r = ask("昨天生產多少噸？");
        assertThat(r.status()).isEqualTo(503);
        assertThat(OPENAI.requests("/v1/responses")).isEmpty();
    }

    @Test
    @DisplayName("兩者皆失敗 → 503 CLASSIFIER_UNAVAILABLE")
    void bothFail() {
        OPENAI.on("/v1/decisions", req -> StubResponse.status(503, OpenAiStubs.error("down")));
        OPENAI.on("/v1/responses", req -> StubResponse.status(500, OpenAiStubs.error("down")));
        AskClient.Result r = ask("昨天生產多少噸？");
        assertThat(r.status()).isEqualTo(503);
        assertThat(r.body().get("errorCode").asText()).isEqualTo("CLASSIFIER_UNAVAILABLE");
    }

    @Test
    @DisplayName("Responses 結構化輸出無法解析 → 503")
    void responsesInvalidJson() {
        OPENAI.on("/v1/decisions", req -> StubResponse.status(500, OpenAiStubs.error("down")));
        OPENAI.on("/v1/responses", req -> StubResponse.json(OpenAiStubs.responsesOutput("not json")));
        assertThat(ask("昨天生產多少噸？").status()).isEqualTo(503);
    }

    @Test
    @DisplayName("Responses 自評信心低於門檻 → UNCERTAIN")
    void responsesLowConfidence() {
        OPENAI.on("/v1/decisions", req -> StubResponse.status(500, OpenAiStubs.error("down")));
        OPENAI.on("/v1/responses", req -> StubResponse.json(OpenAiStubs.responsesOutput(
                "{\"route\":\"FINANCE\",\"agents\":[\"FINANCE\"],\"confidence\":0.5,\"reason\":\"不確定\"}")));
        JsonNode b = ask("那個怎麼樣？").body();
        assertThat(b.get("route").asText()).isEqualTo("UNCERTAIN");
        assertThat(AGENTS.requests("/finance")).isEmpty();
    }
}
