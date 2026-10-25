package com.cx.airouter.service;

import com.cx.airouter.agent.AgentDefinition;
import com.cx.airouter.config.ClassifierType;
import java.util.List;

/**
 * 套用信心門檻與允許清單後的最終路由決策。
 *
 * @param route      FINANCE / PRODUCTION / BOTH / OTHER / UNCERTAIN（Agent ID 由設定決定）
 * @param agents     要呼叫的 Agent（只會來自允許清單）
 * @param confidence 分類器信心值（可為 null，例如模型拒答）
 * @param reason     未路由時的原因說明
 */
public record RouteDecision(
        String route,
        Kind kind,
        List<AgentDefinition> agents,
        Double confidence,
        ClassifierType classifier,
        String reason) {

    public enum Kind { SINGLE, MULTI, OTHER, UNCERTAIN }

    public boolean routed() {
        return kind == Kind.SINGLE || kind == Kind.MULTI;
    }
}
