package kr.jay.springai.ch02.step;

import java.util.UUID;

import kr.jay.springai.ch02.support.ChatConsole;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * [Step 4 · 2.7] 다중 턴 문맥을 유지하는 대화 메모리.
 *
 * <p>실행: {@code --spring.ai.cli.step=ch2-step4}
 * <br>확인: "내 이름은 홍길동이야" → "내 이름이 뭐라고 했지?"  (1장 chapter01에서는 모른다고 했던 질문)
 *
 * <p>구조 — '정책'과 '저장소'가 분리돼 있다 (책 그림 2.13):
 * <pre>
 *   ChatMemory (정책: 무엇을 기억할까)            ← MessageWindowChatMemory: 최근 N개만
 *     └ ChatMemoryRepository (저장소: 어디에)     ← InMemory / JDBC / Redis / Mongo ...
 *   MessageChatMemoryAdvisor (언제: 호출 앞뒤에서 꺼내 넣고, 답을 저장)
 * </pre>
 * 저장소만 바꾸면 인메모리 → Redis로 옮겨도 이 클래스의 나머지 코드는 그대로다.
 */
@Component
@ConditionalOnProperty(prefix = "spring.ai.cli", name = "step", havingValue = "ch2-step4")
public class Ch2Step4_Memory implements CommandLineRunner {

    private final ChatClient chatClient;

    /** 대화방 번호. 실서비스에서는 로그인 사용자 ID나 세션 키를 쓴다. */
    private final String conversationId = UUID.randomUUID().toString();

    public Ch2Step4_Memory(ChatClient.Builder builder) {
        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())  // 앱이 꺼지면 사라짐(휘발성)
                .maxMessages(20)                                           // 최근 20개 메시지만 유지
                .build();

        this.chatClient = builder
                // 어드바이저가 매 호출마다 ① 지난 대화를 꺼내 프롬프트 앞에 넣고 ② 이번 질문과 답을 저장한다
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }

    @Override
    public void run(String... args) {
        System.out.println("Conversation ID: " + conversationId);
        ChatConsole.run("[Ch2] Step4: 대화 메모리 (다중 턴, stream)", input ->
                chatClient.prompt()
                        .user(input)
                        // 2.0: 대화 ID를 '호출 시점'에 명시한다. 빠뜨리면 예외가 난다 → 사용자끼리 대화가 섞이는 사고 방지
                        .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                        .stream()
                        .content()
                        .doOnNext(System.out::print)
                        .blockLast());
    }
}
