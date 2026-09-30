package kr.jay.springai.ch04.step;

import kr.jay.springai.ch04.examples.CalculatorTools;
import kr.jay.springai.ch04.examples.DateTimeTools;
import kr.jay.springai.ch04.support.ChatConsole;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * [Step 1 · 4.3.1 예제 4.33] @Tool 메서드 기반 도구.
 *
 * <p>실행: {@code --spring.ai.cli.step=ch4-step1}
 * <br>해 볼 말: "지금 런던은 몇 시야?", "37 곱하기 89는?", "250000원의 15%는?"
 *
 * <p>사용자는 도구 이름을 몰라도 된다. 자연어로 말하면 모델이 도구 목록(이름·설명·스키마)을 보고 골라 인자를 만든다.
 * 2.0에서는 도구를 등록한 ChatClient에 ToolCallingAdvisor가 '자동으로' 붙어 실행 → 결과 전달 → 재호출을 처리한다.
 */
@Component
@ConditionalOnProperty(prefix = "spring.ai.cli", name = "step", havingValue = "ch4-step1")
public class Ch4Step1_MethodTools implements CommandLineRunner {

    private final ChatClient chatClient;

    public Ch4Step1_MethodTools(ChatClient.Builder builder, DateTimeTools dateTimeTools, CalculatorTools calculatorTools) {
        this.chatClient = builder.clone()
                .defaultSystem("""
                        당신은 계산과 날짜 확인을 툴로 처리하는 AI 어시스턴트입니다.
                        툴 결과를 확인한 뒤 한국어로 짧게 답변합니다.
                        """)
                // @Tool 메서드를 가진 '객체'를 넘긴다. 프레임워크가 @Tool 메서드를 찾아 각각 ToolCallback으로 바꾼다
                .defaultTools(dateTimeTools, calculatorTools)
                .build();
    }

    @Override
    public void run(String... args) {
        ChatConsole.run("[Ch4] Step1: @Tool 메서드 도구 (날짜·계산)", input ->
                System.out.print(chatClient.prompt().user(input).call().content()));
    }
}
