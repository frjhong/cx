package com.cx.airouter.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

/**
 * 啟動時檢查設定組合，設定錯誤時直接啟動失敗（fail-fast），避免上線後才發現無法路由。
 */
@Component
public class RouterStartupValidator implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(RouterStartupValidator.class);

    private final AgentRoutingProperties properties;

    public RouterStartupValidator(AgentRoutingProperties properties) {
        this.properties = properties;
    }

    @Override
    public void afterPropertiesSet() {
        AgentRoutingProperties.Classifier classifier = properties.getClassifier();
        boolean needsOpenAi = classifier.getPrimary().isExternal() || classifier.getFallback().isExternal();
        if (needsOpenAi && !properties.getOpenai().hasApiKey()) {
            throw new IllegalStateException("分類器設定為 " + classifier.getPrimary() + "/" + classifier.getFallback()
                    + "，但未設定環境變數 OPENAI_API_KEY。沒有 API Key 時請使用 mock profile（ai-router.classifier.primary=MOCK）。");
        }
        if (classifier.getPrimary() == ClassifierType.NONE) {
            throw new IllegalStateException("ai-router.classifier.primary 不可為 NONE");
        }
        if (classifier.getPrimary() == ClassifierType.MOCK) {
            log.warn("=== 目前使用 MOCK 路由分類器（規則式示範），不是真實 AI 路由；請勿用於正式環境 ===");
        }
        if (properties.getMockAgents().isEnabled()) {
            log.warn("=== 內建 Mock Agent API 已啟用（/mock-agents/**），回覆為示範內容；請勿用於正式環境 ===");
        }
        log.info("AI Router 分類器 primary={} fallback={} confidenceThreshold={}（示範值，未經校準）",
                classifier.getPrimary(), classifier.getFallback(), classifier.getConfidenceThreshold());
    }
}
