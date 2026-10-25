package com.cx.airouter.security;

import com.cx.airouter.config.AgentRoutingProperties;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 機敏內容偵測（身分證字號、信用卡號、機密標記等，規則由設定檔管理）。
 * 偵測結果用於決定是否可將問題送往外部模型。
 */
@Component
public class SensitiveContentDetector {

    private final PatternMatcher matcher;

    public SensitiveContentDetector(AgentRoutingProperties properties) {
        this.matcher = new PatternMatcher(properties.getDataProtection().getSensitivePatterns());
    }

    /** @return 符合的規則名稱；未偵測到時為 empty */
    public Optional<String> detect(String question) {
        return matcher.firstMatch(question);
    }
}
