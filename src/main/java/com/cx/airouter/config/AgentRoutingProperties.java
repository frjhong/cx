package com.cx.airouter.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * AI Router 全部可調整設定（前綴 {@code ai-router}）。
 *
 * <p>Agent 定義完全由設定檔管理；新增 Agent 只需在 {@code ai-router.agents} 增加一筆，
 * 不需修改 Controller 與核心 Router。</p>
 */
@Validated
@ConfigurationProperties(prefix = "ai-router")
public class AgentRoutingProperties {

    @Valid
    private Classifier classifier = new Classifier();

    @Valid
    private OpenAi openai = new OpenAi();

    @Valid
    private List<AgentProperties> agents = new ArrayList<>();

    @Valid
    private AgentUrlPolicy agentUrlPolicy = new AgentUrlPolicy();

    @Valid
    private Security security = new Security();

    @Valid
    private DataProtection dataProtection = new DataProtection();

    @Valid
    private PromptInjection promptInjection = new PromptInjection();

    private MockAgents mockAgents = new MockAgents();

    @Min(1)
    private int maxQuestionLength = 2000;

    public Classifier getClassifier() { return classifier; }
    public void setClassifier(Classifier classifier) { this.classifier = classifier; }
    public OpenAi getOpenai() { return openai; }
    public void setOpenai(OpenAi openai) { this.openai = openai; }
    public List<AgentProperties> getAgents() { return agents; }
    public void setAgents(List<AgentProperties> agents) { this.agents = agents; }
    public AgentUrlPolicy getAgentUrlPolicy() { return agentUrlPolicy; }
    public void setAgentUrlPolicy(AgentUrlPolicy agentUrlPolicy) { this.agentUrlPolicy = agentUrlPolicy; }
    public Security getSecurity() { return security; }
    public void setSecurity(Security security) { this.security = security; }
    public DataProtection getDataProtection() { return dataProtection; }
    public void setDataProtection(DataProtection dataProtection) { this.dataProtection = dataProtection; }
    public PromptInjection getPromptInjection() { return promptInjection; }
    public void setPromptInjection(PromptInjection promptInjection) { this.promptInjection = promptInjection; }
    public MockAgents getMockAgents() { return mockAgents; }
    public void setMockAgents(MockAgents mockAgents) { this.mockAgents = mockAgents; }
    public int getMaxQuestionLength() { return maxQuestionLength; }
    public void setMaxQuestionLength(int maxQuestionLength) { this.maxQuestionLength = maxQuestionLength; }

    /** 分類器設定。 */
    public static class Classifier {
        /** 主要分類器。 */
        @NotNull
        private ClassifierType primary = ClassifierType.DECISIONS;
        /** 主要分類器暫時不可用時的備援（NONE 表示不備援，直接回報錯誤）。 */
        @NotNull
        private ClassifierType fallback = ClassifierType.NONE;
        /**
         * 信心門檻。低於此值一律判定為 UNCERTAIN，不呼叫任何 Agent。
         * 0.75 為示範初始值，<b>尚未以實際問題集校準</b>。
         */
        @DecimalMin("0.0") @DecimalMax("1.0")
        private double confidenceThreshold = 0.75;
        /** 超過兩個 Agent 時，多 Agent 路由下判定「需要某 Agent」的機率門檻（同樣未校準）。 */
        @DecimalMin("0.0") @DecimalMax("1.0")
        private double multiAgentThreshold = 0.5;
        /** 多 Agent 合作的路由標籤；兩個 Agent 時為 BOTH。 */
        @NotBlank
        @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,31}$")
        private String multiRouteLabel = "BOTH";
        /** Mock / 內部分類器使用的「非業務」提示詞（例如天氣、閒聊）。 */
        private List<String> otherHints = new ArrayList<>(List.of("天氣", "氣溫", "下雨", "颱風", "笑話", "電影", "新聞", "旅遊"));

