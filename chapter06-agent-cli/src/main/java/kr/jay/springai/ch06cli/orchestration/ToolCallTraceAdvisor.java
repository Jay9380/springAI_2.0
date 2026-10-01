package kr.jay.springai.ch06cli.orchestration;

import java.util.function.Consumer;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;

/**
 * [6.6.3] 루프 안(+350)에서 매 라운드의 [툴 호출]과 [툴 결과]를 사람이 볼 수 있게 출력한다.
 *
 * <ul>
 *   <li>after : 모델 응답에 툴 요청이 있으면 [툴 호출] 이름 + 인자</li>
 *   <li>before : 다음 라운드 요청의 마지막 메시지가 툴 결과면 [툴 결과] (앞 120자)</li>
 * </ul>
 * 스프링 AI 기본 툴 루프는 중간 과정을 숨긴다. 루프 안쪽에 관측 어드바이저를 두면 매 반복이 보인다(6.3.3).
 */
public class ToolCallTraceAdvisor implements BaseAdvisor {

    private final Consumer<String> out;

    public ToolCallTraceAdvisor(Consumer<String> out) {
        this.out = out;
    }

    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
        var instructions = request.prompt().getInstructions();
        Message last = instructions.isEmpty() ? null : instructions.getLast();
        if (last instanceof ToolResponseMessage trm) {
            trm.getResponses().forEach(r -> out.accept("  [툴 결과] " + r.name() + " → " + shorten(r.responseData())));
        }
        return request;
    }

    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
        if (response.chatResponse() != null && response.chatResponse().getResult() != null) {
            response.chatResponse().getResult().getOutput().getToolCalls()
                    .forEach(tc -> out.accept("  [툴 호출] " + tc.name() + " " + tc.arguments()));
        }
        return response;
    }

    static String shorten(String s) {
        String one = s == null ? "" : s.replace("\n", " ");
        return one.length() <= 120 ? one : one.substring(0, 120) + "…";
    }

    @Override
    public int getOrder() {
        return BaseAdvisor.HIGHEST_PRECEDENCE + 350;
    }
}
