package com.cx.airouter.agent;

/**
 * Agent 回覆。
 *
 * @param answer Agent 回傳的原始答案文字（Router 不改寫、不補充數據）
 */
public record AgentResponse(String answer) {
}
