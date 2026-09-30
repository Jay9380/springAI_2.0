package kr.jay.springai.ch04;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import kr.jay.springai.ch04.examples.CalculatorTools;
import kr.jay.springai.ch04.examples.DateTimeTools;
import kr.jay.springai.ch04.support.InventoryTools;
import kr.jay.springai.ch04.support.TodoTools;
import kr.jay.springai.ch04.support.ToolEnabledChatService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;

/** 4.5.3 최종 서비스 — 메모리를 루프 바깥에 둔 배치가 의도대로 동작하는지. */
class FinalServiceTest {

    @Test
    void memoryOutsideToolLoop_storesOnlyQuestionAndFinalAnswer() {
        ScriptedToolModel model = new ScriptedToolModel(n -> n == 1
                ? ScriptedToolModel.toolCall("todo_add", "{\"title\":\"재고 확인\"}")
                : ScriptedToolModel.text("할 일 #1을 등록했어요."));
        ChatMemory memory = MessageWindowChatMemory.builder().build();
        TodoTools todos = new TodoTools();
        var service = new ToolEnabledChatService(ChatClient.builder(model), memory,
                new DateTimeTools(), new CalculatorTools(), todos, new InventoryTools());

        String answer = service.call("재고 확인을 할 일로 추가해줘", "c1");

        assertThat(answer).isEqualTo("할 일 #1을 등록했어요.");
        assertThat(todos.listTodos()).contains("재고 확인");                       // 도구가 실제로 상태를 바꿈
        List<Message> saved = memory.get("c1");
        assertThat(saved).extracting(Message::getMessageType)
                .containsExactly(MessageType.USER, MessageType.ASSISTANT);       // 도구 왕복 메시지는 저장 안 됨
        assertThat(saved.get(1).getText()).isEqualTo("할 일 #1을 등록했어요.");
    }
}
