package kr.jay.springai.ch06;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;

import kr.jay.springai.ch06.community.ConsoleQuestionHandler;
import kr.jay.springai.ch06.community.LenientTodoWriteCallback;
import kr.jay.springai.ch06.community.SubagentToolPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springaicommunity.agent.tools.AskUserQuestionTool.Question;
import org.springaicommunity.agent.tools.AskUserQuestionTool.Question.Option;
import org.springaicommunity.agent.tools.FileSystemTools;
import org.springaicommunity.agent.tools.SkillsTool;
import org.springaicommunity.agent.tools.TodoWriteTool;
import org.springaicommunity.agent.tools.TodoWriteTool.Todos;
import org.springaicommunity.agent.tools.TodoWriteTool.Todos.TodoItem;
import org.springaicommunity.agent.tools.TodoWriteTool.Todos.Status;
import org.springaicommunity.agent.tools.task.claude.ClaudeSubagentReferences;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;

/** 6.5 커뮤니티 툴: 스킬의 점진적 공개, 읽기 전용·허용 디렉터리, 질문 핸들러, TodoWrite 제약, 하위 에이전트 권한 검사. */
class CommunityToolsTest {

    static final Path RESOURCES = Path.of("src/main/resources").toAbsolutePath();   // 테스트는 모듈 디렉터리에서 돈다

    @Test
    void skills_descriptionShowsOnlyNames_bodyComesAsToolResult() {
        ToolCallback skill = SkillsTool.builder().addSkillsDirectory(RESOURCES.resolve("skills").toString()).build();

        String description = skill.getToolDefinition().description();
        assertThat(description).contains("restock-policy", "code-reviewer", "재발주");      // 이름·설명은 항상
        assertThat(description).doesNotContain("안전재고 − 현재 재고");                      // 본문은 아직 아님

        String body = skill.call("{\"command\":\"restock-policy\"}");
        assertThat(body).contains("안전재고 − 현재 재고").contains("reference/safety-stock.md");
        // 함수 툴의 String 결과는 JSON 문자열로 직렬화되어 따옴표가 붙는다 (4장 returnDirect에서 본 것과 같은 현상)
        assertThat(skill.call("{\"command\":\"no-such-skill\"}")).contains("Skill not found");
    }

    @Test
    void fileSystemTools_registerWriteAndEditToo_soFilterToRead() {
        List<String> names = Arrays.stream(ToolCallbacks.from(FileSystemTools.builder().build()))
                .map(t -> t.getToolDefinition().name()).toList();
        assertThat(names).contains("Read", "Write", "Edit");                              // 그대로 등록하면 쓰기까지 노출된다
    }

    @Test
    void read_isConfinedToAllowedDirectories() {
        Path skills = RESOURCES.resolve("skills");
        ToolCallback read = Arrays.stream(ToolCallbacks.from(FileSystemTools.builder().allowedDirectory(skills).build()))
                .filter(t -> t.getToolDefinition().name().equals("Read")).findFirst().orElseThrow();

        assertThat(read.call(json(skills.resolve("restock-policy/reference/safety-stock.md")))).contains("SKU-200");
        assertThat(read.call(json(RESOURCES.resolve("project/secrets/db.properties")))).contains("Access denied");
        assertThat(read.call(json(skills.resolve("../project/secrets/db.properties")))).contains("Access denied");
    }

    static String json(Path p) {
        return "{\"filePath\":\"" + p.toString().replace("\\", "\\\\") + "\"}";
    }

    static final Question TRIP = new Question("어떤 여행을 원하세요?", "여행 유형",
            List.of(new Option("도시", "박물관·미식"), new Option("자연", "알프스·해안"), new Option("휴양", "지중해 해변")), true);

    static String answer(String line) {
        var q = new ArrayDeque<String>();
        if (line != null) {
            q.add(line);
        }
        return new ConsoleQuestionHandler(q::poll).handle(List.of(TRIP)).get(TRIP.question());
    }

    @Test
    void questionHandler_mapsNumbersToLabels_andKeepsFreeText() {
        assertThat(answer("2")).isEqualTo("자연");
        assertThat(answer("1, 3")).isEqualTo("도시, 휴양");
        assertThat(answer("동유럽 소도시")).isEqualTo("동유럽 소도시");
        assertThat(answer("9")).isEqualTo("9");                                           // 범위 밖 번호는 직접 입력으로
        assertThat(answer(null)).isEqualTo("");                                           // 입력 끊김: null 대신 빈 값 (라이브러리 검증 통과)
    }

    @Test
    void todoWrite_rejectsTwoInProgress_inCodeNotJustPrompt() {
        var tool = TodoWriteTool.builder().build();
        assertThatThrownBy(() -> tool.todoWrite(new Todos(List.of(
                new TodoItem("재고 조회", Status.in_progress, "재고 조회 중"),
                new TodoItem("요약", Status.in_progress, "요약 중")))))
                .hasMessageContaining("Only ONE task can be in_progress");
    }

    @Test
    void lenientTodoWrite_acceptsFlatShapeThatSmallModelsSend() {
        List<String> seen = new java.util.ArrayList<>();
        var todo = new LenientTodoWriteCallback(TodoWriteTool.builder()
                .todoEventHandler(t -> t.todos().forEach(i -> seen.add(i.status() + ":" + i.content()))).build());
        // 실측에서 qwen3.5:4b가 보낸 모양: 배열을 '문자열'로 감쌌다
        String stringified = "{\"todos\":\"[{\\\"activeForm\\\": \\\"조회 중\\\", \\\"content\\\": \\\"재고 조회\\\", \\\"status\\\": \\\"in_progress\\\"}]\"}";
        String flat = "{\"todos\":[{\"content\":\"검토\",\"status\":\"pending\",\"activeForm\":\"검토 중\"}]}";
        String nested = "{\"todos\":{\"todos\":[{\"content\":\"요약\",\"status\":\"pending\",\"activeForm\":\"요약 중\"}]}}";

        todo.call(stringified);
        todo.call(flat);
        todo.call(nested);                                                 // 스키마대로의 모양
        assertThat(seen).containsExactly("in_progress:재고 조회", "pending:검토", "pending:요약");
        // 보정 없이 원래 툴에 실측 모양을 주면 실패한다 (실행 로그와 같은 오류)
        var raw = Arrays.stream(ToolCallbacks.from(TodoWriteTool.builder().build())).findFirst().orElseThrow();
        assertThatThrownBy(() -> raw.call(stringified)).hasStackTraceContaining("from Array value");
    }

    @Test
    void subagentPolicy_flagsMissingTools_inShippedDefinitions() {
        List<String> problems = SubagentToolPolicy.violations(
                ClaudeSubagentReferences.fromRootDirectory(RESOURCES.resolve("agents").toString()));
        assertThat(problems).singleElement().asString().startsWith("report-writer: tools 미지정");
    }

    @Test
    void subagentPolicy_flagsBookExampleToolNames(@TempDir Path dir) throws Exception {
        // 책 예제 6.26의 프런트매터 그대로: 실제 툴 이름은 Read이고 ListDirectory는 기본 세트에 없다
        Files.writeString(dir.resolve("code-reviewer.md"), """
                ---
                name: code-reviewer
                description: 리뷰
                tools: ReadFile, Grep, ListDirectory
                ---
                리뷰하세요.
                """);
        List<String> problems = SubagentToolPolicy.violations(ClaudeSubagentReferences.fromRootDirectory(dir.toString()));
        assertThat(problems).hasSize(2).anyMatch(p -> p.contains("'ReadFile'")).anyMatch(p -> p.contains("'ListDirectory'"));
    }
}
