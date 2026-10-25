package com.cx.airouter.classifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cx.airouter.agent.AgentDefinition;
import com.cx.airouter.agent.AgentRegistry;
import com.cx.airouter.agent.FinanceAgentAdapter;
import com.cx.airouter.agent.GenericHttpAgentAdapter;
import com.cx.airouter.agent.ProductionAgentAdapter;
import com.cx.airouter.config.AgentRoutingProperties;
import com.cx.airouter.service.RouteDecision;
import com.cx.airouter.service.RouteResolver;
import com.cx.airouter.support.OpenAiStubs;
import com.cx.airouter.support.StubHttpServer;
import com.cx.airouter.support.StubHttpServer.StubResponse;
import com.cx.airouter.support.TestProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.web.client.RestClient;

/**
 * OpenAiDecisionClient：超過兩個 Agent 時的請求結構（choice + 每個 Agent 的 predicate）與多 Agent 判斷。
 */
class OpenAiDecisionClientTest {

    private StubHttpServer openai;
    private OpenAIClient sdk;
    private AgentRoutingProperties props;
    private AgentRegistry registry;
    private OpenAiDecisionClient client;

    @BeforeEach
    void setUp() {
        openai = StubHttpServer.start();
        sdk = OpenAIOkHttpClient.builder().apiKey("test-key").baseUrl(openai.baseUrl() + "/v1").maxRetries(0).build();
        props = TestProperties.mock();
        AgentRoutingProperties.AgentProperties quality = new AgentRoutingProperties.AgentProperties();
        quality.setId("QUALITY");
        quality.setName("品質 AI 助理");
        quality.setDescription("負責品質檢驗、不良率、客訴分析。");
        quality.setUrl("http://localhost:9000/quality");
        props.getAgents().add(quality);
        RestClient.Builder rb = RestClient.builder();
        registry = new AgentRegistry(props, List.of(new FinanceAgentAdapter(rb), new ProductionAgentAdapter(rb), new GenericHttpAgentAdapter(rb)));
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("openAIClient", sdk);
        client = new OpenAiDecisionClient(beans.getBeanProvider(OpenAIClient.class), new RoutingPromptFactory(), props);
    }

    @AfterEach
    void tearDown() {
        sdk.close();
        openai.close();
    }

    private RoutingRequest request(String q) {
        return new RoutingRequest("r1", q, registry.enabledAgents(), "BOTH", "u_test");
    }

    @Test
    @DisplayName("三個 Agent：一次請求內含 choice + 3 個 predicate；依 predicate 機率挑選多 Agent 組合")
    void multiAgentWithPredicates() throws Exception {
        openai.on("/v1/decisions", req -> StubResponse.json(OpenAiStubs.decision("BOTH", 0.9,
                Map.of("FINANCE", 0.93, "PRODUCTION", 0.88, "QUALITY", 0.12))));
        ClassificationResult result = client.classify(request("產量下降對公司獲利造成多少影響？"));

        JsonNode body = new ObjectMapper().readTree(openai.requests("/v1/decisions").getFirst().body());
        assertThat(body.get("questions")).hasSize(4);
        assertThat(body.get("questions").get(1).get("type").asText()).isEqualTo("predicate");
        assertThat(body.get("questions").get(1).get("name").asText()).isEqualTo("needs_FINANCE");
        assertThat(body.get("questions").get(0).get("choices")).hasSize(6); // 3 Agent + BOTH + OTHER + UNCERTAIN

        assertThat(result.agentRelevance()).containsEntry("QUALITY", 0.12);
        assertThat(result.inputTokens()).isEqualTo(180L);
        RouteDecision decision = new RouteResolver(registry, props).resolve(result, registry.enabledAgents());
        assertThat(decision.kind()).isEqualTo(RouteDecision.Kind.MULTI);
        assertThat(decision.agents()).extracting(AgentDefinition::id).containsExactly("FINANCE", "PRODUCTION");
    }

    @Test
    @DisplayName("選項機率與信心解析")
    void parsesProbabilities() {
        openai.on("/v1/decisions", req -> StubResponse.json(OpenAiStubs.decisionChoice("QUALITY", 0.81)));
        ClassificationResult result = client.classify(request("上週客訴率多少？"));
        assertThat(result.selectedOption()).isEqualTo("QUALITY");
        assertThat(result.confidence()).isEqualTo(0.81);
        assertThat(result.optionProbabilities()).containsEntry("QUALITY", 0.81);
    }

    @Test
    @DisplayName("429 / 連線失敗 → 可切換備援的 ClassifierException")
    void errorsAreFallbackEligible() {
        openai.on("/v1/decisions", req -> StubResponse.status(429, OpenAiStubs.error("rate limited")));
        assertThatThrownBy(() -> client.classify(request("q")))
                .isInstanceOfSatisfying(ClassifierException.class, e -> {
                    assertThat(e.errorCode()).isEqualTo("OPENAI_HTTP_429");
                    assertThat(e.fallbackEligible()).isTrue();
                });
    }

    @Test
    @DisplayName("未設定 API Key → OPENAI_NOT_CONFIGURED")
    void notConfigured() {
        OpenAiDecisionClient unconfigured = new OpenAiDecisionClient(
                new StaticListableBeanFactory().getBeanProvider(OpenAIClient.class), new RoutingPromptFactory(), props);
        assertThatThrownBy(() -> unconfigured.classify(request("q")))
                .isInstanceOfSatisfying(ClassifierException.class, e -> assertThat(e.errorCode()).isEqualTo("OPENAI_NOT_CONFIGURED"));
    }
}
