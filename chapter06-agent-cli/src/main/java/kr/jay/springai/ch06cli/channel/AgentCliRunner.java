package kr.jay.springai.ch06cli.channel;

import java.util.Comparator;
import java.util.UUID;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import kr.jay.springai.ch06cli.orchestration.SpringAIAgent;

/**
 * [6.6.7 예제 6.48] T1 채널 — 같은 conversationId로 여러 턴을 잇는 CLI 루프.
 *
 * <ul>
 *   <li>한 턴이 실패해도 세션은 끊기지 않는다 (6.5에서 '없는 툴 이름' 예외가 CLI를 죽인 경험).</li>
 *   <li>{@code /tools} : 이 에이전트에 등록된 툴 목록</li>
 *   <li>{@code /metrics} : 관측 지표. 스프링 AI가 자동으로 쌓은 {@code gen_ai.client.token.usage}와
 *       ToolLoopMetricsAdvisor의 {@code agent.tool.*}를 같은 MeterRegistry에서 읽는다.</li>
 * </ul>
 * 채널 독립성: 이 클래스만 REST 컨트롤러로 바꾸면 웹 채널이 된다. T2(SpringAIAgent)는 그대로다.
 */
final class AgentCliRunner {

    private AgentCliRunner() {
    }

    static void run(String title, SpringAIAgent agent, MeterRegistry registry) {
        System.out.printf("%s%n  툴 루프: %s, 등록 툴 %d개%n", title, agent.loopAdvisorName(), agent.toolNames().size());
        String conversationId = UUID.randomUUID().toString();
        ChatConsole.run(title, input -> {
            switch (input) {
                case "/tools" -> System.out.print(String.join(", ", agent.toolNames()));
                case "/metrics" -> System.out.print(metrics(registry));
                default -> {
                    try {
                        System.out.print(agent.run(input, conversationId));
                    }
                    catch (RuntimeException e) {
                        System.out.print("(이번 요청 실패: " + e.getMessage() + ")");
                    }
                }
            }
        });
    }

    static String metrics(MeterRegistry registry) {
        StringBuilder sb = new StringBuilder("── 자동 계측 (gen_ai.client.token.usage)");
        registry.find("gen_ai.client.token.usage").meters().stream()
                .sorted(Comparator.comparing(m -> m.getId().getTags().toString()))
                .forEach(m -> sb.append("\n  ")
                        .append(m.getId().getTag("gen_ai.operation.name")).append(" / ")
                        .append(m.getId().getTag("gen_ai.response.model")).append(" / ")
                        .append(m.getId().getTag("gen_ai.token.type")).append(" = ")
                        .append(m instanceof Counter c ? (long) c.count()
                                : m instanceof DistributionSummary d ? (long) d.totalAmount() : m.measure()));
        sb.append("\n── 커스텀 (ToolLoopMetricsAdvisor)\n  반복 ")
                .append((long) registry.counter("agent.tool.loop.iterations").count()).append("회");
        registry.find("agent.tool.calls").counters()
                .forEach(c -> sb.append(", ").append(c.getId().getTag("tool")).append("×").append((long) c.count()));
        return sb.toString();
    }
}
