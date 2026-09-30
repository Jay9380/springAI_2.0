package kr.jay.springai.ch04.step;

import java.util.UUID;

import kr.jay.springai.ch04.examples.CalculatorTools;
import kr.jay.springai.ch04.examples.DateTimeTools;
import kr.jay.springai.ch04.support.ChatConsole;
import kr.jay.springai.ch04.support.InventoryTools;
import kr.jay.springai.ch04.support.TodoTools;
import kr.jay.springai.ch04.support.ToolEnabledChatService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * [4.5.3] 툴 지원 AI 챗봇 CLI — 4장 통합.
 *
 * <p>실행: {@code ./mvnw -pl chapter04 spring-boot:run}  (기본 단계)
 *
 * <p>책 표 4.12의 질문을 순서대로 해 보자:
 * 오늘 날짜 알려줘 → 35000원 상품을 18% 할인하면 얼마야? → 고객 C-100 연락처 확인해줘 →
 * 상품 목록 보여줘 → SKU-100 재고 2개 예약하고 그 내용을 할 일로 추가해줘 → 할 일 목록 보여줘 →
 * 이번 대화 학습 주제를 세션 요약해줘
 */
@Component
@ConditionalOnProperty(prefix = "spring.ai.cli", name = "step", havingValue = "ch4-final", matchIfMissing = true)
public class Ch4ToolCliChatbotApplication implements CommandLineRunner {

    private final ToolEnabledChatService service;
    private final String conversationId = UUID.randomUUID().toString();

    public Ch4ToolCliChatbotApplication(ChatClient.Builder builder, DateTimeTools dateTimeTools,
                                        CalculatorTools calculatorTools, TodoTools todoTools,
                                        InventoryTools inventoryTools) {
        var memory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(20)
                .build();
        this.service = new ToolEnabledChatService(builder, memory, dateTimeTools, calculatorTools, todoTools, inventoryTools);
    }

    @Override
    public void run(String... args) {
        System.out.println("─".repeat(60));
        System.out.println(" Spring AI Tool Calling CLI  (Chapter 4, Final)");
        System.out.println(" 도구: 날짜 · 계산 · 할인 · 고객 연락처 · 재고 · 할 일 · 세션 요약");
        System.out.println("─".repeat(60));
        System.out.println("Conversation ID: " + conversationId);
        ChatConsole.run("[Ch4] Final: 툴 지원 챗봇 (메모리 + 도구 + 스트리밍)", input ->
                service.stream(input, conversationId).doOnNext(System.out::print).blockLast());
    }
}
