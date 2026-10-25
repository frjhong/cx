package com.cx.airouter.service;

/** 非 Agent 的保留路由標籤。多 Agent 標籤由設定 {@code multi-route-label} 決定（預設 BOTH）。 */
public final class RouteLabels {

    /** 不屬於任何 Agent。 */
    public static final String OTHER = "OTHER";
    /** 信心不足或無法判斷。 */
    public static final String UNCERTAIN = "UNCERTAIN";

    private RouteLabels() {
    }
}
