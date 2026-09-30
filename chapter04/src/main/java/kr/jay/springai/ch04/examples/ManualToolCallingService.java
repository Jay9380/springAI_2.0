package kr.jay.springai.ch04.examples;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;

/**
 * [4.4.5 / Step4 예제 4.36] 사용자가 제어하는 툴 실행 — ToolCallingAdvisor 없이 루프를 직접 돈다.
 *
 * <pre>
 *   ① ChatModel.call(질문 + 도구 목록)                 2.0의 ChatModel은 도구를 실행하지 않는다
 *   ② 응답에 도구 요청이 있나? (hasToolCalls)          없으면 그게 최종 답
 *   ③ [여기서 끼어들 수 있다] 승인·감사 로그·권한 검사
 *   ④ ToolCallingManager.executeToolCalls()            실제 실행은 여전히 매니저에게 맡긴다
 *   ⑤ 대화 기록(질문·도구 요청·도구 결과)으로 다시 ①    MAX_TOOL_LOOPS번까지만
 * </pre>
 * 운영 코드답게 두 가지를 더했다: 반복 횟수 상한(무한 루프 방지), 상태 변경 도구의 사람 승인(Human-in-the-loop).
 */
public class ManualToolCallingService {

    public static final int MAX_TOOL_LOOPS = 5;

    private static final SystemMessage SYSTEM = new SystemMessage("""
            당신은 계산과 재고 확인을 툴로 처리하는 AI 어시스턴트입니다.
            툴 결과를 확인한 뒤 한국어로 짧게 답변합니다. 툴이 실패하면 그 이유를 사용자에게 설명합니다.
            """);

    private final ChatModel chatModel;
    private final ToolCallingManager toolCallingManager;
    private final List<ToolCallback> toolCallbacks;
    private final Set<String> needsApproval;
    private final Predicate<AssistantMessage.ToolCall> approver;

    /**
     * @param needsApproval 실행 전에 사람 승인이 필요한 도구 이름 (상태를 바꾸는 도구)
     * @param approver      승인 여부를 묻는 함수 (CLI에서는 y/n 입력, 테스트에서는 고정값)
     */
    public ManualToolCallingService(ChatModel chatModel, ToolCallingManager toolCallingManager,
                                    List<ToolCallback> toolCallbacks, Set<String> needsApproval,
                                    Predicate<AssistantMessage.ToolCall> approver) {
        this.chatModel = chatModel;
        this.toolCallingManager = toolCallingManager;
        this.toolCallbacks = toolCallbacks;
        this.needsApproval = needsApproval;
        this.approver = approver;
    }

    public String ask(String question) {
        // 옵션에는 '도구 목록'을 담는다. 모델은 이 목록을 보고 도구를 요청할 뿐 실행하지 않는다.
        // 주의(책 4.3.4, 실측): ChatModel에 준 요청 옵션은 기본 옵션과 '합쳐지지 않고 통째로 대체'된다.
        // ToolCallingChatOptions.builder()로 새로 만들면 모델 이름(qwen3.5:4b)·temperature까지 사라져서
        // Ollama가 "model cannot be null or empty"로 거부했다. 그래서 모델의 기본 옵션을 복제(mutate)한 뒤 도구만 더한다.
        ToolCallingChatOptions options = toolOptions(chatModel, toolCallbacks);
        Prompt prompt = new Prompt(List.of(SYSTEM, new UserMessage(question)), options);
        ChatResponse response = chatModel.call(prompt);

        for (int loop = 0; loop < MAX_TOOL_LOOPS && response.hasToolCalls(); loop++) {
            // ③ 사람 승인: 상태를 바꾸는 도구는 실행 전에 묻는다
            for (AssistantMessage.ToolCall call : response.getResult().getOutput().getToolCalls()) {
                if (needsApproval.contains(call.name()) && !approver.test(call)) {
                    return "사용자가 '" + call.name() + "' 실행을 거절하여 작업을 중단했습니다.";
                }
            }

            ToolExecutionResult result = toolCallingManager.executeToolCalls(prompt, response);   // ④
            if (result.returnDirect()) {                                                          // 직접 반환
                return ToolExecutionResult.buildGenerations(result).getFirst().getOutput().getText();
            }
            List<Message> history = result.conversationHistory();       // [system, user, 도구 요청, 도구 결과]
            prompt = new Prompt(history, options);
            response = chatModel.call(prompt);                           // ⑤ 결과를 붙여 다시 호출
        }

        if (response.hasToolCalls()) {
            // 상한에 걸림: 모델이 계속 도구만 요청한다 → 끝없는 루프(=끝없는 비용)를 끊는다
            return "도구 호출이 " + MAX_TOOL_LOOPS + "회를 넘어 중단했습니다. 질문을 더 구체적으로 해 주세요.";
        }
        return response.getResult().getOutput().getText();
    }

    static ToolCallingChatOptions toolOptions(ChatModel chatModel, List<ToolCallback> tools) {
        if (chatModel.getOptions() instanceof ToolCallingChatOptions defaults) {
            return defaults.mutate().toolCallbacks(tools).build();     // 기본값(모델명 등) 유지 + 도구 추가
        }
        return ToolCallingChatOptions.builder().toolCallbacks(tools).build();
    }
}
