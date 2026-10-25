package com.cx.airouter.config;

/**
 * 路由分類器種類。
 */
public enum ClassifierType {
    /** OpenAI Decisions API（POST /v1/decisions，choice 問題）。優先採用。 */
    DECISIONS,
    /** OpenAI Responses API + Structured Outputs（備援）。 */
    RESPONSES,
    /** 示範用 Mock 分類器：以設定檔中的提示詞做簡易比對，<b>不是</b>真實 AI 路由。 */
    MOCK,
    /** 內部分類器：機敏內容不得送外部模型時使用（目前為規則式預設實作，可替換為內部模型）。 */
    INTERNAL,
    /** 不使用（僅用於 fallback 設定）。 */
    NONE;

    /** 是否會將問題送往外部模型服務。 */
    public boolean isExternal() {
        return this == DECISIONS || this == RESPONSES;
    }
}
