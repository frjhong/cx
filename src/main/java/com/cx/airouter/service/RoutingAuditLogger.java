package com.cx.airouter.service;

import com.cx.airouter.agent.AgentCallResult;
import com.cx.airouter.security.LogSanitizer;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 路由稽核日誌（logger 名稱 {@code AUDIT}，可在 logback 設定獨立輸出）。
 *
 * <p>只記錄：請求 ID、呼叫者雜湊、問題雜湊與長度、分類器、路由、Agent、信心、狀態、耗時、錯誤代碼。
 * <b>不記錄</b>問題原文、Agent 回覆內容與任何憑證。</p>
 */
@Component
public class RoutingAuditLogger {

    private static final Logger audit = LoggerFactory.getLogger("AUDIT");

    public record AuditEntry(
            String requestId,
            String principal,
            String question,
            String classifier,
            String route,
            List<String> agents,
            Double confidence,
            String status,
            long durationMs,
            String errorCode,
            List<AgentCallResult> results) {
    }

    public void log(AuditEntry e) {
        String agentResults = e.results() == null ? "-" : e.results().stream()
                .map(r -> r.agentId() + ":" + r.status() + ":" + r.durationMs() + "ms:attempts=" + r.attempts())
                .collect(Collectors.joining(","));
        audit.info("ai-route requestId={} caller={} questionHash={} questionLength={} classifier={} route={} agents={} "
                        + "confidence={} status={} durationMs={} errorCode={} agentResults=[{}]",
                e.requestId(), LogSanitizer.fingerprint(e.principal()), LogSanitizer.fingerprint(e.question()),
                e.question() == null ? 0 : e.question().length(), e.classifier(), e.route(),
                e.agents() == null ? "[]" : e.agents(), e.confidence(), e.status(), e.durationMs(),
                e.errorCode() == null ? "-" : e.errorCode(), agentResults);
    }
}
