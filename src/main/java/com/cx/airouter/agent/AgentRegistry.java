package com.cx.airouter.agent;

import com.cx.airouter.config.AgentRoutingProperties;
import com.cx.airouter.service.RouteLabels;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Agent 允許清單。
 *
 * <p>系統只能呼叫此處登錄、且 URL 通過允許清單驗證的 Agent；
 * 分類模型只能回傳 Agent ID，網址永遠由此 Registry 依設定決定。</p>
 */
@Component
public class AgentRegistry {

    private static final Logger log = LoggerFactory.getLogger(AgentRegistry.class);

    private final Map<String, AgentDefinition> enabledAgents;
    private final Map<String, AgentAdapter> adaptersByAgentId;

    public AgentRegistry(AgentRoutingProperties properties, List<AgentAdapter> adapters) {
        Map<String, AgentAdapter> adaptersByType = adapters.stream()
                .collect(Collectors.toMap(AgentAdapter::type, Function.identity()));
        Set<String> reserved = Set.of(RouteLabels.OTHER, RouteLabels.UNCERTAIN, properties.getClassifier().getMultiRouteLabel());
        AgentRoutingProperties.AgentUrlPolicy urlPolicy = properties.getAgentUrlPolicy();

        Map<String, AgentDefinition> agents = new LinkedHashMap<>();
        Map<String, AgentAdapter> agentAdapters = new LinkedHashMap<>();
        Set<String> seen = new LinkedHashSet<>();
        for (AgentRoutingProperties.AgentProperties p : properties.getAgents()) {
            if (!seen.add(p.getId())) {
                throw new IllegalStateException("Agent ID 重複：" + p.getId());
            }
            if (reserved.contains(p.getId())) {
                throw new IllegalStateException("Agent ID 不可使用保留字：" + p.getId());
            }
            if (!p.isEnabled()) {
                log.info("Agent {} 已停用，不列入路由選項", p.getId());
                continue;
            }
            AgentAdapter adapter = adaptersByType.get(p.getAdapter());
            if (adapter == null) {
                throw new IllegalStateException("Agent " + p.getId() + " 指定的 adapter 不存在：" + p.getAdapter()
                        + "，可用：" + adaptersByType.keySet());
            }
            URI uri = validateUrl(p.getId(), p.getUrl(), urlPolicy);
            agents.put(p.getId(), new AgentDefinition(
                    p.getId(), p.getName(), p.getDescription(), uri, p.getAdapter(),
                    p.getTimeout(), p.getConnectTimeout(), p.getMaxRetries(), p.isIdempotent(),
                    Set.copyOf(p.getRequiredRoles()), p.getApiKey(), p.getApiKeyHeader(),
                    List.copyOf(p.getRoutingHints())));
            agentAdapters.put(p.getId(), adapter);
        }
        if (agents.isEmpty()) {
            throw new IllegalStateException("至少需要一個啟用中的 Agent（ai-router.agents）");
        }
        this.enabledAgents = Collections.unmodifiableMap(agents);
        this.adaptersByAgentId = Collections.unmodifiableMap(agentAdapters);
        log.info("已登錄 Agent：{}", enabledAgents.keySet());
    }

    /** 啟用中的 Agent（依設定順序）。 */
    public List<AgentDefinition> enabledAgents() {
        return List.copyOf(enabledAgents.values());
    }

    /** 依 ID 查詢；不在允許清單中則回傳 empty。 */
    public Optional<AgentDefinition> find(String agentId) {
        return Optional.ofNullable(agentId).map(enabledAgents::get);
    }

    public boolean isAllowed(String agentId) {
        return agentId != null && enabledAgents.containsKey(agentId);
    }

    public AgentAdapter adapterFor(String agentId) {
        AgentAdapter adapter = adaptersByAgentId.get(agentId);
        if (adapter == null) {
            throw new IllegalArgumentException("Agent 不在允許清單中：" + agentId);
        }
        return adapter;
    }

    static URI validateUrl(String agentId, String url, AgentRoutingProperties.AgentUrlPolicy policy) {
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Agent " + agentId + " URL 格式錯誤", e);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("https") && !(scheme.equals("http") && !policy.isRequireHttps())) {
            throw new IllegalStateException("Agent " + agentId + " URL 必須使用 https（或關閉 require-https）");
        }
        if (uri.getUserInfo() != null) {
            throw new IllegalStateException("Agent " + agentId + " URL 不可包含帳號密碼");
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        boolean allowed = policy.getAllowedHosts().stream()
                .map(h -> h.toLowerCase(Locale.ROOT))
                .anyMatch(h -> h.startsWith("*.") ? host.endsWith(h.substring(1)) : host.equals(h));
        if (!allowed) {
            throw new IllegalStateException("Agent " + agentId + " 的主機 " + host + " 不在 agent-url-policy.allowed-hosts 允許清單中");
        }
        return uri;
    }
}
