package kr.jay.springai.ch02;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

/** 2.7 대화 메모리 — 메모리의 정체는 '지난 메시지를 다시 보내 주기'라는 것을 가짜 모델로 확인한다. */
class ChatMemoryTest {

    private final List<Prompt> sent = new ArrayList<>();

    private final ChatModel fake = prompt -> {
        sent.add(prompt);
        return new ChatResponse(List.of(new Generation(new AssistantMessage("응답" + sent.size()))));
    };

    private ChatClient clientWith(ChatMemory memory) {
        return ChatClient.builder(fake)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(memory).build())
                .build();
    }

    @Test
    void advisor_replaysPreviousTurnInNextRequest() {
        var client = clientWith(MessageWindowChatMemory.builder().build());

        client.prompt().user("내 이름은 홍길동").advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "c1")).call().content();
        client.prompt().user("내 이름은?").advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "c1")).call().content();

        List<Message> second = sent.get(1).getInstructions();
        assertThat(second).extracting(Message::getText).containsExactly("내 이름은 홍길동", "응답1", "내 이름은?");
    }

    @Test
    void differentConversationIds_doNotShareHistory() {
        var client = clientWith(MessageWindowChatMemory.builder().build());

        client.prompt().user("비밀: 파란색").advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "alice")).call().content();
        client.prompt().user("비밀이 뭐야?").advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "bob")).call().content();

        assertThat(sent.get(1).getInstructions()).extracting(Message::getText).containsExactly("비밀이 뭐야?");
    }

    @Test
    void missingConversationId_isRejectedIn2_0() {
        // 실패 경로: 2.0.1은 대화 ID가 없으면 호출을 거부한다 (BaseChatMemoryAdvisor의 Assert).
        // 1.x(M8)는 조용히 "default" 대화 하나를 모두가 공유했다 → 사용자 간 대화 유출 위험.
        var client = clientWith(MessageWindowChatMemory.builder().build());
        assertThatThrownBy(() -> client.prompt().user("안녕").call().content())
                .hasMessageContaining("conversationId");
    }

    @Test
    void window_evictsOldMessages_butKeepsSystemMessage() {
        ChatMemory memory = MessageWindowChatMemory.builder().maxMessages(3).build();
        memory.add("w", new SystemMessage("페르소나"));
        for (int i = 1; i <= 4; i++) {
            memory.add("w", new UserMessage("q" + i));
            memory.add("w", new AssistantMessage("a" + i));
        }

        List<Message> kept = memory.get("w");
        assertThat(kept).hasSize(3);
        assertThat(kept.get(0).getMessageType()).isEqualTo(MessageType.SYSTEM);   // 시스템 메시지는 보호
        assertThat(kept).extracting(Message::getText).containsExactly("페르소나", "q4", "a4");
    }
}
