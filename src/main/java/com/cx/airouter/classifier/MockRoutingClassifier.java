package com.cx.airouter.classifier;

import com.cx.airouter.config.AgentRoutingProperties;
import com.cx.airouter.config.ClassifierType;
import org.springframework.stereotype.Component;

/**
 * <b>Mock 路由分類器（非真實 AI 路由）</b>。
 *
 * <p>僅供沒有 OpenAI API Key 時示範與測試端到端流程。依 {@code ai-router.agents[].routing-hints}
 * 做字詞比對，回應中 {@code classifier} 會標示為 {@code MOCK}，{@code mockRouting} 為 true。</p>
 */
@Component
public class MockRoutingClassifier implements RoutingClassifier {

    private final AgentRoutingProperties properties;

    public MockRoutingClassifier(AgentRoutingProperties properties) {
        this.properties = properties;
    }

    @Override
    public ClassifierType type() {
        return ClassifierType.MOCK;
    }

    @Override
    public ClassificationResult classify(RoutingRequest request) {
        return HintScorer.score(type(), request, properties.getClassifier().getOtherHints());
    }
}
