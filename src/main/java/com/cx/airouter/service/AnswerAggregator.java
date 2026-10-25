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

    /**
     * OTHER 與 UNCERTAIN 共用同一則訊息：評估顯示分類器常在兩者間互換，而兩者都不呼叫 Agent，
     * 對使用者來說處理方式相同。實際判定與原因仍保留在回應的 {@code route} / {@code reason} 欄位。
     */
    public String notRoutedMessage(List<AgentDefinition> candidates) {
        String names = candidates.stream().map(AgentDefinition::name).collect(Collectors.joining("、"));
        return "目前只能回答下列 Agent 負責的問題：" + names + "。此問題未轉送至任何 Agent；"
                + "若與上述範圍有關，請說明要查詢的項目（例如期間、指標）後再試。";
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
