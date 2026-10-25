package com.cx.airouter.agent;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Agent A：財務 AI 特助的 Adapter。
 *
 * <p>目前沿用 {@link AbstractHttpAgentAdapter} 的預設 JSON 契約；
 * 取得財務 Agent 正式 API 規格後，於此覆寫 {@code buildRequestBody} / {@code extractAnswer}。</p>
 */
@Component
public class FinanceAgentAdapter extends AbstractHttpAgentAdapter {

    public static final String TYPE = "finance";

    public FinanceAgentAdapter(RestClient.Builder restClientBuilder) {
        super(restClientBuilder);
    }

    @Override
    public String type() {
        return TYPE;
    }
}
