package com.cx.airouter.security;

import com.cx.airouter.config.AgentRoutingProperties;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Prompt Injection 前置偵測（第一道防線）。
 *
 * <p>規則式偵測無法涵蓋所有攻擊手法，因此系統另有多層防護：路由規則只放在開發者控制的指示中、
 * 使用者問題只作為 user 資料傳入、分類輸出限制為固定選項、Agent 只來自允許清單、授權獨立檢查。</p>
 */
@Component
public class PromptInjectionGuard {

    private final PatternMatcher matcher;

    public PromptInjectionGuard(AgentRoutingProperties properties) {
        this.matcher = new PatternMatcher(properties.getPromptInjection().getPatterns());
    }

    /** @return 符合的規則名稱；未偵測到時為 empty */
    public Optional<String> detect(String question) {
        return matcher.firstMatch(question);
    }
}
