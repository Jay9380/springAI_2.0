package kr.jay.springai.ch06.loop;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.support.ToolCallbacks;

/**
 * [6.1.4 방식 B, 예제 6.7] 에이전트 루프를 직접 짠다 — 계획 → 행동 → 관찰 → (반영) → 종료?
 *
 * <pre>
 *   chatModel.call(prompt)                   계획: 모델이 다음 행동(툴 호출 or 최종 답) 결정
 *   while (response.hasToolCalls())          종료 조건: 툴 요청이 없으면 끝
 *     executeToolCalls(prompt, response)     행동: 우리 코드가 툴 실행
 *     new Prompt(conversationHistory())      관찰: 툴 요청 + 툴 결과를 대화에 붙임
 *     chatModel.call(prompt)                 다시 계획
 * </pre>
 *
 * <p><b>책 예제와 다른 점 두 가지.</b>
 * <ol>
 *   <li>상한 {@code maxSteps}: 책의 while에는 상한이 없다. 모델이 계속 툴을 요청하면 끝나지 않는다.</li>
 *   <li>옵션: 2.0에서 런타임 옵션은 기본 옵션을 '대체'한다. 툴만 넣은 옵션을 주면 모델 이름이 빠져
 *       "model cannot be null"이 난다(4장에서 실측). 그래서 기본 옵션을 mutate()해서 툴만 얹는다.</li>
 * </ol>
 * 그리고 매 단계를 {@code trace}에 남긴다 — 이게 수동 루프의 장점(모든 단계를 눈으로 본다)이다.
 */
public class ManualAgentLoop {

    public record LoopResult(String answer, List<String> trace, int steps, boolean stoppedByLimit) {
    }

    private final ChatModel chatModel;
    private final ToolCallingManager toolCallingManager = ToolCallingManager.builder().build();
    private final int maxSteps;

    public ManualAgentLoop(ChatModel chatModel, int maxSteps) {
        this.chatModel = chatModel;
        this.maxSteps = maxSteps;
    }

    public LoopResult run(String question, Object... toolObjects) {
        ToolCallingChatOptions options = chatModel.getOptions() instanceof ToolCallingChatOptions defaults
                ? defaults.mutate().toolCallbacks(ToolCallbacks.from(toolObjects)).build()   // 모델명 등 기본값 유지
                : ToolCallingChatOptions.builder().toolCallbacks(ToolCallbacks.from(toolObjects)).build();
        List<String> trace = new ArrayList<>();

        Prompt prompt = new Prompt(question, options);
        ChatResponse response = chatModel.call(prompt);                          // 계획

        int step = 0;
        while (response.hasToolCalls()) {
            if (++step > maxSteps) {
                trace.add("상한 " + maxSteps + "회 도달 → 루프 강제 종료");
                return new LoopResult("작업을 끝내지 못했습니다 (툴 호출 상한 도달).", trace, step - 1, true);
            }
            for (AssistantMessage.ToolCall call : response.getResult().getOutput().getToolCalls()) {
                trace.add("[" + step + "] 행동: " + call.name() + call.arguments());
            }
            ToolExecutionResult result = toolCallingManager.executeToolCalls(prompt, response);   // 행동
            // 툴 결과는 ToolResponseMessage에 담긴다. getText()는 빈 문자열이고 실제 값은 getResponses()에 있다
            if (result.conversationHistory().getLast() instanceof ToolResponseMessage toolResponse) {
                for (ToolResponseMessage.ToolResponse r : toolResponse.getResponses()) {
                    trace.add("[" + step + "] 관찰: " + r.name() + " → " + r.responseData());
                }
            }
            prompt = new Prompt(result.conversationHistory(), options);                // 관찰을 대화에 붙임
            response = chatModel.call(prompt);                                         // 다시 계획
        }
        String answer = response.getResult().getOutput().getText();
        trace.add("종료: 툴 요청 없음 → 최종 답변");
        return new LoopResult(answer, trace, step, false);
    }
}
