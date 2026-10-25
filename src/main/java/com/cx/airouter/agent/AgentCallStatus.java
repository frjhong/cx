package com.cx.airouter.agent;

/** 單一 Agent 呼叫結果狀態。 */
public enum AgentCallStatus {
    SUCCESS,
    TIMEOUT,
    ERROR,
    /** 呼叫者無此 Agent 的權限，未呼叫。 */
    ACCESS_DENIED
}
