package kr.jay.springai.ch06.step;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import kr.jay.springai.ch06.advisor.ProbeAdvisor;
import kr.jay.springai.ch06.advisor.WarehouseTools;
import kr.jay.springai.ch06.support.ChatConsole;
import org.springaicommunity.agent.tools.FileSystemTools;
import org.springaicommunity.agent.tools.SkillsTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * [Step 9 · 6.5.1 예제 6.22~6.23] 범용 에이전트 스킬 — 능력을 코드가 아니라 SKILL.md로.
 *
 * <p>실행 (저장소 루트에서): {@code --spring.ai.cli.step=ch6-skills}
 * <br>해 볼 말: "SKU-200 재발주 수량을 정해줘" (스킬 본문 → 참고 파일 Read → 재고 툴 → 계산),
 * "chapter06/sample/OrderRepository.java 코드 리뷰해줘"
 *
 * <p>점진적 공개: 매 요청 모델이 보는 건 'Skill' 툴 하나와 그 설명 안의 스킬 이름·한 줄 설명뿐이다.
 * 스킬 본문은 {@code Skill("restock-policy")}를 호출해야 툴 결과로 들어오고, 참고 파일은 Read로 필요할 때만 읽는다.
 *
 * <p><b>책과 다르게 한 것 (책 p.586의 운영 주의를 코드로):</b>
 * <ul>
 *   <li>ShellTools를 등록하지 않는다 — 모델이 임의 셸 명령을 실행할 수 있게 되는 것 자체가 위험이다.</li>
 *   <li>FileSystemTools에서 <b>Read만</b> 꺼내 쓴다 — 그대로 등록하면 Write·Edit까지 노출된다.</li>
 *   <li>읽기 허용 디렉터리를 스킬 폴더와 예제 폴더로 제한한다(allowedDirectory, 심볼릭 링크까지 검사).</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(prefix = "spring.ai.cli", name = "step", havingValue = "ch6-skills")
public class Ch6Step9_Skills implements CommandLineRunner {

    private final ChatClient chatClient;
    private final ProbeAdvisor trace = new ProbeAdvisor("trace", BaseAdvisor.HIGHEST_PRECEDENCE + 900);

    public Ch6Step9_Skills(ChatClient.Builder builder,
                           @Value("${ch6.home:chapter06/src/main/resources}") String home,
                           @Value("${ch6.sample:chapter06/sample}") String sample) {
        Path skills = Path.of(home, "skills").toAbsolutePath().normalize();
        Path samples = Path.of(sample).toAbsolutePath().normalize();
        ToolCallback skillTool = SkillsTool.builder().addSkillsDirectory(skills.toString()).build();
        List<ToolCallback> readOnly = Arrays.stream(ToolCallbacks.from(
                        FileSystemTools.builder().allowedDirectory(skills).allowedDirectory(samples).build()))
                .filter(t -> t.getToolDefinition().name().equals("Read"))          // Write·Edit은 노출하지 않는다
                .toList();
        this.chatClient = builder.clone()
                .defaultSystem("""
                        당신은 다양한 업무 스킬을 활용할 수 있는 에이전트입니다.
                        요청에 맞는 스킬이 있으면 먼저 Skill 툴로 절차를 불러와 그대로 따르세요.
                        스킬 이름은 툴 이름이 아닙니다. 반드시 Skill 툴을 부르고 command 인자에 스킬 이름을 넣으세요.
                        예: Skill(command="restock-policy")
                        파일 경로는 절대 경로를 써야 합니다. 스킬 폴더: %s, 예제 코드 폴더: %s
                        """.formatted(skills, samples))
                .defaultToolCallbacks(skillTool)
                .defaultToolCallbacks(readOnly)
                .defaultTools(new WarehouseTools())
                .defaultAdvisors(trace)
                .build();
    }

    @Override
    public void run(String... args) {
        ChatConsole.run("[Ch6] Step9: 범용 에이전트 스킬 (SKILL.md)", input -> {
            trace.reset();
            try {
                System.out.print(chatClient.prompt().user(input).call().content());
            }
            catch (RuntimeException e) {
                // 모델이 없는 툴 이름을 부르면 ToolCallingManager가 예외를 던진다. 대화 한 턴만 실패시키고 세션은 살린다
                System.out.print("(이번 요청 실패: " + e.getMessage() + ")");
            }
            System.out.print("\n  [툴 호출] " + trace.toolCalls());
        });
    }
}
