package com.cx.airouter.classifier;

import com.cx.airouter.config.AgentRoutingProperties;
import com.cx.airouter.config.ClassifierType;
import org.springframework.stereotype.Component;

/**
 * 內部分類器：問題含機敏內容、且政策禁止送往外部模型時使用。
 *
 * <p>預設實作為規則式（與 Mock 相同演算法，但標示為 INTERNAL），結果偏保守；
 * 正式環境建議以企業內部部署的分類模型取代：實作 {@link RoutingClassifier} 並回傳
 * {@link ClassifierType#INTERNAL}，再移除此 Bean 即可。</p>
 */
@Component
public class InternalRoutingClassifier implements RoutingClassifier {

    private final AgentRoutingProperties properties;

    public InternalRoutingClassifier(AgentRoutingProperties properties) {
        this.properties = properties;
    }

    @Override
    public ClassifierType type() {
        return ClassifierType.INTERNAL;
    }

    @Override
    public ClassificationResult classify(RoutingRequest request) {
        return HintScorer.score(type(), request, properties.getClassifier().getOtherHints());
    }
}
