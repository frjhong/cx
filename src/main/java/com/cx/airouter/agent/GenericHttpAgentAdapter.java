package com.cx.airouter.agent;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 通用 HTTP JSON Adapter。新增的 Agent 若符合預設契約，設定 {@code adapter: generic-http} 即可，
 * 不需撰寫任何 Java 程式。
 */
@Component
public class GenericHttpAgentAdapter extends AbstractHttpAgentAdapter {

    public static final String TYPE = "generic-http";

    public GenericHttpAgentAdapter(RestClient.Builder restClientBuilder) {
        super(restClientBuilder);
    }

    @Override
    public String type() {
        return TYPE;
    }
}
