package com.cx.airouter.service;

import com.cx.airouter.agent.AgentCallResult;
import com.cx.airouter.agent.AgentDefinition;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 整合 Agent 回覆。
 *
 * <p>只做「附來源的串接」：不改寫、不推算、不補充任何數據，避免捏造；
 * 每段答案都標示來源 Agent，失敗的 Agent 明確標示未取得回覆。</p>
 */
@Component
public class AnswerAggregator {

    public String aggregate(RouteDecision decision, List<AgentCallResult> results) {
        if (decision.kind() == RouteDecision.Kind.SINGLE && results.size() == 1 && results.getFirst().isSuccess()) {
            return results.getFirst().answer();
        }
        return results.stream()
                .map(r -> "【" + r.agentName() + "】\n" + (r.isSuccess() ? r.answer() : "（未取得回覆：" + describe(r) + "）"))
                .collect(Collectors.joining("\n\n"));
    }

    public String notRoutedMessage(RouteDecision decision, List<AgentDefinition> candidates) {
        if (decision.kind() == RouteDecision.Kind.OTHER) {
            String names = candidates.stream().map(AgentDefinition::name).collect(Collectors.joining("、"));
            return "此問題不屬於目前支援的 Agent 職責範圍（" + names + "），未轉送至任何 Agent。";
        }
        return "無法有把握地判斷應由哪個 Agent 處理（" + decision.reason() + "），為避免錯誤轉送，未呼叫任何 Agent。請補充問題細節後再試。";
    }

    private static String describe(AgentCallResult r) {
        return switch (r.status()) {
            case TIMEOUT -> "逾時";
            case ACCESS_DENIED -> "無此 Agent 的使用權限";
            case ERROR -> "Agent 發生錯誤";
            case SUCCESS -> "";
        };
    }
}
