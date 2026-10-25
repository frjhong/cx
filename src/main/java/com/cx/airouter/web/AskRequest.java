package com.cx.airouter.web;

import jakarta.validation.constraints.NotBlank;

/**
 * POST /api/ai/ask 請求。長度上限由 {@code ai-router.max-question-length} 於服務層檢查。
 */
public record AskRequest(@NotBlank(message = "question 不可為空白") String question) {
}
