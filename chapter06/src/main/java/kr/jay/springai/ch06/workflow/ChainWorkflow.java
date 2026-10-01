package kr.jay.springai.ch06.workflow;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import org.springframework.ai.chat.client.ChatClient;

/**
 * [6.1.2 ① 예제 6.1] 프롬프트 체이닝 — 앞 단계의 출력이 다음 단계의 입력.
 *
 * <p>큰 일을 한 번에 시키지 않고 작은 단계로 쪼갠다. 각 단계는 짧고 명확한 입력만 보므로 환각이 줄고
 * 컨텍스트 창 한계도 피한다. 대가는 지연 시간(호출 수만큼 느려짐)이다 → "지연보다 정확도가 중요할 때".
 *
 * <p>책 예제는 단계 사이 검사 없이 for 문만 돈다. 여기서는 체이닝의 진짜 장점인 <b>게이트</b>를 하나 더했다.
 * 단계 출력이 조건을 못 맞추면 다음 단계로 넘기지 않고 멈춘다. 이 검사는 LLM이 아니라 <b>코드</b>가 한다.
 */
public class ChainWorkflow {

    /** 한 단계 = 시스템 지시 + 통과 조건(게이트). 게이트가 필요 없으면 항상 true. */
    public record Step(String name, String instruction, Predicate<String> gate) {
        public static Step of(String name, String instruction) {
            return new Step(name, instruction, out -> true);
        }
    }

    /** 각 단계의 출력을 남겨 두면 어디서 품질이 무너졌는지 추적할 수 있다. */
    public record ChainResult(String output, List<String> trace, boolean completed, String stoppedAt) {
    }

    private final ChatClient chatClient;
    private final List<Step> steps;

    public ChainWorkflow(ChatClient chatClient, List<Step> steps) {
        this.chatClient = chatClient;
        this.steps = steps;
    }

    public ChainResult chain(String userInput) {
        String response = userInput;
        List<String> trace = new ArrayList<>();
        for (Step step : steps) {
            // 책 그대로: "{지시}\n{이전 응답}" 을 한 덩어리 입력으로 보낸다
            String input = String.format("%s%n입력:%n%s", step.instruction(), response);
            response = chatClient.prompt(input).call().content();
            trace.add(step.name() + " → " + response);
            if (!step.gate().test(response)) {
                return new ChainResult(response, trace, false, step.name());   // 게이트 실패: 여기서 멈춘다
            }
        }
        return new ChainResult(response, trace, true, null);
    }
}
