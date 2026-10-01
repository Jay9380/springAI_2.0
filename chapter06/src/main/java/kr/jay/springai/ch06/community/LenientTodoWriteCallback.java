package kr.jay.springai.ch06.community;

import java.util.Arrays;

import org.springaicommunity.agent.tools.TodoWriteTool;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * [6.5.3 실측 보정] TodoWrite 입력 모양을 너그럽게 받아 주는 어댑터.
 *
 * <p>TodoWriteTool(0.10.0)의 메서드는 {@code todoWrite(Todos todos)}이고 Todos는 다시 {@code todos} 필드를 가진 record다.
 * 그래서 입력 스키마가 <b>{@code {"todos": {"todos": [...]}}}</b>로 두 번 중첩된다. qwen3.5:4b는 매번 실패했다.
 *
 * <p>처음엔 오류 메시지만 보고 모델이 {@code {"todos": [...]}}(한 겹)를 보낸다고 <b>추측</b>해서 그 모양만 고쳤는데 그대로 실패했다.
 * 실제 인자를 찍어 보니 {@code {"todos": "[{...}, ...]"}} — 배열을 <b>문자열로</b> 감싸 보내고 있었다.
 * 교훈: 고치기 전에 실제 페이로드를 본다. 지금은 두 모양(배열, 배열을 담은 문자열)을 모두 받는다.
 *
 * <p>모델에게 보이는 툴 정의(스키마)는 그대로 두고, 들어온 인자가 배열 모양이면 한 겹 감싸서 원래 툴에 넘긴다.
 * 툴 스키마를 바꿀 수 없는 외부 라이브러리 툴을 작은 모델에 맞추는 방법 — 4장의 결과 변환기와 반대 방향(입력 쪽) 보정이다.
 */
public class LenientTodoWriteCallback implements ToolCallback {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final ToolCallback delegate;

    public LenientTodoWriteCallback(TodoWriteTool tool) {
        this.delegate = Arrays.stream(ToolCallbacks.from(tool)).findFirst().orElseThrow();
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    @Override
    public String call(String toolInput) {
        return delegate.call(normalize(toolInput));
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        return delegate.call(normalize(toolInput), toolContext);
    }

    /**
     * {"todos":[...]} 또는 {"todos":"[...]"} → {"todos":{"todos":[...]}}.
     * 이미 올바른 모양이거나 JSON이 아니면 그대로 둔다(원래 툴이 오류를 내게 둔다).
     */
    static String normalize(String toolInput) {
        try {
            JsonNode root = JSON.readTree(toolInput);
            if (root instanceof ObjectNode obj && obj.get("todos") != null) {
                JsonNode todos = obj.get("todos");
                if (todos.isString()) {
                    todos = JSON.readTree(todos.asString());            // 실측 모양: 배열을 문자열로 감싸 보냄
                }
                if (todos.isArray()) {
                    ObjectNode wrapped = JSON.createObjectNode();
                    wrapped.set("todos", todos);
                    obj.set("todos", wrapped);
                    return JSON.writeValueAsString(obj);
                }
            }
        }
        catch (RuntimeException e) {
            // JSON이 아니면 원래 툴이 그대로 오류를 내게 둔다
        }
        return toolInput;
    }
}
