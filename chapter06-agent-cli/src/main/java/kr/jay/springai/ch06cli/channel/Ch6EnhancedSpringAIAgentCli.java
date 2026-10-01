package kr.jay.springai.ch06cli.channel;

import io.micrometer.core.instrument.MeterRegistry;
import kr.jay.springai.ch06cli.orchestration.SpringAIAgent;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * [Step 5·6·Final · 6.6.5~6.6.7 예제 6.48] 강화 에이전트 통합 CLI — 이 장의 모든 요소가 한 번에.
 *
 * <p>실행 (저장소 루트, 운영 서버·5장 지식 서버 먼저):
 * {@code --spring.profiles.active=with-ops,with-knowledge} (기본 step = ch6-final)
 *
 * <p>해 볼 말 (책 pp.639–640):
 * "SKU-100, SKU-200, SKU-300 중 하나만 재고를 점검하고, 사내 재발주 정책에 따라 필요하면 발주까지 진행해줘"
 * → Skill(restock-policy) → AskUserQuestionTool(어느 SKU?) → toolSearchTool → check_stock → calculator_subtract
 *   → place_purchase_order → [승인 요청] (y/n)
 */
@Component
@ConditionalOnProperty(prefix = "spring.ai.cli", name = "step", havingValue = "ch6-final")
public class Ch6EnhancedSpringAIAgentCli implements CommandLineRunner {

    private final SpringAIAgent agent;
    private final MeterRegistry registry;

    public Ch6EnhancedSpringAIAgentCli(@Qualifier("enhancedAgent") SpringAIAgent agent, MeterRegistry registry) {
        this.agent = agent;
        this.registry = registry;
    }

    @Override
    public void run(String... args) {
        AgentCliRunner.run("[Ch6.6] 강화 에이전트 통합 CLI (메타 툴 + 동적 툴 발견 + 관측)", agent, registry);
    }
}
