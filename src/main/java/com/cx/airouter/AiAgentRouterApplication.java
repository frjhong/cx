package com.cx.airouter;

import com.cx.airouter.config.AgentRoutingProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * AI Agent 智慧路由系統進入點。
 *
 * <p>排除 {@link UserDetailsServiceAutoConfiguration}：本系統不使用帳號密碼登入，
 * 身分驗證由 API Key（示範）或企業 SSO / JWT 提供，避免 Spring Boot 產生預設密碼。</p>
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableConfigurationProperties(AgentRoutingProperties.class)
public class AiAgentRouterApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiAgentRouterApplication.class, args);
    }
}
