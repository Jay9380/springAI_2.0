package kr.jay.springai.ch06.advisor;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;

/**
 * [6.3.3 예제 6.13] 툴 루프 메트릭 어드바이저 — 루프 '안쪽'(+400)에 두어 반복마다 기록한다.
 *
 * <ul>
 *   <li>{@code agent.tool.loop.iterations} : 모델 호출(반복) 수</li>
 *   <li>{@code agent.tool.calls{tool=이름}} : 툴별 호출 수</li>
 *   <li>{@code agent.tool.loop.tokens} : 반복당 토큰 분포</li>
 * </ul>
 * 스프링 AI는 LLM 호출마다 {@code gen_ai.*} 계측을 이미 남긴다. 이건 '루프 단위'로 묶어 보는 집계다.
 * BaseAdvisor를 구현하면 before/after만 쓰면 되고 call·stream 둘 다 지원된다.
 */
public class ToolLoopMetricsAdvisor implements BaseAdvisor {

    private final MeterRegistry registry;

    public ToolLoopMetricsAdvisor(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
        return request;
    }

    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
        ChatResponse cr = response.chatResponse();
        if (cr == null || cr.getResult() == null) {
            return response;
        }
        registry.counter("agent.tool.loop.iterations").increment();
        cr.getResult().getOutput().getToolCalls()
                .forEach(tc -> registry.counter("agent.tool.calls", "tool", tc.name()).increment());
        Usage usage = cr.getMetadata().getUsage();
        if (usage != null && usage.getTotalTokens() != null && usage.getTotalTokens() > 0) {
            DistributionSummary.builder("agent.tool.loop.tokens").baseUnit("tokens")
                    .register(registry).record(usage.getTotalTokens());
        }
        return response;
    }

    @Override
    public int getOrder() {
        return BaseAdvisor.HIGHEST_PRECEDENCE + 400;            // +300(툴 루프)보다 크다 → 루프 안 → 매 반복
    }
}
