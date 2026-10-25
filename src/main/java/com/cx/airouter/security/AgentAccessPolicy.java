package com.cx.airouter.security;

import com.cx.airouter.agent.AgentDefinition;

/**
 * Agent 呼叫授權政策。
 *
 * <p>授權在路由「之後」獨立檢查：AI 路由結果只代表「問題屬於哪個 Agent」，
 * 不代表呼叫者有權使用該 Agent。可替換為呼叫企業權限系統（例如 OPA、IAM）的實作。</p>
 */
public interface AgentAccessPolicy {

    boolean canInvoke(CallerContext caller, AgentDefinition agent);
}
