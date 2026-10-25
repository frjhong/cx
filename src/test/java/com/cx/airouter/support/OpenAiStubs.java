package com.cx.airouter.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Map;

/**
 * 依官方 OpenAPI 規格（openai-java 4.91.0 api_reference/openapi.transformed.yml 的 DecisionResponse / Response）
 * 產生測試用回應 JSON。
 */
public final class OpenAiStubs {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private OpenAiStubs() {
    }

    /** choice 答案；probabilities 依 choice 與 confidence 推算示意分佈。 */
    public static String decisionChoice(String choice, double confidence) {
        return decision(choice, confidence, Map.of());
    }

    /** choice 答案 + 各 Agent 的 predicate 答案（needs_&lt;ID&gt;）。 */
    public static String decision(String choice, double confidence, Map<String, Double> agentPredicates) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("model", "gpt-6-luna");
        ArrayNode answers = root.putArray("answers");
        ObjectNode answer = answers.addObject();
        answer.put("type", "choice");
        answer.put("name", "route");
        answer.put("choice", choice);
        ArrayNode probs = answer.putArray("probabilities");
        probs.addObject().put("value", choice).put("probability", confidence);
        answer.put("confidence", confidence);
        agentPredicates.forEach((agentId, p) -> answers.addObject()
                .put("type", "predicate").put("name", "needs_" + agentId).put("probability", p));
        usage(root);
        return root.toString();
    }

    public static String decisionRefusal() {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("model", "gpt-6-luna");
        root.putArray("answers").addObject().put("type", "refusal").put("name", "route");
        usage(root);
        return root.toString();
    }

    /** Responses API 回應，output_text 為結構化 JSON。 */
    public static String responsesOutput(String outputJson) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("id", "resp_test");
        root.put("object", "response");
        root.put("created_at", 1761300000);
        root.put("model", "gpt-6-luna");
        root.put("status", "completed");
        ObjectNode message = root.putArray("output").addObject();
        message.put("type", "message");
        message.put("id", "msg_test");
        message.put("role", "assistant");
        message.put("status", "completed");
        message.putArray("content").addObject()
                .put("type", "output_text").put("text", outputJson).putArray("annotations");
        root.putObject("usage").put("input_tokens", 120).put("output_tokens", 30).put("total_tokens", 150)
                .<ObjectNode>set("input_tokens_details", MAPPER.createObjectNode().put("cached_tokens", 0))
                .set("output_tokens_details", MAPPER.createObjectNode().put("reasoning_tokens", 0));
        return root.toString();
    }

    public static String error(String message) {
        return "{\"error\":{\"message\":\"" + message + "\",\"type\":\"server_error\",\"code\":null,\"param\":null}}";
    }

    private static void usage(ObjectNode root) {
        ObjectNode usage = root.putObject("usage");
        usage.put("input_tokens", 180);
        usage.putObject("input_tokens_details").put("cached_tokens", 0).put("cache_write_tokens", 0);
        usage.put("output_tokens", 0);
        usage.putObject("output_tokens_details").put("reasoning_tokens", 0);
        usage.put("total_tokens", 180);
    }
}
