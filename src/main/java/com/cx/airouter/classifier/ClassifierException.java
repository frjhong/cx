package com.cx.airouter.classifier;

import com.cx.airouter.config.ClassifierType;

/**
 * 分類器呼叫失敗。
 */
public class ClassifierException extends RuntimeException {

    private final ClassifierType classifier;
    private final String errorCode;
    private final boolean fallbackEligible;

    /**
     * @param fallbackEligible 是否屬於「服務暫時不可用」類錯誤，可切換至備援分類器
     */
    public ClassifierException(ClassifierType classifier, String errorCode, String message, boolean fallbackEligible, Throwable cause) {
        super(message, cause);
        this.classifier = classifier;
        this.errorCode = errorCode;
        this.fallbackEligible = fallbackEligible;
    }

    public ClassifierType classifier() {
        return classifier;
    }

    public String errorCode() {
        return errorCode;
    }

    public boolean fallbackEligible() {
        return fallbackEligible;
    }
}
