package kr.jay.springai.ch06cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import kr.jay.springai.ch06cli.orchestration.meta.LenientTodoWriteCallback;
import org.junit.jupiter.api.Test;
import org.springaicommunity.agent.tools.TodoWriteTool;

/** 실측에서 qwen3.5:4b가 보낸 TodoWrite 인자 모양들 — 모두 같은 계획으로 해석돼야 한다. */
class TodoShapeTest {

    @Test
    void acceptsEveryShapeTheModelActuallySent() {
        List<String> seen = new ArrayList<>();
        var todo = new LenientTodoWriteCallback(TodoWriteTool.builder()
                .todoEventHandler(t -> t.todos().forEach(i -> seen.add(i.content()))).build());
        String item = "{\"content\":\"재고 조회\",\"status\":\"in_progress\",\"activeForm\":\"조회 중\"}";
        String escaped = item.replace("\"", "\\\"");

        todo.call("{\"todos\":{\"todos\":[" + item + "]}}");                      // 스키마대로
        todo.call("{\"todos\":\"[" + escaped + "]\"}");                           // 6.5 실측: 배열을 문자열로
        // 6.6 실측: 문자열 안에 {"todos": [...]}를 한 겹 더 감싼 배열 (실행 로그의 모양 그대로)
        todo.call("""
                {"todos":"[{\\"todos\\": [{\\"content\\":\\"재고 조회\\",\\"status\\":\\"in_progress\\",\\"activeForm\\":\\"조회 중\\"}]}]"}""");
        assertThat(seen).containsExactly("재고 조회", "재고 조회", "재고 조회");
    }
}
