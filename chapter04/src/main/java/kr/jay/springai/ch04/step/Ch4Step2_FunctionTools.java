package kr.jay.springai.ch04.step;

import kr.jay.springai.ch04.examples.Chapter4ToolCallbacks;
import kr.jay.springai.ch04.support.ChatConsole;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * [Step 2 · 4.3.2 / 4.2.5 예제 4.34] FunctionToolCallback 함수형 도구와 결과 변환.
 *
 * <p>실행: {@code --spring.ai.cli.step=ch4-step2}
 * <br>해 볼 말: "35000원 상품을 18% 할인하면 얼마야?", "고객 C-100 연락처 확인해줘"
 *
 * <p>시스템 프롬프트에 '어떤 질문에 어떤 도구를 쓸지'를 명시한다. 작은 모델일수록 효과가 크다.
 * 고객 연락처는 결과 변환기가 이메일을 [EMAIL]로 바꾼 뒤에 모델에게 전달된다.
 */
@Component
@ConditionalOnProperty(prefix = "spring.ai.cli", name = "step", havingValue = "ch4-step2")
public class Ch4Step2_FunctionTools implements CommandLineRunner {

    private final ChatClient chatClient;

    public Ch4Step2_FunctionTools(ChatClient.Builder builder) {
        this.chatClient = builder.clone()
                .defaultSystem("""
                        당신은 상품 할인과 고객 연락처를 툴로 처리하는 AI 어시스턴트입니다.
                        - 할인 계산은 반드시 discount_calculator 툴을 사용합니다.
                        - 고객 연락처 조회는 반드시 customer_contact_lookup 툴을 사용합니다.
                        - 툴 결과를 한국어 한두 문장으로 전달합니다.
                        """)
                .defaultTools(Chapter4ToolCallbacks.discountCalculator(), Chapter4ToolCallbacks.customerContactLookup())
                .build();
    }

    @Override
    public void run(String... args) {
        ChatConsole.run("[Ch4] Step2: 함수형 도구 + 결과 변환 (할인·고객 연락처)", input ->
                System.out.print(chatClient.prompt().user(input).call().content()));
    }
}
