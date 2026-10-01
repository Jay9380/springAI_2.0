package kr.jay.springai.ch06;

import static kr.jay.springai.ch06.ScriptedModel.text;
import static kr.jay.springai.ch06.ScriptedModel.toolCall;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


import kr.jay.springai.ch06.context.ContextEngineeredAgent;
import kr.jay.springai.ch06.context.ContextEngineeredAgent.Role;
import kr.jay.springai.ch06.context.OrderTools;
import kr.jay.springai.ch06.context.ProjectFileTools;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.model.tool.ToolCallingChatOptions;

/** 6.2 '집행 영역'은 모델이 무엇을 하든 코드가 보장해야 한다 — 그래서 모델이 규칙을 어기는 시나리오를 일부러 만든다. */
class ContextEngineeringTest {

    @Test
    void viewer_neverSeesCancelSchema_operatorDoes() {
        ScriptedModel model = new ScriptedModel((n, p) -> text("ok"));
        var viewer = new ContextEngineeredAgent(ChatClient.builder(model), Role.VIEWER, new OrderTools(), new ProjectFileTools());
        viewer.ask("안녕", "c1");

        var sent = (ToolCallingChatOptions) model.prompts.getFirst().getOptions();
        assertThat(sent.getToolCallbacks()).extracting(t -> t.getToolDefinition().name())
                .containsExactlyInAnyOrder("getCustomerOrders", "readProjectFile");     // 모델이 실제로 받은 스키마

        var operator = new ContextEngineeredAgent(ChatClient.builder(model), Role.OPERATOR, new OrderTools(), new ProjectFileTools());
        assertThat(operator.exposedToolNames()).contains(OrderTools.CANCEL_ORDER);
    }

    @Test
    void viewer_modelInventingCancelCall_isRejectedAndNothingChanges() {
        // 모델이 스키마에 없는 툴 이름을 지어내 호출하는 최악의 경우
        OrderTools orders = new OrderTools();
        ScriptedModel model = new ScriptedModel((n, p) -> toolCall(OrderTools.CANCEL_ORDER, "{\"orderId\":\"ORD-1001\"}"));
        var viewer = new ContextEngineeredAgent(ChatClient.builder(model), Role.VIEWER, orders, new ProjectFileTools());

        assertThatThrownBy(() -> viewer.ask("ORD-1001 취소", "c1")).hasMessageContaining("No ToolCallback found");
        assertThat(orders.statusOf("ORD-1001")).isEqualTo("배송 준비");
    }

    @Test
    void operator_cancelWorks_andFailureMessageGuidesNextAction() {
        OrderTools orders = new OrderTools();
        ScriptedModel model = new ScriptedModel((n, p) -> switch (n) {
            case 1 -> toolCall(OrderTools.CANCEL_ORDER, "{\"orderId\":\"ORD-1002\"}");
            default -> text("배송 중이라 반품 안내");
        });
        var operator = new ContextEngineeredAgent(ChatClient.builder(model), Role.OPERATOR, orders, new ProjectFileTools());
        operator.ask("ORD-1002 취소", "c1");

        assertThat(orders.statusOf("ORD-1002")).isEqualTo("배송 중");                   // 업무 규칙도 코드가 지킨다
        // 실패 기록이 다음 호출 컨텍스트에 들어간다 (툴 결과는 getText()가 아니라 ToolResponseMessage.getResponses()에 있다)
        assertThat(model.prompts.get(1).getInstructions())
                .filteredOn(m -> m instanceof ToolResponseMessage)
                .flatExtracting(m -> ((ToolResponseMessage) m).getResponses())
                .extracting(ToolResponseMessage.ToolResponse::responseData)
                .anyMatch(d -> d.contains("반품 절차를 안내"));
    }

    @Test
    void projectInstructions_areInjectedIntoSystemPrompt() {
        ScriptedModel model = new ScriptedModel((n, p) -> text("ok"));
        new ContextEngineeredAgent(ChatClient.builder(model), Role.VIEWER, new OrderTools(), new ProjectFileTools()).ask("hi", "c1");

        assertThat(model.prompts.getFirst().getSystemMessage().getText())
                .contains("<정책>").contains("자바 21 record를 사용한다")
                .contains("조회 권한만");                                       // 스키마 제거(집행)와 짝인 역할 안내(전달)
    }

    @Test
    void readFile_allowsProjectDocs() {
        assertThat(new ProjectFileTools().readProjectFile("README.md")).contains("배송 시작 전에만");
    }

    @ParameterizedTest
    @ValueSource(strings = {"secrets/db.properties", "./secrets/db.properties", "Secrets/db.properties",
            "SECRETS/db.properties", "docs/../secrets/db.properties", "../pom.xml", "/etc/passwd", "secrets\\db.properties"})
    void readFile_blocksSecretsAndEscapes_whateverTheSpelling(String path) {
        String result = new ProjectFileTools().readProjectFile(path);
        assertThat(result).startsWith("거부").doesNotContain("FAKE-DO-NOT-LEAK");
    }

    @Test
    void missingFile_listsAlternatives() {
        assertThat(new ProjectFileTools().readProjectFile("NOPE.md")).contains("사용 가능한 파일");
    }
}
