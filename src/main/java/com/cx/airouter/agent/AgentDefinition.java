package com.cx.airouter.agent;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Set;

/**
 * 經過驗證、不可變的 Agent 定義（由 {@link AgentRegistry} 依設定檔建立）。
 *
 * @param uri 已通過允許清單驗證的 Agent API 位址
 */
public record AgentDefinition(
        String id,
        String name,
        String description,
        URI uri,
        String adapterType,
        Duration timeout,
        Duration connectTimeout,
        int maxRetries,
        boolean idempotent,
        Set<String> requiredRoles,
        String apiKey,
        String apiKeyHeader,
        List<String> routingHints) {

    @Override
    public String toString() {
        // 避免 apiKey 進入日誌
        return "AgentDefinition[id=" + id + ", name=" + name + ", host=" + uri.getHost() + "]";
    }
}
