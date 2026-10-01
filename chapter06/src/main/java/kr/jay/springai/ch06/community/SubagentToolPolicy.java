package kr.jay.springai.ch06.community;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springaicommunity.agent.common.task.subagent.SubagentReference;
import org.springaicommunity.agent.tools.task.claude.ClaudeSubagentDefinition;
import org.springaicommunity.agent.tools.task.claude.ClaudeSubagentResolver;

/**
 * [6.5.4] 하위 에이전트 정의의 권한 검사 — 시작할 때 한 번 돈다.
 *
 * <p>agent-utils 0.10.0 소스로 확인한 사실(6장-3 노트 2.1):
 * <ul>
 *   <li>하위 에이전트의 기본 툴 세트 = TodoWrite, Grep, Glob, <b>Bash</b>, Read, <b>Write</b>, <b>Edit</b>, WebFetch.
 *       FileSystemTools·ShellTools 모두 디렉터리 제한 없이 만들어진다.</li>
 *   <li>프런트매터에 {@code tools:}가 없으면 그 기본 세트 <b>전부</b>를 받는다 → 보고서 작성 에이전트가 셸을 실행할 수 있다.</li>
 *   <li>{@code tools:}는 이름 <b>정확 일치</b>로 거른다. 책 예제 6.26의 {@code ReadFile, ListDirectory}는 실제 이름(Read)과 달라
 *       조용히 빠진다 → 리뷰 에이전트가 파일을 못 읽는다.</li>
 * </ul>
 * 최소 권한이 기본값이 아니므로, 모든 정의에 {@code tools:}가 있는지와 이름이 실제 툴과 맞는지를 코드로 확인한다.
 */
public final class SubagentToolPolicy {

    /** ClaudeSubagentType이 만드는 기본 툴 이름 (0.10.0, Brave 키 없음). */
    public static final Set<String> KNOWN_TOOLS = Set.of("TodoWrite", "Grep", "Glob", "Bash", "Read", "Write", "Edit", "WebFetch");

    /** 위험한 툴: 쓰면 안 된다는 게 아니라, 쓴다면 의도적으로 명시해야 한다. */
    public static final Set<String> DANGEROUS_TOOLS = Set.of("Bash", "Write", "Edit");

    private SubagentToolPolicy() {
    }

    public static List<String> violations(List<SubagentReference> references) {
        ClaudeSubagentResolver resolver = new ClaudeSubagentResolver();
        List<String> problems = new ArrayList<>();
        for (SubagentReference ref : references) {
            var def = (ClaudeSubagentDefinition) resolver.resolve(ref);
            List<String> tools = def.tools();
            if (tools == null || tools.isEmpty()) {
                problems.add(def.getName() + ": tools 미지정 → 기본 툴 전부(" + DANGEROUS_TOOLS + " 포함)를 받는다");
                continue;
            }
            for (String tool : tools) {
                if (!KNOWN_TOOLS.contains(tool)) {
                    problems.add(def.getName() + ": 알 수 없는 툴 이름 '" + tool + "' → 조용히 빠진다 (실제 이름: " + KNOWN_TOOLS + ")");
                }
            }
        }
        return problems;
    }
}
