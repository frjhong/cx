package com.cx.airouter.service;

import com.cx.airouter.agent.AgentCallResult;
import com.cx.airouter.agent.AgentDefinition;
import com.cx.airouter.agent.AgentInvoker;
import com.cx.airouter.agent.AgentRegistry;
import com.cx.airouter.classifier.ClassificationResult;
import com.cx.airouter.classifier.ClassifierException;
import com.cx.airouter.classifier.RoutingClassifier;
import com.cx.airouter.classifier.RoutingRequest;
import com.cx.airouter.config.AgentRoutingProperties;
import com.cx.airouter.config.AgentRoutingProperties.DataProtection.ExternalModelPolicy;
import com.cx.airouter.config.AgentRoutingProperties.PromptInjection;
import com.cx.airouter.config.ClassifierType;
import com.cx.airouter.security.AgentAccessPolicy;
import com.cx.airouter.security.CallerContext;
import com.cx.airouter.security.LogSanitizer;
import com.cx.airouter.security.PromptInjectionGuard;
import com.cx.airouter.security.SensitiveContentDetector;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * AI 路由核心流程：
 * <ol>
 *   <li>輸入檢查（長度、Prompt Injection）</li>
 *   <li>機敏內容政策：決定可否送往外部模型，必要時拒絕或改用內部分類器</li>
 *   <li>意圖分類（Decisions → 可設定備援 Responses）</li>
 *   <li>套用信心門檻與允許清單 → 路由決策</li>
 *   <li>獨立授權檢查（不依 AI 結果授權）</li>
 *   <li>呼叫 Agent（多 Agent 並行、逾時、有限重試、錯誤隔離）</li>
 *   <li>附來源整合答案、稽核日誌</li>
 * </ol>
 * 新增 Agent 只需修改設定檔，本類別不需變更。
 */
@Service
public class AgentRouterService {

    private static final Logger log = LoggerFactory.getLogger(AgentRouterService.class);

    private final AgentRoutingProperties properties;
    private final AgentRegistry registry;
    private final Map<ClassifierType, RoutingClassifier> classifiers;
    private final RouteResolver routeResolver;
    private final AgentInvoker agentInvoker;
    private final AgentAccessPolicy accessPolicy;
    private final PromptInjectionGuard injectionGuard;
    private final SensitiveContentDetector sensitiveDetector;
    private final AnswerAggregator aggregator;
    private final RoutingAuditLogger auditLogger;

    public AgentRouterService(AgentRoutingProperties properties, AgentRegistry registry, List<RoutingClassifier> classifiers,
                              RouteResolver routeResolver, AgentInvoker agentInvoker, AgentAccessPolicy accessPolicy,
                              PromptInjectionGuard injectionGuard, SensitiveContentDetector sensitiveDetector,
                              AnswerAggregator aggregator, RoutingAuditLogger auditLogger) {
        this.properties = properties;
        this.registry = registry;
        this.classifiers = new EnumMap<>(ClassifierType.class);
        classifiers.forEach(c -> this.classifiers.put(c.type(), c));
        this.routeResolver = routeResolver;
        this.agentInvoker = agentInvoker;
        this.accessPolicy = accessPolicy;
        this.injectionGuard = injectionGuard;
        this.sensitiveDetector = sensitiveDetector;
        this.aggregator = aggregator;
        this.auditLogger = auditLogger;
    }

    public RoutingOutcome ask(String rawQuestion, CallerContext caller) {
        long start = System.nanoTime();
        String requestId = UUID.randomUUID().toString();
        String question = rawQuestion == null ? "" : rawQuestion.strip();
        List<String> warnings = new ArrayList<>();
        RouteDecision decision = null;
        try {
            validateInput(question, warnings);
            List<AgentDefinition> candidates = registry.enabledAgents();
            RoutingRequest routingRequest = new RoutingRequest(requestId, question, candidates,
                    properties.getClassifier().getMultiRouteLabel(), "u_" + LogSanitizer.fingerprint(caller.principal()));

            ClassificationResult classification = classify(routingRequest, question, warnings);
            decision = routeResolver.resolve(classification, candidates);

            RoutingOutcome outcome;
            if (!decision.routed()) {
                outcome = new RoutingOutcome(requestId, decision, RoutingOutcome.Status.NOT_ROUTED,
                        aggregator.notRoutedMessage(decision, candidates), List.of(), elapsedMs(start), warnings);
            } else {
                outcome = invokeAgents(requestId, question, caller, decision, warnings, start);
            }
            audit(outcome, caller, question);
            return outcome;
        } catch (RouterException e) {
            auditFailure(requestId, caller, question, decision, e.errorCode(), start);
            throw e;
        }
    }

    private void validateInput(String question, List<String> warnings) {
        if (question.isEmpty()) {
            throw new RouterException(HttpStatus.BAD_REQUEST, "QUESTION_REQUIRED", "question 不可為空白");
        }
        if (question.length() > properties.getMaxQuestionLength()) {
            throw new RouterException(HttpStatus.BAD_REQUEST, "QUESTION_TOO_LONG",
                    "question 長度不可超過 " + properties.getMaxQuestionLength() + " 字");
        }
        Optional<String> injection = injectionGuard.detect(question);
        if (injection.isPresent()) {
            log.warn("偵測到疑似 Prompt Injection rule={}", injection.get());
            if (properties.getPromptInjection().getAction() == PromptInjection.Action.REJECT) {
                throw new RouterException(HttpStatus.UNPROCESSABLE_ENTITY, "PROMPT_INJECTION_SUSPECTED",
                        "問題內容包含疑似試圖操控系統規則的指令，已拒絕處理。請直接描述您要查詢的業務問題。");
            }
            warnings.add("問題含疑似操控指令（" + injection.get() + "），已忽略其指令內容並僅依業務語意分類");
        }
    }

