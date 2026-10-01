package kr.jay.springai.ch06.step;

import java.util.UUID;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import kr.jay.springai.ch06.advisor.ProbeAdvisor;
import kr.jay.springai.ch06.advisor.SafeGuardToolCallingAdvisor;
import kr.jay.springai.ch06.advisor.ToolLoopMetricsAdvisor;
import kr.jay.springai.ch06.advisor.WarehouseTools;
import kr.jay.springai.ch06.support.ChatConsole;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * [Step 4 · 6.3.2~6.3.4] 재귀 어드바이저 — 체인의 세 구역을 숫자로 확인한다.
 *
 * <p>실행: {@code --spring.ai.cli.step=ch6-advisor-loop}
 * <br>해 볼 말: "SKU-100과 SKU-300 재고를 확인하고 합계를 알려줘", "SKU-100 감사 로그에서 출고가 몇 번이야?"(집계 툴 선택)
 *
 * <pre>
 *   +200  MessageChatMemoryAdvisor   루프 밖 → 요청당 1번 (최종 질문·답만 저장)
 *   +250  probe "outer"              루프 밖 → 요청당 1번
 *   +300  SafeGuardToolCallingAdvisor  ← 루프 (반복 상한 5, 토큰 예산, 툴 결과 600자 자르기)
 *   +400  ToolLoopMetricsAdvisor     루프 안 → 매 반복
 *   +500  probe "inner"              루프 안 → 매 반복
 *         ChatModel
 * </pre>
 * 답변마다 outer/inner 호출 수, 메트릭, 메모리에 저장된 메시지 수를 출력한다.
 */
@Component
@ConditionalOnProperty(prefix = "spring.ai.cli", name = "step", havingValue = "ch6-advisor-loop")
public class Ch6Step4_RecursiveAdvisors implements CommandLineRunner {

    private final ChatClient chatClient;
    private final ChatMemory memory = MessageWindowChatMemory.builder().maxMessages(50).build();
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final ProbeAdvisor outer = new ProbeAdvisor("outer", BaseAdvisor.HIGHEST_PRECEDENCE + 250);
    private final ProbeAdvisor inner = new ProbeAdvisor("inner", BaseAdvisor.HIGHEST_PRECEDENCE + 500);

    public Ch6Step4_RecursiveAdvisors(ChatClient.Builder builder) {
        this.chatClient = builder.clone()
                .defaultSystem("당신은 창고 관리 에이전트입니다. 재고와 로그는 반드시 툴로 확인하고 한국어로 짧게 답합니다.")
                .defaultTools(new WarehouseTools())
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(memory).order(BaseAdvisor.HIGHEST_PRECEDENCE + 200).build(),
                        outer,
                        new SafeGuardToolCallingAdvisor(5, 50_000, 600),
                        new ToolLoopMetricsAdvisor(registry),
                        inner)
                .build();
    }

    @Override
    public void run(String... args) {
        String conversationId = UUID.randomUUID().toString();
        ChatConsole.run("[Ch6] Step4: 재귀 어드바이저 (루프 안/밖)", input -> {
            outer.reset();
            inner.reset();
            System.out.print(chatClient.prompt().user(input)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                    .call().content());
            System.out.printf("%n  [관측] outer(루프 밖)=%d회, inner(루프 안)=%d회, 누적 반복=%.0f, 툴 호출=%s, 메모리 메시지=%d개",
                    outer.calls(), inner.calls(),
                    registry.counter("agent.tool.loop.iterations").count(),
                    registry.find("agent.tool.calls").counters().stream()
                            .map(c -> c.getId().getTag("tool") + "×" + (int) c.count()).toList(),
                    memory.get(conversationId).size());
        });
    }
}
