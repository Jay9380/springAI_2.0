package kr.jay.springai.ch04.support;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import kr.jay.springai.ch04.examples.ToolNames;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * [4.5.3 예제 4.39] 할 일 도구 — 툴 호출이 '앱 상태를 바꾸고, 그 상태가 다음 요청에 남는다'는 것을 보여 준다.
 * 모델은 기억하지 않지만 도구 뒤의 앱 상태는 남는다: "할 일 목록 보여줘"에 이전에 추가한 항목이 나온다.
 */
@Component
public class TodoTools {

    private record Todo(int id, String title, boolean done) {
    }

    private final List<Todo> todos = new ArrayList<>();
    private final AtomicInteger seq = new AtomicInteger();

    @Tool(name = ToolNames.TODO_ADD, description = "할 일을 추가하고 부여된 번호를 반환합니다.")
    public synchronized String addTodo(@ToolParam(description = "할 일 내용") String title) {
        Todo t = new Todo(seq.incrementAndGet(), title, false);
        todos.add(t);
        return "할 일 #" + t.id() + " 등록: " + title;
    }

    @Tool(name = ToolNames.TODO_LIST, description = "등록된 할 일 목록과 완료 여부를 반환합니다.")
    public synchronized String listTodos() {
        if (todos.isEmpty()) {
            return "등록된 할 일이 없습니다.";
        }
        StringBuilder sb = new StringBuilder();
        // 상태는 기호([ ], [x])가 아니라 글자로 쓴다. 실측: "#1 [ ] 제목"을 모델이 '완료됨'으로 잘못 읽었다.
        // 도구 결과도 모델이 읽는 프롬프트다 — 오해의 여지가 없는 형식으로 돌려준다.
        todos.forEach(t -> sb.append("#").append(t.id()).append(" (").append(t.done() ? "완료" : "미완료").append(") ")
                .append(t.title()).append('\n'));
        return sb.toString().trim();
    }

    @Tool(name = ToolNames.TODO_COMPLETE, description = "번호로 할 일을 완료 처리합니다.")
    public synchronized String completeTodo(@ToolParam(description = "완료할 할 일 번호") int id) {
        for (int i = 0; i < todos.size(); i++) {
            if (todos.get(i).id() == id) {
                todos.set(i, new Todo(id, todos.get(i).title(), true));
                return "할 일 #" + id + " 완료";
            }
        }
        return "할 일 #" + id + "을 찾을 수 없습니다.";
    }
}
