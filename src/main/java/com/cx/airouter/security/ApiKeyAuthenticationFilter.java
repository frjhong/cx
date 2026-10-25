package com.cx.airouter.security;

import com.cx.airouter.config.AgentRoutingProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * API Key 驗證（示範與系統對系統整合用）。金鑰以常數時間比較；未帶或錯誤時不建立身分，
 * 由 Spring Security 回應 401。
 */
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private final String headerName;
    private final List<AgentRoutingProperties.ApiKeyClient> clients;

    public ApiKeyAuthenticationFilter(AgentRoutingProperties.Security security) {
        this.headerName = security.getApiKeyHeader();
        this.clients = security.getApiKeys().stream()
                .filter(c -> c.getKey() != null && !c.getKey().isBlank())
                .toList();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String presented = request.getHeader(headerName);
        if (presented != null && !presented.isBlank()) {
            byte[] presentedBytes = presented.getBytes(StandardCharsets.UTF_8);
            for (AgentRoutingProperties.ApiKeyClient client : clients) {
                if (MessageDigest.isEqual(presentedBytes, client.getKey().getBytes(StandardCharsets.UTF_8))) {
                    List<SimpleGrantedAuthority> authorities = client.getRoles().stream()
                            .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                            .toList();
                    SecurityContextHolder.getContext().setAuthentication(
                            UsernamePasswordAuthenticationToken.authenticated(client.getPrincipal(), null, authorities));
                    break;
                }
            }
        }
        chain.doFilter(request, response);
    }
}