        public ClassifierType getPrimary() { return primary; }
        public void setPrimary(ClassifierType primary) { this.primary = primary; }
        public ClassifierType getFallback() { return fallback; }
        public void setFallback(ClassifierType fallback) { this.fallback = fallback; }
        public double getConfidenceThreshold() { return confidenceThreshold; }
        public void setConfidenceThreshold(double confidenceThreshold) { this.confidenceThreshold = confidenceThreshold; }
        public double getMultiAgentThreshold() { return multiAgentThreshold; }
        public void setMultiAgentThreshold(double multiAgentThreshold) { this.multiAgentThreshold = multiAgentThreshold; }
        public String getMultiRouteLabel() { return multiRouteLabel; }
        public void setMultiRouteLabel(String multiRouteLabel) { this.multiRouteLabel = multiRouteLabel; }
        public List<String> getOtherHints() { return otherHints; }
        public void setOtherHints(List<String> otherHints) { this.otherHints = otherHints; }
    }

    /** OpenAI 連線設定。API Key 只能由環境變數 OPENAI_API_KEY 提供。 */
    public static class OpenAi {
        private String apiKey = "";
        @NotBlank
        private String baseUrl = "https://api.openai.com/v1";
        /** Decisions API 模型；官方文件說明目前僅支援 gpt-6-luna。 */
        @NotBlank
        private String decisionsModel = "gpt-6-luna";
        /** Responses API 備援模型。 */
        @NotBlank
        private String responsesModel = "gpt-6-luna";
        @NotNull
        private Duration timeout = Duration.ofSeconds(10);
        /** SDK 自動重試次數（SDK 只重試連線錯誤、408、409、429、5xx）。 */
        @Min(0)
        private int maxRetries = 1;

        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getDecisionsModel() { return decisionsModel; }
        public void setDecisionsModel(String decisionsModel) { this.decisionsModel = decisionsModel; }
        public String getResponsesModel() { return responsesModel; }
        public void setResponsesModel(String responsesModel) { this.responsesModel = responsesModel; }
        public Duration getTimeout() { return timeout; }
        public void setTimeout(Duration timeout) { this.timeout = timeout; }
        public int getMaxRetries() { return maxRetries; }
        public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }

        public boolean hasApiKey() {
            return apiKey != null && !apiKey.isBlank();
        }
    }

    /** 單一 Agent 定義。 */
    public static class AgentProperties {
        /** Agent ID，同時作為分類選項值；限大寫英數與底線。 */
        @NotBlank
        @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,31}$")
        private String id;
        @NotBlank
        private String name;
        /** 職責描述；會作為分類選項說明送給分類模型。 */
        @NotBlank
        private String description;
        /** Agent API URL；必須符合 agent-url-policy 允許清單。 */
        @NotBlank
        private String url;
        private boolean enabled = true;
        /** 對應的 AgentAdapter 種類（finance / production / generic-http）。 */
        @NotBlank
        private String adapter = "generic-http";
        /** 單次呼叫讀取逾時。 */
        @NotNull
        private Duration timeout = Duration.ofSeconds(10);
        @NotNull
        private Duration connectTimeout = Duration.ofSeconds(3);
        /** 有限重試次數（不含第一次）。 */
        @Min(0)
        private int maxRetries = 1;
        /**
         * 此 Agent 操作是否為冪等（純查詢）。false 時，只在「確定請求未送達」（連線失敗）時重試，
         * 避免重複執行具副作用的操作。
         */
        private boolean idempotent = true;
        /** 呼叫此 Agent 所需角色（任一即可）。空清單表示任何已驗證使用者皆可。 */
        private List<String> requiredRoles = new ArrayList<>();
        /** 呼叫 Agent API 時使用的服務憑證（由環境變數提供，可空白）。 */
        private String apiKey = "";
        private String apiKeyHeader = "Authorization";
        /** 僅供 Mock / 內部規則分類器使用的提示詞；真實 AI 路由不使用。 */
        private List<String> routingHints = new ArrayList<>();

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getAdapter() { return adapter; }
        public void setAdapter(String adapter) { this.adapter = adapter; }
        public Duration getTimeout() { return timeout; }
        public void setTimeout(Duration timeout) { this.timeout = timeout; }
        public Duration getConnectTimeout() { return connectTimeout; }
        public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
        public int getMaxRetries() { return maxRetries; }
        public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
        public boolean isIdempotent() { return idempotent; }
        public void setIdempotent(boolean idempotent) { this.idempotent = idempotent; }
        public List<String> getRequiredRoles() { return requiredRoles; }
        public void setRequiredRoles(List<String> requiredRoles) { this.requiredRoles = requiredRoles; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getApiKeyHeader() { return apiKeyHeader; }
        public void setApiKeyHeader(String apiKeyHeader) { this.apiKeyHeader = apiKeyHeader; }
        public List<String> getRoutingHints() { return routingHints; }
        public void setRoutingHints(List<String> routingHints) { this.routingHints = routingHints; }
    }

    /** Agent URL 允許清單；模型輸出永遠無法決定網址，網址只來自此處驗證過的設定。 */
    public static class AgentUrlPolicy {
        private List<String> allowedHosts = new ArrayList<>();
        private boolean requireHttps = true;

        public List<String> getAllowedHosts() { return allowedHosts; }
        public void setAllowedHosts(List<String> allowedHosts) { this.allowedHosts = allowedHosts; }
        public boolean isRequireHttps() { return requireHttps; }
        public void setRequireHttps(boolean requireHttps) { this.requireHttps = requireHttps; }
    }

    /** 身分驗證設定。 */
    public static class Security {
        public enum Mode { API_KEY, JWT }

        @NotNull
        private Mode mode = Mode.JWT;
        /** 呼叫 /api/ai/ask 所需的基本角色。 */
        @NotBlank
        private String requiredRole = "AI_ROUTER_USER";
        private String apiKeyHeader = "X-API-Key";
        /** API_KEY 模式的金鑰清單（示範／系統對系統用；正式環境建議改用 JWT）。 */
        private List<ApiKeyClient> apiKeys = new ArrayList<>();
        /** JWT 中角色所在的 claim 名稱。 */
        private String jwtRolesClaim = "roles";

        public Mode getMode() { return mode; }
        public void setMode(Mode mode) { this.mode = mode; }
        public String getRequiredRole() { return requiredRole; }
        public void setRequiredRole(String requiredRole) { this.requiredRole = requiredRole; }
        public String getApiKeyHeader() { return apiKeyHeader; }
        public void setApiKeyHeader(String apiKeyHeader) { this.apiKeyHeader = apiKeyHeader; }
        public List<ApiKeyClient> getApiKeys() { return apiKeys; }
        public void setApiKeys(List<ApiKeyClient> apiKeys) { this.apiKeys = apiKeys; }
        public String getJwtRolesClaim() { return jwtRolesClaim; }
        public void setJwtRolesClaim(String jwtRolesClaim) { this.jwtRolesClaim = jwtRolesClaim; }
    }

    /** API Key 對應的呼叫者與角色。 */
    public static class ApiKeyClient {
        private String key;
        private String principal;
        private List<String> roles = new ArrayList<>();

        public String getKey() { return key; }
        public void setKey(String key) { this.key = key; }
        public String getPrincipal() { return principal; }
        public void setPrincipal(String principal) { this.principal = principal; }
        public List<String> getRoles() { return roles; }
        public void setRoles(List<String> roles) { this.roles = roles; }
    }

    /** 機敏資料保護設定。 */
    public static class DataProtection {
        public enum ExternalModelPolicy {
            /** 允許送往外部模型。 */
            ALLOW,
            /** 拒絕路由。 */
            REJECT,
            /** 改用內部分類器。 */
            INTERNAL_CLASSIFIER
        }

        @NotNull
        private ExternalModelPolicy externalModelPolicy = ExternalModelPolicy.REJECT;
        private List<NamedPattern> sensitivePatterns = new ArrayList<>();

        public ExternalModelPolicy getExternalModelPolicy() { return externalModelPolicy; }
        public void setExternalModelPolicy(ExternalModelPolicy externalModelPolicy) { this.externalModelPolicy = externalModelPolicy; }
        public List<NamedPattern> getSensitivePatterns() { return sensitivePatterns; }
        public void setSensitivePatterns(List<NamedPattern> sensitivePatterns) { this.sensitivePatterns = sensitivePatterns; }
    }

    /** Prompt Injection 防護設定。 */
    public static class PromptInjection {
        public enum Action {
            /** 偵測到即拒絕。 */
            REJECT,
            /** 僅標記警告並繼續（仍受結構化輸出、允許清單、授權檢查保護）。 */
            FLAG
        }

        @NotNull
        private Action action = Action.REJECT;
        private List<NamedPattern> patterns = new ArrayList<>();

        public Action getAction() { return action; }
        public void setAction(Action action) { this.action = action; }
        public List<NamedPattern> getPatterns() { return patterns; }
        public void setPatterns(List<NamedPattern> patterns) { this.patterns = patterns; }
    }

    /** 具名稱的正規表示式（名稱用於日誌，避免記錄實際內容）。 */
    public static class NamedPattern {
        private String name;
        private String regex;

        public NamedPattern() {
        }

        public NamedPattern(String name, String regex) {
            this.name = name;
            this.regex = regex;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getRegex() { return regex; }
        public void setRegex(String regex) { this.regex = regex; }
    }

    /** 內建 Mock Agent API（模擬既有 Agent 系統）。 */
    public static class MockAgents {
        private boolean enabled = false;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }
}
