package kr.jay.springai.ch04.step;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

import kr.jay.springai.ch04.examples.CalculatorTools;
import kr.jay.springai.ch04.examples.FriendlyToolExceptionProcessor;
import kr.jay.springai.ch04.examples.ManualToolCallingService;
import kr.jay.springai.ch04.examples.ToolNames;
import kr.jay.springai.ch04.support.InventoryTools;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * [Step 4 · 4.4.5 예제 4.36] ToolCallingManager로 실행을 직접 제어 + 사람 승인.
 *
 * <p>실행: {@code --spring.ai.cli.step=ch4-step4}
 * <br>해 볼 말: "SKU-100 재고 확인하고 2개 예약해줘" (예약 전에 y/n 승인을 묻는다), "10을 0으로 나눠줘" (예외 변환)
 *
 * <p>이 단계는 입력 한 줄을 ChatConsole 대신 직접 읽는다. 승인 질문(y/n)도 같은 콘솔에서 받아야 하기 때문이다.
 */
@Component
@ConditionalOnProperty(prefix = "spring.ai.cli", name = "step", havingValue = "ch4-step4")
public class Ch4Step4_ToolCallingManager implements CommandLineRunner {

    private final ChatModel chatModel;
    private final CalculatorTools calculatorTools;
    private final InventoryTools inventoryTools;

    public Ch4Step4_ToolCallingManager(ChatModel chatModel, CalculatorTools calculatorTools, InventoryTools inventoryTools) {
        this.chatModel = chatModel;
        this.calculatorTools = calculatorTools;
        this.inventoryTools = inventoryTools;
    }

    @Override
    public void run(String... args) {
        // 매니저 구성: 예외 → 친절한 메시지, 그리고 2.0.1의 호출 횟수 제한(책에 없음, 기본 도구당 40·전체 150)
        ToolCallingManager manager = DefaultToolCallingManager.builder()
                .toolExecutionExceptionProcessor(new FriendlyToolExceptionProcessor())
                .maxCallsPerTool(10)
                .maxTotalToolCalls(20)
                .build();
        List<ToolCallback> tools = new ArrayList<>(Arrays.asList(ToolCallbacks.from(calculatorTools, inventoryTools)));

        try (Scanner scanner = new Scanner(System.in)) {
            ManualToolCallingService service = new ManualToolCallingService(chatModel, manager, tools,
                    Set.of(ToolNames.PRODUCT_RESERVE),                   // 상태를 바꾸는 도구만 승인 대상
                    call -> {
                        System.out.printf("%n[승인 요청] %s %s 실행할까요? (y/n) ", call.name(), call.arguments());
                        return scanner.hasNextLine() && scanner.nextLine().trim().equalsIgnoreCase("y");
                    });

            System.out.println("=== [Ch4] Step4: ToolCallingManager 직접 제어 + 사람 승인 ===\n종료하려면 /exit 입력\n");
            while (true) {
                System.out.print("> ");
                if (!scanner.hasNextLine()) {
                    break;
                }
                String input = scanner.nextLine().trim();
                if (input.equalsIgnoreCase("/exit")) {
                    break;
                }
                if (!input.isEmpty()) {
                    System.out.println("AI: " + service.ask(input) + "\n");
                }
            }
        }
    }
}
