package kr.jay.springai.ch06cli.channel;

import io.micrometer.core.instrument.MeterRegistry;
import kr.jay.springai.ch06cli.orchestration.SpringAIAgent;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * [Step 1~4 · 6.6.3~6.6.5] 코어 에이전트 CLI. 같은 코드가 연결한 서버에 따라 능력이 늘어난다.
 *
 * <pre>
 *   Step 1  로컬 툴만               --spring.ai.cli.step=ch6-core
 *   Step 2·3 + 운영 서버(승인 게이트) --spring.profiles.active=with-ops --spring.ai.cli.step=ch6-core
 *   Step 4  + 지식 서버(RAG 에이전트) --spring.profiles.active=with-ops,with-knowledge --spring.ai.cli.step=ch6-core
 *   동적 툴 발견을 일찍 켜 보려면     --spring.ai.cli.tool-search.min-tools=5
 * </pre>
 * Step이 올라가도 이 클래스와 SpringAIAgent는 한 줄도 바뀌지 않는다 — 능력은 T3에서 늘어난다(OCP).
 */
@Component
@ConditionalOnProperty(prefix = "spring.ai.cli", name = "step", havingValue = "ch6-core")
public class Ch6CoreAgentCli implements CommandLineRunner {

    private final SpringAIAgent agent;
    private final MeterRegistry registry;

    public Ch6CoreAgentCli(@Qualifier("coreAgent") SpringAIAgent agent, MeterRegistry registry) {
        this.agent = agent;
        this.registry = registry;
    }

    @Override
    public void run(String... args) {
        AgentCliRunner.run("[Ch6.6] 코어 에이전트 CLI", agent, registry);
    }
}
