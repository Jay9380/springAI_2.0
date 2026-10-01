package kr.jay.springai.ch06.step;

import kr.jay.springai.ch06.loop.CustomerTools;
import kr.jay.springai.ch06.loop.ManualAgentLoop;
import kr.jay.springai.ch06.support.ChatConsole;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * [Step 2 · 6.1.3~6.1.4] 자율 에이전트 루프를 직접 돌리고, 매 단계를 출력한다.
 *
 * <p>실행: {@code --spring.ai.cli.step=ch6-loop}
 * <br>해 볼 말: "ID 42 고객의 등급과 최근 주문을 알려줘" (고객 → 주문, 툴 2번 연쇄)
 *
 * <p>Step 1의 워크플로와 비교: 여기서는 몇 번 툴을 부를지, 어떤 순서로 부를지 코드에 없다. 모델이 정한다.
 * 코드가 정하는 건 '언제 멈출지'(툴 요청 없음 또는 상한)뿐이다.
 */
@Component
@ConditionalOnProperty(prefix = "spring.ai.cli", name = "step", havingValue = "ch6-loop")
public class Ch6Step2_ManualAgentLoop implements CommandLineRunner {

    private final ManualAgentLoop loop;

    public Ch6Step2_ManualAgentLoop(ChatModel chatModel) {
        this.loop = new ManualAgentLoop(chatModel, 5);
    }

    @Override
    public void run(String... args) {
        ChatConsole.run("[Ch6] Step2: 수동 에이전트 루프 (계획→행동→관찰)", input -> {
            var result = loop.run(input, new CustomerTools());
            result.trace().forEach(t -> System.out.println("\n  " + t));
            System.out.print("\n" + result.answer());
        });
    }
}
