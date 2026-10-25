package com.cx.airouter.security;

import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 由 Spring Security 取得目前呼叫者。
 *
 * <p>企業 SSO 整合點：JWT 模式下角色由 {@link SecurityConfig} 的 JwtAuthenticationConverter 轉換；
 * 若企業 IdP 角色結構不同（例如群組需再查詢目錄服務），可替換此 Bean。</p>
 */
@Component
public class CallerContextResolver {

    private static final String ROLE_PREFIX = "ROLE_";

    public CallerContext current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            throw new IllegalStateException("呼叫者未驗證");
        }
        Set<String> roles = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a != null && a.startsWith(ROLE_PREFIX))
                .map(a -> a.substring(ROLE_PREFIX.length()))
                .collect(Collectors.toUnmodifiableSet());
        return new CallerContext(auth.getName(), roles);
    }
}
