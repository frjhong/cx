package com.cx.airouter.agent;

/**
 * 單次 Agent 呼叫失敗。
 */
public class AgentInvocationException extends RuntimeException {

    /** 失敗類別，決定是否可安全重試。 */
    public enum Kind {
        /** 連線建立失敗：請求確定未送達，任何 Agent 都可安全重試。 */
        CONNECT_FAILURE,
        /** 讀取逾時：請求可能已被執行，只有冪等 Agent 可重試。 */
        TIMEOUT,
        /** 5xx 或暫時性錯誤：只有冪等 Agent 可重試。 */
        SERVER_ERROR,
        /** 4xx 等用戶端錯誤：不重試。 */
        CLIENT_ERROR,
        /** 回應格式不符：不重試。 */
        INVALID_RESPONSE
    }

    private final Kind kind;

    public AgentInvocationException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public AgentInvocationException(Kind kind, String message) {
        this(kind, message, null);
    }

    public Kind kind() {
        return kind;
    }
}
