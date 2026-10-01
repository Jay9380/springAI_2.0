package kr.jay.springai.ch06.step;

import java.nio.file.Path;
import java.util.List;

import kr.jay.springai.ch06.advisor.ProbeAdvisor;
import kr.jay.springai.ch06.community.SubagentToolPolicy;
import kr.jay.springai.ch06.support.ChatConsole;
import org.springaicommunity.agent.common.task.subagent.SubagentReference;
import org.springaicommunity.agent.tools.task.TaskTool;
import org.springaicommunity.agent.tools.task.claude.ClaudeSubagentReferences;
import org.springaicommunity.agent.tools.task.claude.ClaudeSubagentType;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * [Step 11 · 6.5.4 예제 6.26~6.27] 하위 에이전트 위임 — TaskTool.
 *
 * <p>실행 (저장소 루트에서): {@code --spring.ai.cli.step=ch6-task}
 * <br>해 볼 말: "chapter06/sample/OrderRepository.java 파일을 code-reviewer 에이전트에게 리뷰시켜줘"
 *
 * <p>하위 에이전트는 {@code agents/*.md}(YAML 프런트매터 + 지시문)로 정의한다. 리드 에이전트는 'Task' 툴 하나로 위임하고,
 * 하위 에이전트는 <b>자기만의 격리된 컨텍스트</b>에서 일한 뒤 핵심 결과만 돌려준다(리드의 컨텍스트가 오염되지 않는다).
 * 하위 에이전트의 기본 툴 세트에는 Task가 없으므로 다시 위임할 수 없다 → 2단계 계층이 구조적으로 보장된다.
 *
 * <p>시작할 때 {@link SubagentToolPolicy}로 정의를 검사해 경고를 출력한다. report-writer.md는 일부러 tools:를 비워 두었다.
 */
@Component
@ConditionalOnProperty(prefix = "spring.ai.cli", name = "step", havingValue = "ch6-task")
public class Ch6Step11_Subagents implements CommandLineRunner {

    private final ChatClient chatClient;
    private final List<String> policyWarnings;
    private final ProbeAdvisor trace = new ProbeAdvisor("trace", BaseAdvisor.HIGHEST_PRECEDENCE + 900);

    public Ch6Step11_Subagents(ChatClient.Builder builder, @Value("${ch6.home:chapter06/src/main/resources}") String home) {
        List<SubagentReference> agents = ClaudeSubagentReferences.fromRootDirectory(Path.of(home, "agents").toString());
        this.policyWarnings = SubagentToolPolicy.violations(agents);
        var taskTool = TaskTool.builder()
                .subagentTypes(ClaudeSubagentType.builder()
                        .chatClientBuilder("default", builder.clone())      // 하위 에이전트를 실행할 ChatClient (model 속성별로 여러 개 가능)
                        .build())
                .subagentReferences(agents)
                .build();
        this.chatClient = builder.clone()
                .defaultSystem("""
                        당신은 리드 에이전트입니다. 전문 작업은 Task 툴로 알맞은 하위 에이전트에게 위임하고,
                        돌려받은 결과를 한국어로 간결하게 정리해 답하세요. 파일 경로는 절대 경로로 전달하세요.
                        현재 작업 디렉터리: %s
                        """.formatted(Path.of("").toAbsolutePath()))
                .defaultToolCallbacks(taskTool)
                .defaultAdvisors(trace)
                .build();
    }

    @Override
    public void run(String... args) {
        System.out.println("── 하위 에이전트 정의 검사");
        if (policyWarnings.isEmpty()) {
            System.out.println("  문제 없음");
        }
        policyWarnings.forEach(w -> System.out.println("  ⚠ " + w));
        ChatConsole.run("[Ch6] Step11: 하위 에이전트 위임 (TaskTool)", input -> {
            trace.reset();
            System.out.print(chatClient.prompt().user(input).call().content());
            System.out.print("\n  [리드의 툴 호출] " + trace.toolCalls());
        });
    }
}
