package kr.jay.springai.ch04;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import kr.jay.springai.ch04.examples.CalculatorTools;
import kr.jay.springai.ch04.examples.Chapter4ToolCallbacks;
import kr.jay.springai.ch04.examples.DateTimeTools;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.execution.DefaultToolCallResultConverter;

/** 4.2~4.3 툴 명세와 실행 — 모델 없이 도구 자체와 '요청 → 실행 → 재호출' 왕복을 확인한다. */
class ToolSpecTest {

    private ToolCallback find(ToolCallback[] callbacks, String name) {
        return Arrays.stream(callbacks).filter(c -> c.getToolDefinition().name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void toolAnnotation_becomesDefinitionWithSchema_optionalParamNotRequired() {
        ToolCallback[] cbs = ToolCallbacks.from(new DateTimeTools(), new CalculatorTools());
        assertThat(cbs).extracting(c -> c.getToolDefinition().name())
                .containsExactlyInAnyOrder("current_datetime", "current_date", "calculate", "percentage");

        String dateTimeSchema = find(cbs, "current_datetime").getToolDefinition().inputSchema();
        assertThat(dateTimeSchema).contains("\"zoneId\"").contains("IANA 시간대");
        assertThat(dateTimeSchema).doesNotContain("\"required\" : [ \"zoneId\" ]");   // 선택 파라미터

        String calcSchema = find(cbs, "calculate").getToolDefinition().inputSchema();
        assertThat(calcSchema).contains("\"required\"").contains("\"a\"").contains("\"operator\"").contains("\"b\"");
    }

    @Test
    void toolCallback_executesFromJsonArguments() {
        ToolCallback calc = find(ToolCallbacks.from(new CalculatorTools()), "calculate");
        assertThat(calc.call("{\"a\":37,\"operator\":\"*\",\"b\":89}")).isEqualTo("3293.0");
    }

    @Test
    void functionTool_usesInputTypeForSchema_andComputes() {
        ToolCallback discount = Chapter4ToolCallbacks.discountCalculator();
        assertThat(discount.getToolDefinition().inputSchema()).contains("\"price\"").contains("할인율");
        assertThat(discount.call("{\"price\":35000,\"discountRate\":18}")).contains("\"finalPrice\":28700");
    }

    @Test
    void resultConverter_masksEmailBeforeItReachesTheModel() {
        String result = Chapter4ToolCallbacks.customerContactLookup().call("{\"customerId\":\"C-100\"}");
        assertThat(result).contains("김고객").contains("[EMAIL]").doesNotContain("@example.com");
    }

    @Test
    void stringResult_isSerializedAsQuotedJson() {
        // 함정: String 반환 도구는 결과가 따옴표 붙은 JSON 문자열이 된다 → returnDirect로 사용자에게 바로 보내면 따옴표가 보인다
        assertThat(new DefaultToolCallResultConverter().convert("맑음", String.class)).isEqualTo("\"맑음\"");
    }

    @Test
    void chatClient_runsToolAndCallsModelAgainWithToolResult() {
        // 가짜 모델: 1번째 호출엔 "calculate를 불러 달라"는 요청을, 2번째 호출엔 최종 답을 준다
        ScriptedToolModel fake = new ScriptedToolModel(n -> n == 1
                ? ScriptedToolModel.toolCall("calculate", "{\"a\":37,\"operator\":\"*\",\"b\":89}")
                : ScriptedToolModel.text("37 × 89 = 3293입니다."));
        List<Prompt> prompts = fake.prompts;

        String answer = ChatClient.builder(fake).build().prompt()
                .user("37 곱하기 89는?")
                .tools(new CalculatorTools())
                .call().content();

        assertThat(answer).isEqualTo("37 × 89 = 3293입니다.");
        assertThat(prompts).hasSize(2);                                              // 모델 호출 2번
        // ① 첫 요청에는 도구 명세가 옵션으로 실려 간다
        assertThat(((ToolCallingChatOptions) prompts.get(0).getOptions()).getToolCallbacks())
                .extracting(c -> c.getToolDefinition().name()).contains("calculate");
        // ⑤ 두 번째 요청에는 [질문, 도구 요청, 도구 결과]가 실려 간다
        List<Message> second = prompts.get(1).getInstructions();
        assertThat(second).extracting(Message::getMessageType)
                .containsExactly(MessageType.USER, MessageType.ASSISTANT, MessageType.TOOL);
        assertThat(((ToolResponseMessage) second.get(2)).getResponses().get(0).responseData()).isEqualTo("3293.0");
    }
}
