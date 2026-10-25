package com.cx.airouter.classifier;

import com.cx.airouter.config.ClassifierType;

/**
 * 路由分類器。實作只回傳「選項值」，不得產生網址或任何可執行內容。
 */
public interface RoutingClassifier {

    ClassifierType type();

    /**
     * @throws ClassifierException 分類服務不可用或回應異常
     */
    ClassificationResult classify(RoutingRequest request);
}
