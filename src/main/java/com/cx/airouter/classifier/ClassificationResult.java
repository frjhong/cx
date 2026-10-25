package com.cx.airouter.classifier;

import com.cx.airouter.config.ClassifierType;
import java.util.List;
import java.util.Map;

/**
 * 分類器原始結果（尚未套用信心門檻與允許清單驗證）。
 *
 * @param classifier          實際產生結果的分類器
 * @param selectedOption      分類器選出的選項值；refused 時為 null
 * @param confidence          信心值（Decisions：API 回傳 confidence；Responses：模型自評，未經校準）
 * @param refused             模型拒絕回答
 * @param optionProbabilities 各選項機率（Decisions API 提供）
 * @param agentRelevance      各 Agent「需要此 Agent」的機率（多 Agent 判斷用，可空）
 * @param suggestedAgents     分類器建議的 Agent 清單（Responses / Mock 提供，可空）
 * @param inputTokens         輸入 Token 數（可為 null）
 */
public record ClassificationResult(
        ClassifierType classifier,
        String selectedOption,
        Double confidence,
        boolean refused,
        Map<String, Double> optionProbabilities,
        Map<String, Double> agentRelevance,
        List<String> suggestedAgents,
        Long inputTokens) {

    public static ClassificationResult refusal(ClassifierType classifier, Long inputTokens) {
        return new ClassificationResult(classifier, null, null, true, Map.of(), Map.of(), List.of(), inputTokens);
    }
}
