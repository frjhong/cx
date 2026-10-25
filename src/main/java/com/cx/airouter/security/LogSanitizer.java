package com.cx.airouter.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 日誌去識別化工具：問題內容與使用者識別只以雜湊記錄，不寫入原文。
 */
public final class LogSanitizer {

    private LogSanitizer() {
    }

    /** SHA-256 前 16 個十六進位字元，可用於關聯同一問題／使用者，但無法還原原文。 */
    public static String fingerprint(String value) {
        if (value == null) {
            return "-";
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
