package kr.jay.springai.ch06.step;

import java.util.UUID;

import kr.jay.springai.ch06.advisor.ProbeAdvisor;
import kr.jay.springai.ch06.advisor.WarehouseTools;
import kr.jay.springai.ch06.community.ConsoleQuestionHandler;
import kr.jay.springai.ch06.community.LenientTodoWriteCallback;
import kr.jay.springai.ch06.support.ChatConsole;
import org.springaicommunity.agent.tools.AskUserQuestionTool;
import org.springaicommunity.agent.tools.TodoWriteTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * [Step 10 · 6.5.2~6.5.3 예제 6.24~6.25] 명확화 질문(AskUserQuestionTool) + 작업 계획(TodoWriteTool).
 *
 * <p>실행: {@code --spring.ai.cli.step=ch6-ask-todo}
 * <br>해 볼 말:
 * <ul>
 *   <li>"다음 유럽 여행지 추천해줘" — 모호하다 → 모델이 스스로 AskUserQuestionTool을 불러 선택지를 제시</li>
 *   <li>"SKU-100, SKU-200, SKU-300 재고를 각각 확인하고 50개 미만인 것만 골라 요약해줘" — 3단계 이상 → TodoWrite로 계획·진행 상황 표시</li>
 * </ul>
 *
 * <p>두 툴 모두 <b>호출 여부를 LLM이 정한다.</b> 질문이 필요한지, 계획이 필요한지를 모델이 판단해 다른 툴 고르듯 부른다.
 * 코드가 정하는 건 질문을 어떻게 사람에게 전달할지(QuestionHandler)와 계획 변경을 어디에 알릴지(TodoEventHandler)다.
 * TodoWrite 기록이 다음 반복에서도 보이도록 메모리와 함께 쓴다(책의 설계 포인트).
 */
@Component
@ConditionalOnProperty(prefix = "spring.ai.cli", name = "step", havingValue = "ch6-ask-todo")
public class Ch6Step10_AskAndTodo implements CommandLineRunner {

    private final ChatClient chatClient;
    private final ProbeAdvisor trace = new ProbeAdvisor("trace", BaseAdvisor.HIGHEST_PRECEDENCE + 900);

    public Ch6Step10_AskAndTodo(ChatClient.Builder builder) {
        TodoWriteTool todoTool = TodoWriteTool.builder()
                .todoEventHandler(todos -> {
                    System.out.println("\n  [계획]");
                    todos.todos().forEach(item -> System.out.printf("   %-11s %s%n", item.status(), item.content()));
                })
                .build();
        ChatMemory memory = MessageWindowChatMemory.builder().maxMessages(40).build();
        this.chatClient = builder.clone()
                .defaultSystem("""
                        당신은 신중한 업무 에이전트입니다.
                        - 요청이 모호하면 가정하지 말고 AskUserQuestionTool로 선택지를 제시해 물어보세요.
                        - 여러 단계가 필요한 작업은 먼저 TodoWrite로 할 일 목록을 만들고, 각 단계를 in_progress → completed 순으로 처리하세요.
                        - 재고는 반드시 툴로 확인합니다. 답은 한국어로 간결하게.
                        """)
                .defaultTools(AskUserQuestionTool.builder().questionHandler(new ConsoleQuestionHandler()).build(),
                        new WarehouseTools())
                .defaultToolCallbacks(new LenientTodoWriteCallback(todoTool))   // 이중 중첩 스키마를 4B 모델이 틀려서 입력 보정
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(memory).build(), trace)
                .build();
    }

    @Override
    public void run(String... args) {
        String conversationId = UUID.randomUUID().toString();
        ChatConsole.run("[Ch6] Step10: 명확화 질문 + 작업 계획", input -> {
            trace.reset();
            System.out.print(chatClient.prompt().user(input)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                    .call().content());
            System.out.print("\n  [툴 호출] " + trace.toolCalls());
        });
    }
}
