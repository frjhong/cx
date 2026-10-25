package com.cx.airouter.config;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAI SDK 與執行緒池設定。
 *
 * <p>只有在設定了 OPENAI_API_KEY 時才建立 {@link OpenAIClient}；
 * 因此 Mock 模式在沒有 API Key 的情況下仍可啟動。</p>
 */
@Configuration(proxyBeanMethods = false)
public class OpenAiConfig {

    @Bean(destroyMethod = "close")
    @ConditionalOnExpression("'${ai-router.openai.api-key:}'.trim().length() > 0")
    public OpenAIClient openAIClient(AgentRoutingProperties properties) {
        AgentRoutingProperties.OpenAi openai = properties.getOpenai();
        return OpenAIOkHttpClient.builder()
                .apiKey(openai.getApiKey())
                .baseUrl(openai.getBaseUrl())
                .timeout(openai.getTimeout())
                .maxRetries(openai.getMaxRetries())
                .build();
    }

    /** Agent 並行呼叫使用的虛擬執行緒池（Java 21）。 */
    @Bean(name = "agentExecutor", destroyMethod = "close")
    public ExecutorService agentExecutor() {
        return Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("agent-call-", 0).factory());
    }
}
