package com.cx.airouter.mock;

import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <b>Mock Agent API</b>：模擬既有的財務／生產 Agent 系統，供沒有實際 Agent API 時進行端到端示範。
 *
 * <p>只在 {@code ai-router.mock-agents.enabled=true} 時啟用。回覆明確標示為 Mock，且不提供任何數據，
 * 避免示範資料被誤認為實際經營數字。</p>
 */
@RestController
@RequestMapping("/mock-agents")
@ConditionalOnProperty(prefix = "ai-router.mock-agents", name = "enabled", havingValue = "true")
public class MockAgentController {

    private static final Map<String, String> NAMES = Map.of(
            "finance", "財務 AI 特助",
            "production", "生產 AI 助理");

    @PostMapping("/{agent}")
    public ResponseEntity<Map<String, Object>> answer(@PathVariable String agent, @RequestBody Map<String, Object> body) {
        String name = NAMES.get(agent);
        if (name == null) {
            return ResponseEntity.notFound().build();
        }
        String requestId = String.valueOf(body.getOrDefault("requestId", ""));
        String answer = "[MOCK " + name + "] 已收到問題。此為 Mock Agent 示範回覆，未連接實際系統，因此不提供任何數據。";
        return ResponseEntity.ok(Map.of("answer", answer, "requestId", requestId, "mock", true));
    }
}
