package kr.jay.springai.ch06.step;

import kr.jay.springai.ch06.advisor.AgentThinking;
import kr.jay.springai.ch06.advisor.WarehouseTools;
import kr.jay.springai.ch06.support.ChatConsole;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.augment.AugmentedToolCallbackProvider;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * [Step 5 · 6.3.4 예제 6.14] 파라미터 증강 — 모델이 '왜 이 툴을 골랐는지'를 받아 낸다.
 *
 * <p>실행: {@code --spring.ai.cli.step=ch6-augment}
 * <br>해 볼 말: "SKU-200 재고가 있어?"
 *
 * <p>WarehouseTools 코드는 그대로다. AugmentedToolCallbackProvider가 툴 스키마에 innerThought·confidence를 덧붙이고,
 * 모델이 채운 값을 argumentConsumer로 꺼낸 뒤, removeExtraArgumentsAfterProcessing(true)로 원래 툴에는 원래 인자만 넘긴다.
 * 감사 로그·디버깅에 쓴다: "에이전트가 왜 그 툴을 불렀나?"에 답할 수 있게 된다.
 */
@Component
@ConditionalOnProperty(prefix = "spring.ai.cli", name = "step", havingValue = "ch6-augment")
public class Ch6Step5_AugmentedToolArguments implements CommandLineRunner {

    private final ChatClient chatClient;

    public Ch6Step5_AugmentedToolArguments(ChatClient.Builder builder) {
        AugmentedToolCallbackProvider<AgentThinking> provider = AugmentedToolCallbackProvider.<AgentThinking>builder()
                .toolObject(new WarehouseTools())
                .argumentType(AgentThinking.class)
                .argumentConsumer(event -> System.out.printf("%n  [추론 로그] 툴=%s | 추론=%s | 신뢰도=%s%n",
                        event.toolDefinition().name(),
                        event.arguments() == null ? "(없음)" : event.arguments().innerThought(),
                        event.arguments() == null ? "(없음)" : event.arguments().confidence()))
                .removeExtraArgumentsAfterProcessing(true)
                .build();
        this.chatClient = builder.clone()
                .defaultSystem("당신은 창고 관리 에이전트입니다. 재고는 반드시 툴로 확인하고 한국어로 짧게 답합니다.")
                .defaultToolCallbacks(provider)
                .build();
    }

    @Override
    public void run(String... args) {
        ChatConsole.run("[Ch6] Step5: 파라미터 증강 (툴 호출의 '왜'를 기록)",
                input -> System.out.print(chatClient.prompt().user(input).call().content()));
    }
}
