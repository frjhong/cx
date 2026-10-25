package com.cx.airouter.classifier;

import com.cx.airouter.config.ClassifierType;
import com.openai.errors.OpenAIException;
import com.openai.errors.OpenAIInvalidDataException;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.OpenAIServiceException;

/** 將 OpenAI SDK 例外轉為 {@link ClassifierException}（不保留回應內容，避免敏感資料外洩至日誌）。 */
final class OpenAiErrors {

    private OpenAiErrors() {
    }

    static ClassifierException translate(ClassifierType type, OpenAIException e) {
        if (e instanceof OpenAIServiceException se) {
            int status = se.statusCode();
            String code = "OPENAI_HTTP_" + status;
            // 401：金鑰錯誤，備援同樣會失敗；400/422：請求內容錯誤，屬程式問題
            boolean fallback = status == 403 || status == 404 || status == 408 || status == 409 || status == 429 || status >= 500;
            return new ClassifierException(type, code, "OpenAI API 回應 HTTP " + status, fallback, e);
        }
        if (e instanceof OpenAIIoException) {
            return new ClassifierException(type, "OPENAI_IO_ERROR", "無法連線至 OpenAI API", true, e);
        }
        if (e instanceof OpenAIInvalidDataException) {
            return new ClassifierException(type, "OPENAI_INVALID_RESPONSE", "OpenAI API 回應格式不符預期", true, e);
        }
        return new ClassifierException(type, "OPENAI_ERROR", "OpenAI API 呼叫失敗", true, e);
    }
}
