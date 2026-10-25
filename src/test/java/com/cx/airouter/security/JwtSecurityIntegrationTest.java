package com.cx.airouter.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cx.airouter.support.StubHttpServer;
import com.cx.airouter.support.StubHttpServer.StubResponse;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.mockito.Mockito;

/**
 * 企業 SSO / JWT 模式：以 Mock JwtDecoder 模擬 IdP 簽發的 Token，驗證角色對應與授權。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("mock")
class JwtSecurityIntegrationTest {

    static final StubHttpServer AGENTS = StubHttpServer.start();

    @Autowired
    MockMvc mvc;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("ai-router.security.mode", () -> "JWT");
        r.add("ai-router.openai.api-key", () -> "");
        r.add("agent-endpoints.finance-url", () -> AGENTS.baseUrl() + "/finance");
        r.add("agent-endpoints.production-url", () -> AGENTS.baseUrl() + "/production");
    }

    @AfterAll
    static void stop() {
        AGENTS.close();
    }

    @BeforeEach
    void setUp() {
        AGENTS.reset();
        AGENTS.on("/finance", req -> StubResponse.json("{\"answer\":\"ok\"}"));
        Mockito.when(jwtDecoder.decode("finance-token")).thenReturn(jwt("alice", List.of("AI_ROUTER_USER", "FINANCE_READER")));
        Mockito.when(jwtDecoder.decode("no-router-role")).thenReturn(jwt("bob", List.of("FINANCE_READER")));
        Mockito.when(jwtDecoder.decode("invalid")).thenThrow(new BadJwtException("invalid"));
    }

    private static Jwt jwt(String subject, List<String> roles) {
        return Jwt.withTokenValue("t").header("alg", "RS256").subject(subject).claim("roles", roles)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
    }

    private org.springframework.test.web.servlet.ResultActions ask(String token) throws Exception {
        var req = post("/api/ai/ask").contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"今年營業利益增加多少？\"}");
        if (token != null) {
            req.header("Authorization", "Bearer " + token);
        }
        return mvc.perform(req);
    }

    @Test
    @DisplayName("JWT 含必要角色 → 200")
    void validToken() throws Exception {
        ask("finance-token").andExpect(status().isOk()).andExpect(jsonPath("$.route").value("FINANCE"));
    }

    @Test
    @DisplayName("未帶 Token → 401")
    void noToken() throws Exception {
        ask(null).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("Token 無效 → 401")
    void invalidToken() throws Exception {
        ask("invalid").andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("缺少 AI_ROUTER_USER 角色 → 403")
    void missingRouterRole() throws Exception {
        ask("no-router-role").andExpect(status().isForbidden()).andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }
}
