package com.cx.airouter.classifier;

import com.cx.airouter.agent.AgentDefinition;
import com.cx.airouter.config.ClassifierType;
import com.cx.airouter.service.RouteLabels;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 規則式提示詞計分（Mock 與內部分類器共用）。
 *
 * <p><b>限制：</b>此為字詞比對，無法理解語意，只用於示範或機敏內容不得外送時的保守替代方案；
 * 其信心值為規則常數，不是模型機率。</p>
 */
final class HintScorer {

    static final double SINGLE_BASE_CONFIDENCE = 0.80;
    static final double MULTI_CONFIDENCE = 0.85;
    static final double OTHER_CONFIDENCE = 0.90;
    static final double NO_MATCH_CONFIDENCE = 0.30;

    private HintScorer() {
    }

    static ClassificationResult score(ClassifierType type, RoutingRequest request, List<String> otherHints) {
        String text = request.question().toLowerCase(Locale.ROOT);
        Map<String, Integer> hits = new LinkedHashMap<>();
        for (AgentDefinition agent : request.candidates()) {
            int count = (int) agent.routingHints().stream()
                    .filter(h -> !h.isBlank() && text.contains(h.toLowerCase(Locale.ROOT)))
                    .count();
            if (count > 0) {
                hits.put(agent.id(), count);
            }
        }
        List<String> matched = new ArrayList<>(hits.keySet());
        if (matched.size() >= 2) {
            return result(type, request.multiRouteLabel(), MULTI_CONFIDENCE, matched);
        }
        if (matched.size() == 1) {
            String id = matched.getFirst();
            double confidence = Math.min(95, Math.round(SINGLE_BASE_CONFIDENCE * 100) + 5L * hits.get(id)) / 100.0;
            return result(type, id, confidence, matched);
        }
        boolean other = otherHints.stream().anyMatch(h -> !h.isBlank() && text.contains(h.toLowerCase(Locale.ROOT)));
        return other
                ? result(type, RouteLabels.OTHER, OTHER_CONFIDENCE, List.of())
                : result(type, RouteLabels.UNCERTAIN, NO_MATCH_CONFIDENCE, List.of());
    }

    private static ClassificationResult result(ClassifierType type, String option, double confidence, List<String> agents) {
        return new ClassificationResult(type, option, confidence, false, Map.of(), Map.of(), List.copyOf(agents), null);
    }
}
