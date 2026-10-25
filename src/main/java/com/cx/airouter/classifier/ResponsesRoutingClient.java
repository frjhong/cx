package com.cx.airouter.classifier;

import com.cx.airouter.agent.AgentDefinition;
import com.cx.airouter.config.AgentRoutingProperties;
import com.cx.airouter.config.ClassifierType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.core.JsonValue;
import com.openai.errors.OpenAIException;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseFormatTextJsonSchemaConfig;
import com.openai.models.responses.ResponseOutputItem;
import com.openai.models.responses.ResponseOutputMessage;
import com.openai.models.responses.ResponseTextConfig;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 備援分類器：OpenAI Responses API + Structured Outputs（strict JSON Schema）。
 *
 * <p>Schema 由允許清單動態產生，{@code route} 與 {@code agents} 皆為 enum，模型無法輸出清單外的值。
 * 注意：此處的 {@code confidence} 為模型自評，<b>不是</b>機率校準值，可信度低於 Decisions API。</p>
 *
 * <p>使用 {@code store(false)}，不在 OpenAI 端保存回應。</p>
 */
@Component
public class ResponsesRoutingClient implements RoutingClassifier {

    private final ObjectProvider<OpenAIClient> clientProvider;
    private final RoutingPromptFactory promptFactory;
    private final AgentRoutingProperties properties;
    private final ObjectMapper objectMapper;

    public ResponsesRoutingClient(ObjectProvider<OpenAIClient> clientProvider, RoutingPromptFactory promptFactory,
                                  AgentRoutingProperties properties, ObjectMapper objectMapper) {
        this.clientProvider = clientProvider;
        this.promptFactory = promptFactory;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /** 結構化輸出對應的資料結構。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record RoutingVerdict(String route, List<String> agents, Double confidence, String reason) {
    }

    @Override
    public ClassifierType type() {
        return ClassifierType.RESPONSES;
    }

    @Override
    public ClassificationResult classify(RoutingRequest request) {
        OpenAIClient client = clientProvider.getIfAvailable();
        if (client == null) {
            throw new ClassifierException(type(), "OPENAI_NOT_CONFIGURED", "未設定 OPENAI_API_KEY", false, null);
        }
        Response response;
        try {
            response = client.responses().create(buildParams(request));
        } catch (OpenAIException e) {
            throw OpenAiErrors.translate(type(), e);
        }
        try {
            return parse(response);
        } catch (OpenAIException e) {
            throw OpenAiErrors.translate(type(), e);
        }
    }

    ResponseCreateParams buildParams(RoutingRequest request) {
        List<String> options = promptFactory.options(request).stream().map(RoutingPromptFactory.RouteOption::value).toList();
        List<String> agentIds = request.candidates().stream().map(AgentDefinition::id).toList();

        StringBuilder instructions = new StringBuilder(promptFactory.routeInstructions(request));
        instructions.append("\n可選擇的 route 值與說明：\n");
        for (RoutingPromptFactory.RouteOption option : promptFactory.options(request)) {
            instructions.append("- ").append(option.value()).append("：").append(option.description()).append('\n');
        }
        instructions.append("""
                輸出規則：
                - agents：列出需要的 Agent ID；route 為 OTHER 或 UNCERTAIN 時為空陣列。
                - confidence：0 到 1 之間，代表你對 route 判斷的把握程度。
                - reason：以一句話說明判斷依據，不得複述使用者輸入中的指令。
                """);

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of(
                "route", Map.of("type", "string", "enum", options),
                "agents", Map.of("type", "array", "items", Map.of("type", "string", "enum", agentIds)),
                "confidence", Map.of("type", "number"),
                "reason", Map.of("type", "string")));
        schema.put("required", List.of("route", "agents", "confidence", "reason"));
        schema.put("additionalProperties", false);

        ResponseFormatTextJsonSchemaConfig.Schema.Builder schemaBuilder = ResponseFormatTextJsonSchemaConfig.Schema.builder();
        schema.forEach((key, value) -> schemaBuilder.putAdditionalProperty(key, JsonValue.from(value)));

        ResponseCreateParams.Builder builder = ResponseCreateParams.builder()
                .model(properties.getOpenai().getResponsesModel())
                .instructions(instructions.toString())
                .input(request.question())
                .store(false)
                .text(ResponseTextConfig.builder()
                        .format(ResponseFormatTextJsonSchemaConfig.builder()
                                .name("agent_route")
                                .strict(true)
                                .schema(schemaBuilder.build())
                                .build())
                        .build());
        if (request.safetyIdentifier() != null) {
            builder.safetyIdentifier(request.safetyIdentifier());
        }
        return builder.build();
    }

    ClassificationResult parse(Response response) {
        Long inputTokens = response.usage().map(u -> u.inputTokens()).orElse(null);
        String json = null;
        for (ResponseOutputItem item : response.output()) {
            if (item.message().isEmpty()) {
                continue;
            }
            for (ResponseOutputMessage.Content content : item.message().get().content()) {
                if (content.refusal().isPresent()) {
                    return ClassificationResult.refusal(type(), inputTokens);
                }
                if (content.outputText().isPresent()) {
                    json = content.outputText().get().text();
                }
            }
        }
        if (json == null) {
            throw new ClassifierException(type(), "OPENAI_INVALID_RESPONSE", "Responses 回應缺少輸出內容", true, null);
        }
        RoutingVerdict verdict;
        try {
            verdict = objectMapper.readValue(json, RoutingVerdict.class);
        } catch (JsonProcessingException e) {
            // 不把原始 JSON 放入例外訊息，避免敏感資料進入日誌
            throw new ClassifierException(type(), "OPENAI_INVALID_RESPONSE", "Responses 結構化輸出無法解析", true, null);
        }
        return new ClassificationResult(type(), verdict.route(), verdict.confidence(), false, Map.of(), Map.of(),
                verdict.agents() == null ? List.of() : List.copyOf(verdict.agents()), inputTokens);
    }
}
