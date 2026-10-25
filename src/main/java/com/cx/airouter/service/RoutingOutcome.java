package com.cx.airouter.service;

import com.cx.airouter.agent.AgentCallResult;
import java.util.List;

/**
 * 一次路由請求的完整結果。
 */
public record RoutingOutcome(
        String requestId,
        RouteDecision decision,
        Status status,
        String answer,
        List<AgentCallResult> results,
        long durationMs,
        List<String> warnings) {

    public enum Status {
        /** 所有 Agent 成功。 */
        SUCCESS,
        /** 多 Agent 中部分成功。 */
        PARTIAL,
        /** 所有 Agent 失敗。 */
        FAILED,
        /** OTHER / UNCERTAIN，未呼叫任何 Agent。 */
        NOT_ROUTED
    }
}
