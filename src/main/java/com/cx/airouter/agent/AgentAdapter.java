package com.cx.airouter.agent;

/**
 * 既有 AI Agent 的呼叫轉接介面。
 *
 * <p>因各 Agent 的實際 API 規格尚未提供，以 Adapter 隔離請求／回應格式；
 * 取得正式規格後只需替換或新增 Adapter，不影響 Router 與 Controller。</p>
 *
 * <p>實作必須是無狀態（或執行緒安全）的；重試、逾時與並行由 {@link AgentInvoker} 統一處理，
 * Adapter 只負責「單次」呼叫，失敗時拋出 {@link AgentInvocationException}。</p>
 */
public interface AgentAdapter {

    /** Adapter 種類，對應設定檔 {@code ai-router.agents[].adapter}。 */
    String type();

    /** 單次呼叫 Agent。 */
    AgentResponse invoke(AgentDefinition agent, AgentRequest request);
}
