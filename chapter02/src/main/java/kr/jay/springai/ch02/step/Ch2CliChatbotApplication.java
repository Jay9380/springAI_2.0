package kr.jay.springai.ch02.step;

import java.util.UUID;

import kr.jay.springai.ch02.advisor.ElapsedTimeAdvisor;
import kr.jay.springai.ch02.support.ChatConsole;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * [2.9.4] 최종 CLI 챗봇 — Step1~5를 하나의 ChatClient로 조립한다.
 *
 * <p>실행: {@code ./mvnw -pl chapter02 spring-boot:run} (기본 단계)
 *
 * <pre>
 *   페르소나  : defaultSystem              (Step2)
 *   기억      : MessageChatMemoryAdvisor   (Step4)
 *   관찰      : SimpleLoggerAdvisor, ElapsedTimeAdvisor (Step5)
 *   스트리밍  : stream().content()          (Step1)
 * </pre>
 * 이것이 책이 말하는 '증강된 LLM(augmented LLM)'의 최소 형태다(그림 2.7).
 * 여기에 지식(RAG, 3장)과 도구(툴 호출, 4장)를 더하면 에이전트의 재료가 모두 갖춰진다.
 *
 * <p>matchIfMissing = true: spring.ai.cli.step 설정이 아예 없을 때도 이 러너가 기본으로 돈다.
 */
@Component
@ConditionalOnProperty(prefix = "spring.ai.cli", name = "step", havingValue = "ch2-final", matchIfMissing = true)
public class Ch2CliChatbotApplication implements CommandLineRunner {

    private final ChatClient chatClient;
    private final String conversationId = UUID.randomUUID().toString();
    private final String model;
    private final String baseUrl;

    public Ch2CliChatbotApplication(ChatClient.Builder builder,
                                    @Value("${spring.ai.ollama.chat.model}") String model,
                                    @Value("${spring.ai.ollama.base-url}") String baseUrl) {
        this.model = model;
        this.baseUrl = baseUrl;

        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(20)
                .build();

        this.chatClient = builder
                .defaultSystem(Ch2Step2_PromptTemplate.SYSTEM_PROMPT)          // 페르소나
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),  // 기억
                        new SimpleLoggerAdvisor(),                             // 로깅
                        new ElapsedTimeAdvisor())                              // 응답 시간
                .build();
    }

    @Override
    public void run(String... args) {
        System.out.println("─".repeat(50));
        System.out.println(" Spring AI CLI Chatbot  (Chapter 2, Final)");
        System.out.println(" Persona, Memory, Advisor, Streaming");
        System.out.println(" Model: " + model + " (via Ollama @ " + baseUrl + ")");
        System.out.println("─".repeat(50));
        System.out.println("Conversation ID: " + conversationId);

        ChatConsole.run("[Ch2] Final: 통합 챗봇", input ->
                chatClient.prompt()
                        .user(input)
                        .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                        .stream()
                        .content()
                        .doOnNext(System.out::print)
                        .blockLast());
    }
}
