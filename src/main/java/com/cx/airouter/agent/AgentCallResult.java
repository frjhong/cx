package com.cx.airouter.agent;

/**
 * 單一 Agent 呼叫結果（保留來源 Agent，供整合與稽核）。
 *
 * @param errorCode 失敗時的錯誤代碼（不含敏感內容）
 */
public record AgentCallResult(
        String agentId,
        String agentName,
        AgentCallStatus status,
        String answer,
        long durationMs,
        int attempts,
        String errorCode) {

    public boolean isSuccess() {
        return status == AgentCallStatus.SUCCESS;
    }

    public static AgentCallResult accessDenied(AgentDefinition agent) {
        return new AgentCallResult(agent.id(), agent.name(), AgentCallStatus.ACCESS_DENIED, null, 0, 0, "AGENT_ACCESS_DENIED");
    }
}
