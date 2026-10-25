package com.cx.airouter.web;

import com.cx.airouter.agent.AgentCallResult;
import com.cx.airouter.agent.AgentCallStatus;
import com.cx.airouter.security.CallerContextResolver;
import com.cx.airouter.service.AgentRouterService;
import com.cx.airouter.service.RoutingOutcome;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI Router REST API。
 *
 * <p>HTTP 狀態：成功、部分成功、未路由（OTHER / UNCERTAIN）回 200；
 * 所有 Agent 皆逾時回 504、其他 Agent 失敗回 502（回應內容仍包含各 Agent 結果）。</p>
 */
@RestController
@RequestMapping("/api/ai")
public class AgentRouterController {

    private final AgentRouterService routerService;
    private final CallerContextResolver callerContextResolver;

    public AgentRouterController(AgentRouterService routerService, CallerContextResolver callerContextResolver) {
        this.routerService = routerService;
        this.callerContextResolver = callerContextResolver;
    }

    @PostMapping("/ask")
    public ResponseEntity<AskResponse> ask(@Valid @RequestBody AskRequest request) {
        RoutingOutcome outcome = routerService.ask(request.question(), callerContextResolver.current());
        return ResponseEntity.status(httpStatus(outcome)).body(AskResponse.from(outcome));
    }

    static HttpStatus httpStatus(RoutingOutcome outcome) {
        if (outcome.status() != RoutingOutcome.Status.FAILED) {
            return HttpStatus.OK;
        }
        boolean allTimeout = outcome.results().stream()
                .map(AgentCallResult::status)
                .allMatch(s -> s == AgentCallStatus.TIMEOUT);
        return allTimeout ? HttpStatus.GATEWAY_TIMEOUT : HttpStatus.BAD_GATEWAY;
    }
}
