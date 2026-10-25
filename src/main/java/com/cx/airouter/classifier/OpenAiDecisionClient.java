package com.cx.airouter.classifier;

import com.cx.airouter.agent.AgentDefinition;
import com.cx.airouter.config.AgentRoutingProperties;
import com.cx.airouter.config.ClassifierType;
import com.openai.client.OpenAIClient;
import com.openai.errors.OpenAIException;
import com.openai.models.decisions.Decision;
import com.openai.models.decisions.DecisionChoiceOption;
import com.openai.models.decisions.DecisionChoiceValue;
import com.openai.models.decisions.DecisionCreateParams;
import com.openai.models.decisions.DecisionInputItem;
import com.openai.models.decisions.DecisionInputMessage;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 以 OpenAI Decisions API（POST /v1/decisions）進行意圖分類。
 *
 * <p>使用官方 Java SDK {@code com.openai:openai-java} 4.91.0 的
 * {@code client.decisions().create(DecisionCreateParams)}。</p>
 *
 * <p>請求結構：</p>
 * <ul>
 *   <li>{@code input}：只包含一則 user 訊息（使用者問題），作為待分類資料。</li>
 *   <li>{@code questions[0]}：choice 問題 {@code route}，選項為允許清單內的 Agent ID + 多 Agent 標籤 + OTHER + UNCERTAIN。</li>
 *   <li>超過兩個 Agent 時，另加每個 Agent 一個 predicate 問題 {@code needs_<ID>}，用於判斷多 Agent 組合。</li>
 * </ul>
 *
 * <p>回應解析：choice 答案的 {@code choice}、{@code confidence}、{@code probabilities}；
 * predicate 答案的 {@code probability}；任何問題可能回傳 {@code refusal}；以及 {@code usage}。</p>
 */
@Component
public class OpenAiDecisionClient implements RoutingClassifier {

    static final String ROUTE_QUESTION = "route";

    private final ObjectProvider<OpenAIClient> clientProvider;
    private final RoutingPromptFactory promptFactory;
    private final AgentRoutingProperties properties;

    public OpenAiDecisionClient(ObjectProvider<OpenAIClient> clientProvider, RoutingPromptFactory promptFactory,
                                AgentRoutingProperties properties) {
        this.clientProvider = clientProvider;
        this.promptFactory = promptFactory;
        this.properties = properties;
    }

    @Override
    public ClassifierType type() {
        return ClassifierType.DECISIONS;
    }

    @Override
    public ClassificationResult classify(RoutingRequest request) {
        OpenAIClient client = clientProvider.getIfAvailable();
        if (client == null) {
            throw new ClassifierException(type(), "OPENAI_NOT_CONFIGURED", "未設定 OPENAI_API_KEY", false, null);
        }
        DecisionCreateParams params = buildParams(request);
        Decision decision;
        try {
            decision = client.decisions().create(params);
        } catch (OpenAIException e) {
            throw OpenAiErrors.translate(type(), e);
        }
        try {
            return parse(decision, request);
        } catch (OpenAIException e) {
            throw OpenAiErrors.translate(type(), e);
        }
    }

    DecisionCreateParams buildParams(RoutingRequest request) {
        DecisionCreateParams.Question.Choice.Builder choice = DecisionCreateParams.Question.Choice.builder()
                .name(ROUTE_QUESTION)
                .instructions(promptFactory.routeInstructions(request));
        for (RoutingPromptFactory.RouteOption option : promptFactory.options(request)) {
            choice.addChoice(DecisionChoiceOption.builder()
                    .value(option.value())
                    .description(option.description())
                    .build());
        }

        DecisionInputMessage userMessage = DecisionInputMessage.ofUser(
                DecisionInputMessage.User.builder().content(request.question()).build());

        DecisionCreateParams.Builder builder = DecisionCreateParams.builder()
                .model(properties.getOpenai().getDecisionsModel())
                .inputOfDecisionInputItems(List.of(DecisionInputItem.ofDecisionInputMessage(userMessage)))
                .addQuestion(choice.build());

        if (RoutingPromptFactory.needsAgentPredicates(request)) {
            for (AgentDefinition agent : request.candidates()) {
                builder.addQuestion(DecisionCreateParams.Question.Predicate.builder()
                        .name(RoutingPromptFactory.predicateName(agent))
                        .instructions(promptFactory.agentPredicateInstructions(agent))
                        .build());
            }
        }
        if (request.safetyIdentifier() != null) {
            builder.safetyIdentifier(request.safetyIdentifier());
        }
        return builder.build();
    }

    ClassificationResult parse(Decision decision, RoutingRequest request) {
        Long inputTokens = decision.usage().inputTokens();
        Map<String, String> predicateToAgent = new LinkedHashMap<>();
        for (AgentDefinition agent : request.candidates()) {
            predicateToAgent.put(RoutingPromptFactory.predicateName(agent), agent.id());
        }

        Decision.Answer.Choice routeAnswer = null;
        boolean routeRefused = false;
        Map<String, Double> relevance = new LinkedHashMap<>();
        for (Decision.Answer answer : decision.answers()) {
            if (answer.isChoice() && nameOf(answer.asChoice().name()).equals(ROUTE_QUESTION)) {
                routeAnswer = answer.asChoice();
            } else if (answer.isRefusal() && nameOf(answer.asRefusal().name()).equals(ROUTE_QUESTION)) {
                routeRefused = true;
            } else if (answer.isPredicate()) {
                String agentId = predicateToAgent.get(nameOf(answer.asPredicate().name()));
                if (agentId != null) {
                    relevance.put(agentId, answer.asPredicate().probability());
                }
            }
            // 其他（未知名稱或 predicate refusal）忽略：不影響路由
        }

        if (routeRefused) {
            return ClassificationResult.refusal(type(), inputTokens);
        }
        if (routeAnswer == null) {
            throw new ClassifierException(type(), "OPENAI_INVALID_RESPONSE", "Decisions 回應缺少 route 答案", true, null);
        }

        Map<String, Double> probabilities = new LinkedHashMap<>();
        for (Decision.Answer.Choice.Probability p : routeAnswer.probabilities()) {
            probabilities.put(valueOf(p.value()), p.probability());
        }
        return new ClassificationResult(type(), valueOf(routeAnswer.choice()), routeAnswer.confidence(), false,
                probabilities, relevance, List.of(), inputTokens);
    }

    private static String nameOf(Optional<String> name) {
        return name.orElse("");
    }

    /** 選項值有型別（字串或布林）；本系統只使用字串，布林轉為字串後必然不在允許清單中。 */
    private static String valueOf(DecisionChoiceValue value) {
        return value.isString() ? value.asString() : String.valueOf(value.asBool());
    }
}
