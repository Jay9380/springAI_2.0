package kr.jay.springai.ch02.demo;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * [2.7] 대화 메모리 — 어드바이저가 몰래 해 주는 일을 손으로 해 본다.
 *
 * <p>실행: {@code --spring.ai.cli.step=ch2-demo-memory}
 */
@Component
@ConditionalOnProperty(prefix = "spring.ai.cli", name = "step", havingValue = "ch2-demo-memory")
public class MemoryDemo implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(MemoryDemo.class);

    private final ChatModel chatModel;
    private final ChatClient.Builder builder;

    public MemoryDemo(ChatModel chatModel, ChatClient.Builder builder) {
        this.chatModel = chatModel;
        this.builder = builder;
    }

    @Override
    public void run(String... args) {
        manualShortTermMemory();
        windowEviction();
        conversationsAreIsolated();
    }

    /**
     * [2.7.5 예제 2.63] ChatClient 없이 ChatModel로 메모리를 '수동' 관리.
     * 조회 → 주입 → 호출 → 저장. 이 네 단계가 MessageChatMemoryAdvisor가 하는 일 그대로다.
     */
    private void manualShortTermMemory() {
        ChatMemory memory = MessageWindowChatMemory.builder().maxMessages(20).build();
        String id = "manual-session-001";

        // 1턴
        memory.add(id, new UserMessage("내 이름은 제임스 본드야."));            // ① 질문을 먼저 기록
        ChatResponse r1 = chatModel.call(new Prompt(memory.get(id)));       // ② 기록 전체를 프롬프트로
        memory.add(id, r1.getResult().getOutput());                         // ③ 답도 기록

        // 2턴
        memory.add(id, new UserMessage("내 이름이 뭐라고 했지? 이름만 답해."));
        List<Message> history = memory.get(id);                             // 지금까지 3개 메시지
        ChatResponse r2 = chatModel.call(new Prompt(history));
        memory.add(id, r2.getResult().getOutput());

        log.info("── ① 수동 메모리: 두 번째 요청에 실린 메시지 수 = {}", history.size());
        log.info("답변: {}", r2.getResult().getOutput().getText());
    }

    /**
     * MessageWindowChatMemory의 '슬라이딩 윈도우'. maxMessages를 넘으면 오래된 것부터 버린다.
     * 단, SystemMessage는 버리지 않는다(초기 페르소나가 사라지는 사고 방지).
     */
    private void windowEviction() {
        ChatMemory memory = MessageWindowChatMemory.builder().maxMessages(3).build();
        String id = "window";
        memory.add(id, new SystemMessage("당신은 요리사입니다."));
        for (int i = 1; i <= 4; i++) {
            memory.add(id, new UserMessage("질문 " + i));
            memory.add(id, new AssistantMessage("답변 " + i));
        }
        log.info("── ② maxMessages=3 윈도우에 남은 것: {}",
                memory.get(id).stream().map(m -> m.getMessageType() + ":" + m.getText()).toList());
    }

    /** 대화 ID가 다르면 기억도 따로다. 여러 사용자가 동시에 쓰는 서비스의 기본 조건. */
    private void conversationsAreIsolated() {
        ChatMemory memory = MessageWindowChatMemory.builder().build();
        ChatClient client = builder.clone()
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(memory).build())
                .build();

        client.prompt().user("내가 좋아하는 색은 파란색이야.")
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "alice")).call().content();
        String bob = client.prompt().user("내가 좋아하는 색이 뭐야? 모르면 모른다고 해.")
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "bob")).call().content();
        String alice = client.prompt().user("내가 좋아하는 색이 뭐야?")
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "alice")).call().content();

        log.info("── ③ bob  (다른 방): {}", bob);
        log.info("── ③ alice(같은 방): {}", alice);
        // 모델이 엉뚱하게 답해도 '기억 장치'가 잘못된 건지, 모델이 기억을 못 쓴 건지 구분하려면 저장소를 직접 본다
        log.info("── ③ 저장소 내용 alice={}개, bob={}개", memory.get("alice").size(), memory.get("bob").size());
        memory.get("alice").forEach(m -> log.info("   alice | {}: {}", m.getMessageType(), m.getText()));
    }
}
