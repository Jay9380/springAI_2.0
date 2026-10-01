package kr.jay.springai.ch06.step;

import java.util.UUID;

import kr.jay.springai.ch06.agent.BasicSpringAIAgent;
import kr.jay.springai.ch06.support.ChatConsole;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * [Step 7 · 6.4.3~6.4.4] 코어만으로 만든 에이전트 + 사람 승인(HITL).
 *
 * <p>실행 (터미널 2개):
 * <pre>
 *   1) java -jar chapter06/target/chapter06-*.jar --spring.profiles.active=ops-server
 *   2) java -jar chapter06/target/chapter06-*.jar --spring.profiles.active=agent --spring.ai.cli.step=ch6-agent
 * </pre>
 * 해 볼 말:
 * <ul>
 *   <li>"지금 몇 시인지 확인하고 SKU-100, SKU-300 재고 합계를 알려줘" — 로컬 툴 연쇄</li>
 *   <li>"payment-api 상태 보고 재시작해줘" — MCP 툴 → 서버가 승인 요청 → 콘솔에 [승인 요청] (y/n)</li>
 * </ul>
 * 서버 없이 {@code --spring.ai.cli.step=ch6-agent}만 주면 로컬 툴만으로 동작한다.
 */
@Component
@ConditionalOnProperty(prefix = "spring.ai.cli", name = "step", havingValue = "ch6-agent")
public class Ch6Step7_BasicAgent implements CommandLineRunner {

    private final BasicSpringAIAgent agent;

    public Ch6Step7_BasicAgent(ChatClient.Builder builder, ObjectProvider<SyncMcpToolCallbackProvider> mcpTools) {
        this.agent = new BasicSpringAIAgent(builder, mcpTools.getIfAvailable());
    }

    @Override
    public void run(String... args) {
        String conversationId = UUID.randomUUID().toString();
        ChatConsole.run("[Ch6] Step7: 스프링 AI 에이전트 (로컬 툴 + MCP 툴 + 사람 승인)", input -> {
            System.out.print(agent.run(input, conversationId));
            System.out.print("\n  [메모리] " + agent.memoryTypes(conversationId));
        });
    }
}
