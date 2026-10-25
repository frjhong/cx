package com.cx.airouter.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cx.airouter.config.AgentRoutingProperties;
import com.cx.airouter.support.TestProperties;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/** Agent 允許清單與設定驗證。 */
class AgentRegistryTest {

    private static AgentRegistry registry(AgentRoutingProperties props) {
        RestClient.Builder rb = RestClient.builder();
        return new AgentRegistry(props, List.of(new FinanceAgentAdapter(rb), new ProductionAgentAdapter(rb), new GenericHttpAgentAdapter(rb)));
    }

    static AgentRoutingProperties.AgentProperties agent(String id, String url, String adapter) {
        AgentRoutingProperties.AgentProperties a = new AgentRoutingProperties.AgentProperties();
        a.setId(id);
        a.setName(id + " Agent");
        a.setDescription("負責 " + id);
        a.setUrl(url);
        a.setAdapter(adapter);
        return a;
    }

    @Test
    @DisplayName("實際 application.yml（正式設定）可正確載入兩個 Agent")
    void loadsProductionConfig() {
        AgentRegistry r = registry(TestProperties.load(null, java.util.Map.of()));
        assertThat(r.enabledAgents()).extracting(AgentDefinition::id).containsExactly("FINANCE", "PRODUCTION");
        assertThat(r.find("FINANCE").orElseThrow().uri().getScheme()).isEqualTo("https");
    }

    @Test
    @DisplayName("擴充：只改設定即可新增第三個 Agent（generic-http adapter）")
    void addAgentByConfigOnly() {
        AgentRoutingProperties props = TestProperties.mock();
        props.getAgents().add(agent("QUALITY", "http://localhost:9000/quality", "generic-http"));
        AgentRegistry r = registry(props);
        assertThat(r.enabledAgents()).extracting(AgentDefinition::id).containsExactly("FINANCE", "PRODUCTION", "QUALITY");
        assertThat(r.adapterFor("QUALITY")).isInstanceOf(GenericHttpAgentAdapter.class);
    }

    @Test
    @DisplayName("停用的 Agent 不列入路由選項")
    void disabledAgentExcluded() {
        AgentRoutingProperties props = TestProperties.mock();
        props.getAgents().get(1).setEnabled(false);
        assertThat(registry(props).isAllowed("PRODUCTION")).isFalse();
    }

    @Test
    @DisplayName("URL 主機不在允許清單 → 啟動失敗")
    void hostNotAllowed() {
        AgentRoutingProperties props = TestProperties.mock();
        props.getAgents().get(0).setUrl("http://evil.example.com/finance");
        assertThatThrownBy(() -> registry(props)).hasMessageContaining("不在 agent-url-policy.allowed-hosts");
    }

    @Test
    @DisplayName("正式設定要求 https")
    void httpsRequired() {
        AgentRoutingProperties props = TestProperties.load(null, java.util.Map.of());
        props.getAgents().get(0).setUrl("http://finance-agent.internal.example.com/api/ask");
        assertThatThrownBy(() -> registry(props)).hasMessageContaining("https");
    }

    @Test
    @DisplayName("URL 不可包含帳密；Agent ID 不可使用保留字；不可重複")
    void otherValidations() {
        AgentRoutingProperties p1 = TestProperties.mock();
        p1.getAgents().get(0).setUrl("http://user:pass@localhost/finance");
        assertThatThrownBy(() -> registry(p1)).hasMessageContaining("帳號密碼");

        AgentRoutingProperties p2 = TestProperties.mock();
        p2.getAgents().add(agent("BOTH", "http://localhost/x", "generic-http"));
        assertThatThrownBy(() -> registry(p2)).hasMessageContaining("保留字");

        AgentRoutingProperties p3 = TestProperties.mock();
        p3.getAgents().add(agent("FINANCE", "http://localhost/x", "generic-http"));
        assertThatThrownBy(() -> registry(p3)).hasMessageContaining("重複");
    }

    @Test
    @DisplayName("萬用字元主機 *.internal.example.com")
    void wildcardHost() {
        AgentRoutingProperties.AgentUrlPolicy policy = new AgentRoutingProperties.AgentUrlPolicy();
        policy.setAllowedHosts(List.of("*.internal.example.com"));
        assertThat(AgentRegistry.validateUrl("X", "https://hr.internal.example.com/a", policy).getHost()).isEqualTo("hr.internal.example.com");
        assertThatThrownBy(() -> AgentRegistry.validateUrl("X", "https://internal.example.com.evil.io/a", policy))
                .isInstanceOf(IllegalStateException.class);
    }
}
