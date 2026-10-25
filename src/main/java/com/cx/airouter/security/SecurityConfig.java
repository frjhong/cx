package com.cx.airouter.security;

import com.cx.airouter.config.AgentRoutingProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

/**
 * 安全設定。
 *
 * <ul>
 *   <li>{@code API_KEY}：以 {@code X-API-Key} 驗證，適用示範與系統對系統呼叫。</li>
 *   <li>{@code JWT}：企業 SSO（OIDC / OAuth2）整合，設定
 *       {@code spring.security.oauth2.resourceserver.jwt.issuer-uri} 或 {@code jwk-set-uri}；
 *       角色取自 {@code ai-router.security.jwt-roles-claim}。</li>
 * </ul>
 * 呼叫 /api/ai/** 必須具備 {@code required-role}；個別 Agent 權限另由 {@link AgentAccessPolicy} 檢查。
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, AgentRoutingProperties properties,
                                                   ObjectMapper objectMapper) throws Exception {
        AgentRoutingProperties.Security security = properties.getSecurity();

        http.csrf(AbstractHttpConfigurer::disable) // 無 Session、無 Cookie 的 API，不使用 CSRF Token
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers("/actuator/health", "/actuator/info", "/error").permitAll();
                    if (properties.getMockAgents().isEnabled()) {
                        // 內建 Mock Agent 模擬「外部既有系統」，僅在 Mock 模式開放
                        auth.requestMatchers("/mock-agents/**").permitAll();
                    }
                    auth.requestMatchers("/api/ai/**").hasRole(security.getRequiredRole());
                    auth.anyRequest().denyAll();
                })
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) ->
                                writeError(res, objectMapper, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHENTICATED", "未驗證或憑證無效"))
                        .accessDeniedHandler((req, res, ex) ->
                                writeError(res, objectMapper, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN", "無權限存取此資源")));

        if (security.getMode() == AgentRoutingProperties.Security.Mode.API_KEY) {
            http.addFilterBefore(new ApiKeyAuthenticationFilter(security), AnonymousAuthenticationFilter.class);
        } else {
            http.oauth2ResourceServer(o -> o
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter(security.getJwtRolesClaim())))
                    .authenticationEntryPoint((req, res, ex) ->
                            writeError(res, objectMapper, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHENTICATED", "未驗證或憑證無效")));
        }
        return http.build();
    }

    static Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter(String rolesClaim) {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Collection<GrantedAuthority> authorities = new ArrayList<>();
            Object claim = jwt.getClaim(rolesClaim);
            if (claim instanceof Collection<?> roles) {
                roles.forEach(r -> authorities.add(new SimpleGrantedAuthority("ROLE_" + r)));
            } else if (claim instanceof String s) {
                for (String r : s.split("[ ,]+")) {
                    if (!r.isBlank()) {
                        authorities.add(new SimpleGrantedAuthority("ROLE_" + r));
                    }
                }
            }
            return authorities;
        });
        return converter;
    }

    private static void writeError(HttpServletResponse res, ObjectMapper mapper, int status, String code, String message)
            throws IOException {
        res.setStatus(status);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        mapper.writeValue(res.getOutputStream(), Map.of("errorCode", code, "message", message));
    }
}
