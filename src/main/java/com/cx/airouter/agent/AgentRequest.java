package com.cx.airouter.agent;

/**
 * 轉送給 Agent 的請求。
 *
 * @param requestId      本次路由請求 ID（追蹤用）
 * @param question       使用者原始問題（原文轉送，不夾帶模型輸出）
 * @param callerId       呼叫者識別（來自已驗證身分）
 * @param idempotencyKey 冪等鍵；重試時保持不變，讓 Agent 端可去重
 */
public record AgentRequest(String requestId, String question, String callerId, String idempotencyKey) {
}
