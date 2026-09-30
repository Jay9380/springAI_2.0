package kr.jay.springai.ch04.step;

import kr.jay.springai.ch04.examples.CalculatorTools;
import kr.jay.springai.ch04.examples.Chapter4ToolCallbacks;
import kr.jay.springai.ch04.examples.DateTimeTools;
import kr.jay.springai.ch04.support.ChatConsole;
import kr.jay.springai.ch04.support.InventoryTools;
import kr.jay.springai.ch04.support.TodoTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * [Step 5 · 4.4.4 예제 4.37] 어드바이저가 제어하는 툴 실행 — ToolCallingAdvisor를 직접 구성.
 *
 * <p>실행: {@code --spring.ai.cli.step=ch4-step5}
 * <br>해 볼 말: "오늘 날짜와 SKU-200 재고를 확인한 뒤 할 일로 기록해줘" (한 요청에 도구 3개가 연쇄 실행)
 *
 * <p>Step4의 수동 루프를 ToolCallingAdvisor가 대신 돈다. 자동 등록(Step1)과 다른 점은 '직접 구성'한다는 것:
 * <ul>
 *   <li>advisorOrder : 루프의 위치. 이보다 order가 큰 어드바이저는 루프 '안'으로 복사되어 도구 왕복마다 실행된다</li>
 *   <li>toolExecutionEligibilityChecker : 어떤 응답이면 루프를 계속할지 — 무한 루프 가드를 넣는 자리</li>
 * </ul>
 * SimpleLoggerAdvisor(order 0)는 루프 order(HIGHEST+300)보다 커서 루프 안에서 매 왕복을 로그로 남긴다.
 */
@Component
@ConditionalOnProperty(prefix = "spring.ai.cli", name = "step", havingValue = "ch4-step5")
public class Ch4Step5_ToolCallingAdvisor implements CommandLineRunner {

    private final ChatClient chatClient;

    public Ch4Step5_ToolCallingAdvisor(ChatClient.Builder builder, DateTimeTools dateTimeTools,
                                       CalculatorTools calculatorTools, TodoTools todoTools,
                                       InventoryTools inventoryTools) {
        this.chatClient = builder.clone()
                .defaultSystem("당신은 업무 지원 AI입니다. 날짜·계산·재고·할 일은 반드시 툴로 확인하고 한국어로 짧게 답합니다.")
                .defaultTools(dateTimeTools, calculatorTools, todoTools, inventoryTools)
                .defaultTools(Chapter4ToolCallbacks.discountCalculator(), Chapter4ToolCallbacks.customerContactLookup())
                .defaultAdvisors(
                        ToolCallingAdvisor.builder()
                                .advisorOrder(BaseAdvisor.HIGHEST_PRECEDENCE + 300)
                                .toolExecutionEligibilityChecker(r -> r != null && r.hasToolCalls())
                                .build(),
                        new SimpleLoggerAdvisor())
                .build();
    }

    @Override
    public void run(String... args) {
        ChatConsole.run("[Ch4] Step5: ToolCallingAdvisor 직접 구성 (도구 여러 개 연쇄)", input ->
                System.out.print(chatClient.prompt().user(input).call().content()));
    }
}
