package com.cx.airouter.security;

import java.util.Set;

/**
 * 已驗證呼叫者（由 SSO / JWT 或 API Key 驗證後取得，不信任任何請求內容自稱的身分）。
 *
 * @param principal 呼叫者識別
 * @param roles     角色（不含 ROLE_ 前綴）
 */
public record CallerContext(String principal, Set<String> roles) {

    public boolean hasAnyRole(Set<String> required) {
        return required.isEmpty() || required.stream().anyMatch(roles::contains);
    }
}
