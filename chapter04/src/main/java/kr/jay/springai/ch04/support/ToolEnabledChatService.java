package kr.jay.springai.ch04.support;

import java.util.Map;

import kr.jay.springai.ch04.examples.CalculatorTools;
import kr.jay.springai.ch04.examples.Chapter4ToolCallbacks;
import kr.jay.springai.ch04.examples.DateTimeTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import reactor.core.publisher.Flux;

/**
 * [4.5.3 예제 4.40] 최종 서비스 — 2장의 메모리·스트리밍 + 4장의 도구를 한 ChatClient에.
 *
 * <p>어드바이저 순서가 핵심이다 (order가 작을수록 바깥):
 * <pre>
 *   MessageChatMemoryAdvisor  (HIGHEST + 200)  루프 '바깥' — 요청당 한 번: 지난 대화를 넣고, 끝나면 질문과 최종 답만 저장
 *   ToolCallingAdvisor        (HIGHEST + 300)  도구 루프 — 루프 도는 동안의 도구 요청·결과 이력은 스스로 관리
 * </pre>
 * 메모리가 루프 바깥에 있으므로 도구 왕복 메시지가 대화 메모리에 쌓이지 않는다(역할이 겹치지 않음).
 * 4.4.4 예제처럼 메모리를 루프 '안'(order 400)에 두려면 disableInternalConversationHistory()로
 * 도구 어드바이저의 자체 이력을 꺼야 메시지가 두 번 들어가는 사고를 막는다.
 */
public class ToolEnabledChatService {

    static final String SYSTEM_PROMPT = """
            당신은 Spring AI Tool Calling 학습을 돕는 친절한 AI 어시스턴트입니다.
            다음 원칙을 지킵니다:
            - 날짜, 계산, 할인, 고객 연락처, 할 일, 상품 재고 같은 작업은 제공된 툴을 사용합니다.
            - 툴 결과를 사용자에게 자연스러운 문장으로 요약합니다.
            - 툴로 확인하지 않은 사실은 단정하지 않습니다.
            - 답변은 한국어로 간결하게 작성합니다.
            """;

    private final ChatClient chatClient;

    public ToolEnabledChatService(ChatClient.Builder builder, ChatMemory chatMemory,
                                  DateTimeTools dateTimeTools, CalculatorTools calculatorTools,
                                  TodoTools todoTools, InventoryTools inventoryTools) {
        this.chatClient = builder.clone()
                .defaultSystem(SYSTEM_PROMPT)
                .defaultTools(dateTimeTools, calculatorTools, todoTools, inventoryTools)       // @Tool 객체
                .defaultTools(Chapter4ToolCallbacks.discountCalculator(),                     // 함수형 도구
                        Chapter4ToolCallbacks.customerContactLookup(),
                        Chapter4ToolCallbacks.sessionSummary(false))
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory)
                                .order(BaseAdvisor.HIGHEST_PRECEDENCE + 200).build(),
                        ToolCallingAdvisor.builder()
                                .advisorOrder(BaseAdvisor.HIGHEST_PRECEDENCE + 300).build())
                .build();
    }

    public Flux<String> stream(String message, String conversationId) {
        return chatClient.prompt()
                .user(message)
                // 같은 대화 ID를 도구 컨텍스트(세션 요약용)와 메모리 어드바이저 양쪽에 준다
                .toolContext(Map.of("userName", "chapter4-cli-user", "conversationId", conversationId))
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .stream()
                .content();
    }

    public String call(String message, String conversationId) {
        return chatClient.prompt()
                .user(message)
                .toolContext(Map.of("userName", "chapter4-cli-user", "conversationId", conversationId))
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .content();
    }
}
