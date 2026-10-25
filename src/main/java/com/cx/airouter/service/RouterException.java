package com.cx.airouter.service;

import org.springframework.http.HttpStatus;

/**
 * 路由流程中可預期的拒絕或失敗（訊息為對外安全的說明，不含內部細節或使用者輸入）。
 */
public class RouterException extends RuntimeException {

    private final String errorCode;
    private final HttpStatus status;

    public RouterException(HttpStatus status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }

    public HttpStatus status() {
        return status;
    }
}
