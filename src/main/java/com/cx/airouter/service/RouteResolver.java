package com.cx.airouter.service;

import com.cx.airouter.agent.AgentDefinition;
import com.cx.airouter.agent.AgentRegistry;
import com.cx.airouter.classifier.ClassificationResult;
import com.cx.airouter.config.AgentRoutingProperties;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 將分類器原始結果轉為路由決策。
 *
 * <p>安全原則：任何不確定情況（拒答、信心不足、信心值無效、選項不在允許清單）一律判定 UNCERTAIN，
 * 絕不「猜一個」Agent。</p>
 */
@Component
public class RouteResolver {

    private static final Logger log = LoggerFactory.getLogger(RouteResolver.class);

    private final AgentRegistry registry;
    private final AgentRoutingProperties.Classifier config;

    public RouteResolver(AgentRegistry registry, AgentRoutingProperties properties) {
        this.registry = registry;
        this.config = properties.getClassifier();
    }

    public RouteDecision resolve(ClassificationResult result, List<AgentDefinition> candidates) {
        if (result.refused()) {
            return uncertain(result, "分類模型拒絕判斷此問題");
        }
        Double confidence = result.confidence();
        if (confidence == null || confidence.isNaN() || confidence < 0 || confidence > 1) {
            return uncertain(result, "分類信心值無效");
        }
        String option = result.selectedOption();
        if (option == null || RouteLabels.UNCERTAIN.equals(option)) {
            return uncertain(result, "分類器判斷問題語意不明確");
        }
        if (confidence < config.getConfidenceThreshold()) {
            return uncertain(result, String.format(Locale.ROOT, "分類信心 %.2f 低於門檻 %.2f",
                    confidence, config.getConfidenceThreshold()));
        }
        if (RouteLabels.OTHER.equals(option)) {
            return new RouteDecision(RouteLabels.OTHER, RouteDecision.Kind.OTHER, List.of(), confidence,
                    result.classifier(), "問題不屬於任何 Agent 的職責範圍");
        }
        if (option.equals(config.getMultiRouteLabel())) {
            List<AgentDefinition> agents = multiAgents(result, candidates);
            if (agents.size() < 2) {
                return uncertain(result, "無法確定多 Agent 合作所需的 Agent 組合");
            }
            return new RouteDecision(option, RouteDecision.Kind.MULTI, agents, confidence, result.classifier(), null);
        }
        if (registry.isAllowed(option) && candidates.stream().anyMatch(a -> a.id().equals(option))) {
            return new RouteDecision(option, RouteDecision.Kind.SINGLE, List.of(registry.find(option).orElseThrow()),
                    confidence, result.classifier(), null);
        }
        // 模型輸出不在允許清單（理論上 choice / enum 已限制，此為防禦性檢查）
        log.warn("分類結果不在允許清單中，已拒絕路由 classifier={}", result.classifier());
        return uncertain(result, "分類結果不在允許清單中");
    }

    private List<AgentDefinition> multiAgents(ClassificationResult result, List<AgentDefinition> candidates) {
        if (candidates.size() == 2) {
            return candidates;
        }
        Map<String, Double> relevance = result.agentRelevance();
        if (!relevance.isEmpty()) {
            return candidates.stream()
                    .filter(a -> relevance.getOrDefault(a.id(), 0.0) >= config.getMultiAgentThreshold())
                    .toList();
        }
        return candidates.stream().filter(a -> result.suggestedAgents().contains(a.id())).toList();
    }

    private static RouteDecision uncertain(ClassificationResult result, String reason) {
        return new RouteDecision(RouteLabels.UNCERTAIN, RouteDecision.Kind.UNCERTAIN, List.of(), result.confidence(),
                result.classifier(), reason);
    }
}
