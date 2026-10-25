package com.cx.airouter.web;

import com.fasterxml.jackson.annotation.JsonInclude;

/** 統一錯誤回應。不包含堆疊、內部例外訊息或使用者輸入。 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(String errorCode, String message) {
}
