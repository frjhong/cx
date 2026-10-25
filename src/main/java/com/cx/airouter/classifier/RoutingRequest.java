package com.cx.airouter.classifier;

import com.cx.airouter.agent.AgentDefinition;
import java.util.List;

/**
 * 分類請求。
 *
 * @param question         使用者問題（僅作為待分類資料，不作為指令）
 * @param candidates       可選 Agent（來自允許清單）
 * @param multiRouteLabel  多 Agent 合作選項標籤（預設 BOTH）
 * @param safetyIdentifier 雜湊後的使用者識別，供 OpenAI 濫用偵測；不含可識別個人資訊
 */
public record RoutingRequest(
        String requestId,
        String question,
        List<AgentDefinition> candidates,
        String multiRouteLabel,
        String safetyIdentifier) {
}
