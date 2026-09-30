package kr.jay.springai.ch04;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import kr.jay.springai.ch04.examples.CalculatorTools;
import kr.jay.springai.ch04.examples.Chapter4ToolCallbacks;
import kr.jay.springai.ch04.examples.FriendlyToolExceptionProcessor;
import kr.jay.springai.ch04.examples.ManualToolCallingService;
import kr.jay.springai.ch04.examples.ToolNames;
import kr.jay.springai.ch04.support.InventoryTools;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;

/** 4.4 툴 실행 제어 — 수동 루프, 승인, 반복 상한, 예외 변환, ToolContext·returnDirect. */
class ToolExecutionTest {

    private final InventoryTools inventory = new InventoryTools();
    private final List<ToolCallback> tools = Arrays.asList(ToolCallbacks.from(new CalculatorTools(), inventory));
    private final ToolCallingManager manager = DefaultToolCallingManager.builder()
            .toolExecutionExceptionProcessor(new FriendlyToolExceptionProcessor()).build();

    private ManualToolCallingService service(ScriptedToolModel model, boolean approve) {
        return new ManualToolCallingService(model, manager, tools, Set.of(ToolNames.PRODUCT_RESERVE), call -> approve);
    }

    @Test
    void manualLoop_executesToolThenReturnsFinalAnswer() {
        ScriptedToolModel model = new ScriptedToolModel(n -> n == 1
                ? ScriptedToolModel.toolCall("calculate", "{\"a\":6,\"operator\":\"*\",\"b\":8}")
                : ScriptedToolModel.text("48입니다."));
        assertThat(service(model, true).ask("6 곱하기 8?")).isEqualTo("48입니다.");
        assertThat(model.prompts).hasSize(2);
    }

    @Test
    void deniedApproval_doesNotExecuteStateChangingTool() {
        // 실패 경로(의도된 차단): 승인을 거절하면 예약 도구가 실행되지 않아 재고가 그대로다
        ScriptedToolModel model = new ScriptedToolModel(n ->
                ScriptedToolModel.toolCall("product_reserve", "{\"sku\":\"SKU-100\",\"quantity\":2}"));
        String answer = service(model, false).ask("SKU-100 2개 예약해줘");
        assertThat(answer).contains("거절");
        assertThat(inventory.stockOf("SKU-100")).isEqualTo(12);
    }

    @Test
    void approvedReservation_changesState() {
        ScriptedToolModel model = new ScriptedToolModel(n -> n == 1
                ? ScriptedToolModel.toolCall("product_reserve", "{\"sku\":\"SKU-100\",\"quantity\":2}")
                : ScriptedToolModel.text("예약했습니다."));
        service(model, true).ask("SKU-100 2개 예약해줘");
        assertThat(inventory.stockOf("SKU-100")).isEqualTo(10);
    }

    @Test
    void runawayModel_isStoppedByLoopLimit() {
        // 실패 경로: 모델이 끝없이 도구만 요청해도 MAX_TOOL_LOOPS에서 끊는다
        ScriptedToolModel model = new ScriptedToolModel(n -> ScriptedToolModel.toolCall("product_list", "{}"));
        String answer = service(model, true).ask("상품 보여줘");
        assertThat(answer).contains("중단");
        assertThat(model.prompts).hasSize(ManualToolCallingService.MAX_TOOL_LOOPS + 1);
    }

    @Test
    void toolException_isTurnedIntoMessageForTheModel_notACrash() {
        ScriptedToolModel model = new ScriptedToolModel(n -> n == 1
                ? ScriptedToolModel.toolCall("calculate", "{\"a\":10,\"operator\":\"/\",\"b\":0}")
                : ScriptedToolModel.text("0으로는 나눌 수 없어요."));
        String answer = service(model, true).ask("10을 0으로 나눠줘");

        assertThat(answer).isEqualTo("0으로는 나눌 수 없어요.");
        Prompt second = model.prompts.get(1);
        ToolResponseMessage toolResult = (ToolResponseMessage) second.getInstructions().getLast();
        assertThat(toolResult.getResponses().get(0).responseData()).contains("실행에 실패").contains("0으로 나눌 수 없습니다");
    }

    @Test
    void returnDirect_skipsSecondModelCall_andToolContextNeverReachesTheModel() {
        ScriptedToolModel model = new ScriptedToolModel(n ->
                ScriptedToolModel.toolCall("session_summary", "{\"topic\":\"Tool Calling\"}"));
        String answer = ChatClient.builder(model)
                .defaultTools(Chapter4ToolCallbacks.sessionSummary(true)).build()
                .prompt().user("세션 요약해줘")
                .toolContext(Map.of("userName", "secret-user", "conversationId", "conv-42"))
                .call().content();

        assertThat(answer).contains("secret-user").contains("conv-42").contains("Tool Calling");   // 도구 결과 그대로
        assertThat(model.prompts).hasSize(1);                                                    // 모델 재호출 없음
        assertThat(model.prompts.get(0).getContents()).doesNotContain("secret-user");            // 모델은 컨텍스트를 못 봄
    }

    @Test
    void returnDirectString_isShownWithoutJsonQuotes() {
        // 회귀 방지: 기본 변환기면 "\"세션 요약\\n…\""처럼 따옴표·\n이 사용자에게 그대로 보였다
        String result = Chapter4ToolCallbacks.sessionSummary(true).call("{\"topic\":\"T\"}",
                new org.springframework.ai.chat.model.ToolContext(Map.of("userName", "u")));
        assertThat(result).startsWith("세션 요약\n").doesNotStartWith("\"");
    }

    @Test
    void manualLoopOptions_keepModelDefaults_insteadOfReplacingThem() {
        // 회귀 방지: ChatModel 요청 옵션은 기본 옵션을 '대체'한다. 새 옵션을 만들면 모델 이름이 사라져 Ollama가 거부했다
        ScriptedToolModel withModel = new ScriptedToolModel(n -> ScriptedToolModel.text("ok")) {
            @Override
            public org.springframework.ai.chat.prompt.ChatOptions getOptions() {
                return org.springframework.ai.model.tool.ToolCallingChatOptions.builder().model("qwen3.5:4b").build();
            }
        };
        service(withModel, true).ask("안녕");
        assertThat(withModel.prompts.get(0).getOptions().getModel()).isEqualTo("qwen3.5:4b");
    }
}