    private ClassificationResult classify(RoutingRequest request, String question, List<String> warnings) {
        AgentRoutingProperties.Classifier config = properties.getClassifier();
        ClassifierType primary = config.getPrimary();
        ClassifierType fallback = config.getFallback();

        Optional<String> sensitive = sensitiveDetector.detect(question);
        ExternalModelPolicy policy = properties.getDataProtection().getExternalModelPolicy();
        boolean externalForbidden = sensitive.isPresent() && policy != ExternalModelPolicy.ALLOW;
        if (externalForbidden && primary.isExternal()) {
            log.info("偵測到機敏內容 rule={} policy={}", sensitive.get(), policy);
            if (policy == ExternalModelPolicy.REJECT) {
                throw new RouterException(HttpStatus.UNPROCESSABLE_ENTITY, "SENSITIVE_CONTENT_BLOCKED",
                        "問題含機敏資料（" + sensitive.get() + "），依政策不得送往外部模型，已拒絕路由。");
            }
            primary = ClassifierType.INTERNAL;
            warnings.add("問題含機敏資料，已改用內部分類器，未送往外部模型");
        }

        try {
            return classifier(primary).classify(request);
        } catch (ClassifierException e) {
            log.warn("分類器失敗 classifier={} errorCode={} fallbackEligible={}", e.classifier(), e.errorCode(), e.fallbackEligible());
            boolean canFallback = e.fallbackEligible() && fallback != ClassifierType.NONE && fallback != primary
                    && !(externalForbidden && fallback.isExternal());
            if (!canFallback) {
                throw unavailable(e);
            }
            warnings.add("主要分類器 " + primary + " 暫時不可用（" + e.errorCode() + "），已改用備援分類器 " + fallback);
            try {
                return classifier(fallback).classify(request);
            } catch (ClassifierException fe) {
                log.warn("備援分類器失敗 classifier={} errorCode={}", fe.classifier(), fe.errorCode());
                throw unavailable(fe);
            }
        }
    }

    private RoutingClassifier classifier(ClassifierType type) {
        RoutingClassifier classifier = classifiers.get(type);
        if (classifier == null) {
            throw new RouterException(HttpStatus.SERVICE_UNAVAILABLE, "CLASSIFIER_NOT_AVAILABLE", "未設定分類器：" + type);
        }
        return classifier;
    }

    private static RouterException unavailable(ClassifierException e) {
        return new RouterException(HttpStatus.SERVICE_UNAVAILABLE, "CLASSIFIER_UNAVAILABLE",
                "意圖分類服務暫時無法使用（" + e.errorCode() + "），請稍後再試。");
    }

    private RoutingOutcome invokeAgents(String requestId, String question, CallerContext caller, RouteDecision decision,
                                        List<String> warnings, long start) {
        List<AgentDefinition> permitted = new ArrayList<>();
        List<AgentDefinition> denied = new ArrayList<>();
        for (AgentDefinition agent : decision.agents()) {
            (accessPolicy.canInvoke(caller, agent) ? permitted : denied).add(agent);
        }
        if (permitted.isEmpty()) {
            throw new RouterException(HttpStatus.FORBIDDEN, "AGENT_ACCESS_DENIED",
                    "您沒有使用此問題所需 Agent 的權限：" + denied.stream().map(AgentDefinition::name).toList());
        }

        List<AgentCallResult> invoked = agentInvoker.invokeAll(permitted, requestId, question, caller.principal());
        List<AgentCallResult> results = new ArrayList<>();
        for (AgentDefinition agent : decision.agents()) {
            if (denied.contains(agent)) {
                results.add(AgentCallResult.accessDenied(agent));
            } else {
                invoked.stream().filter(r -> r.agentId().equals(agent.id())).findFirst().ifPresent(results::add);
            }
        }
        long successes = results.stream().filter(AgentCallResult::isSuccess).count();
        RoutingOutcome.Status status = successes == results.size() ? RoutingOutcome.Status.SUCCESS
                : successes == 0 ? RoutingOutcome.Status.FAILED : RoutingOutcome.Status.PARTIAL;
        String answer = status == RoutingOutcome.Status.FAILED && results.size() == 1
                ? "Agent 未能回覆（" + results.getFirst().errorCode() + "），請稍後再試。"
                : aggregator.aggregate(decision, results);
        return new RoutingOutcome(requestId, decision, status, answer, results, elapsedMs(start), warnings);
    }

    private void audit(RoutingOutcome outcome, CallerContext caller, String question) {
        RouteDecision d = outcome.decision();
        String code = outcome.results().stream()
                .filter(r -> !r.isSuccess()).map(AgentCallResult::errorCode).findFirst().orElse(null);
        auditLogger.log(new RoutingAuditLogger.AuditEntry(outcome.requestId(), caller.principal(), question,
                d.classifier().name(), d.route(), d.agents().stream().map(AgentDefinition::id).toList(),
                d.confidence(), outcome.status().name(), outcome.durationMs(), code, outcome.results()));
    }

    private void auditFailure(String requestId, CallerContext caller, String question, RouteDecision decision,
                              String errorCode, long start) {
        auditLogger.log(new RoutingAuditLogger.AuditEntry(requestId, caller.principal(), question,
                decision == null ? "-" : decision.classifier().name(),
                decision == null ? "-" : decision.route(),
                decision == null ? List.of() : decision.agents().stream().map(AgentDefinition::id).toList(),
                decision == null ? null : decision.confidence(), "REJECTED", elapsedMs(start), errorCode, null));
    }

    private static long elapsedMs(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }
}
