package com.cx.airouter.web;

import com.cx.airouter.agent.AgentCallResult;
import com.cx.airouter.agent.AgentDefinition;
import com.cx.airouter.config.ClassifierType;
import com.cx.airouter.service.RoutingOutcome;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * POST /api/ai/ask 回應。
 *
 * @param route       FINANCE / PRODUCTION / BOTH / OTHER / UNCERTAIN
 * @param agents      實際路由的 Agent ID
 * @param confidence  分類器回傳的信心值（Decisions：API 的 confidence；Mock：規則常數）
 * @param classifier  實際使用的分類器：DECISIONS / RESPONSES / MOCK / INTERNAL
 * @param mockRouting 是否為 Mock 路由（非真實 AI 路由）
 * @param durationMs  實際處理耗時
 * @param results     各 Agent 呼叫結果（保留來源）
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AskResponse(
        String requestId,
        String route,
        List<String> agents,
        Double confidence,
        String answer,
        long durationMs,
        String classifier,
        boolean mockRouting,
        String status,
        String reason,
        List<AgentResult> results,
        List<String> warnings) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AgentResult(String agentId, String agentName, String status, String answer, long durationMs,
                              int attempts, String errorCode) {

        static AgentResult from(AgentCallResult r) {
            return new AgentResult(r.agentId(), r.agentName(), r.status().name(), r.answer(), r.durationMs(),
                    r.attempts(), r.errorCode());
        }
    }

    public static AskResponse from(RoutingOutcome o) {
        var d = o.decision();
        return new AskResponse(
                o.requestId(),
                d.route(),
                d.agents().stream().map(AgentDefinition::id).toList(),
                d.confidence(),
                o.answer(),
                o.durationMs(),
                d.classifier().name(),
                d.classifier() == ClassifierType.MOCK,
                o.status().name(),
                d.reason(),
                o.results().stream().map(AgentResult::from).toList(),
                o.warnings().isEmpty() ? null : o.warnings());
    }
}
