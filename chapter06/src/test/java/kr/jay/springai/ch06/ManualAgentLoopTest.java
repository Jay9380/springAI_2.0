package kr.jay.springai.ch06;

import static kr.jay.springai.ch06.ScriptedModel.text;
import static kr.jay.springai.ch06.ScriptedModel.toolCall;
import static org.assertj.core.api.Assertions.assertThat;

import kr.jay.springai.ch06.loop.CustomerTools;
import kr.jay.springai.ch06.loop.ManualAgentLoop;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.MessageType;

class ManualAgentLoopTest {

    @Test
    void chainsTwoTools_thenAnswers() {
        ScriptedModel model = new ScriptedModel((n, p) -> switch (n) {
            case 1 -> toolCall("getCustomer", "{\"id\":42}");
            case 2 -> toolCall("getRecentOrders", "{\"customerId\":42}");
            default -> text("김지니(GOLD), 주문 2건");
        });
        var result = new ManualAgentLoop(model, 5).run("42번 고객", new CustomerTools());

        assertThat(result.stoppedByLimit()).isFalse();
        assertThat(result.steps()).isEqualTo(2);
        assertThat(result.answer()).isEqualTo("김지니(GOLD), 주문 2건");
        assertThat(result.trace()).anyMatch(t -> t.contains("관찰: getCustomer") && t.contains("김지니"));
        // 세 번째 호출의 프롬프트에는 앞선 두 번의 툴 요청과 결과가 모두 쌓여 있다 (모델은 기억하지 않는다 — 앱이 다시 보낸다)
        assertThat(model.prompts.get(2).getInstructions())
                .filteredOn(m -> m.getMessageType() == MessageType.TOOL).hasSize(2);
    }

    @Test
    void modelThatNeverStops_isCutByLimit() {
        ScriptedModel model = new ScriptedModel((n, p) -> toolCall("getCustomer", "{\"id\":7}"));
        var result = new ManualAgentLoop(model, 3).run("무한", new CustomerTools());

        assertThat(result.stoppedByLimit()).isTrue();
        assertThat(result.steps()).isEqualTo(3);
        assertThat(model.prompts).hasSize(4);                              // 첫 계획 1 + 재계획 3, 4번째 툴 요청은 실행 안 함
    }
}
