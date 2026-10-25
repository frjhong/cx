package com.cx.airouter.agent;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Agent B：生產 AI 助理的 Adapter。
 *
 * <p>目前沿用 {@link AbstractHttpAgentAdapter} 的預設 JSON 契約；
 * 取得生產 Agent 正式 API 規格後，於此覆寫 {@code buildRequestBody} / {@code extractAnswer}。</p>
 */
@Component
public class ProductionAgentAdapter extends AbstractHttpAgentAdapter {

    public static final String TYPE = "production";

    public ProductionAgentAdapter(RestClient.Builder restClientBuilder) {
        super(restClientBuilder);
    }

    @Override
    public String type() {
        return TYPE;
    }
}
