package kr.jay.springai.ch06cli;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;

/**
 * 테스트용 가짜 모델 — (몇 번째 호출인가, 프롬프트 전체 텍스트)를 보고 응답을 정한다.
 *
 * <p>getOptions()가 ToolCallingChatOptions여야 ToolCallingAdvisor가 루프를 돈다 (4장 ScriptedToolModel과 같은 이유).
 * 병렬 워크플로가 여러 스레드에서 부르므로 기록은 스레드 안전한 리스트에 남긴다.
 */
public class ScriptedModel implements ChatModel {

    public final List<Prompt> prompts = new CopyOnWriteArrayList<>();
    private final BiFunction<Integer, String, AssistantMessage> script;

    public ScriptedModel(BiFunction<Integer, String, AssistantMessage> script) {
        this.script = script;
    }

    public static ScriptedModel replying(BiFunction<Integer, String, String> textScript) {
        return new ScriptedModel((n, prompt) -> new AssistantMessage(textScript.apply(n, prompt)));
    }

    @Override
    public synchronized ChatResponse call(Prompt prompt) {
        prompts.add(prompt);
        String all = prompt.getInstructions().stream()
                .map(m -> m.getText() == null ? "" : m.getText())
                .reduce("", (a, b) -> a + "\n" + b);
        return new ChatResponse(List.of(new Generation(script.apply(prompts.size(), all))));
    }

    @Override
    public ChatOptions getOptions() {
        return ToolCallingChatOptions.builder().build();
    }

    public static AssistantMessage toolCall(String name, String argsJson) {
        return AssistantMessage.builder().content("")
                .toolCalls(List.of(new AssistantMessage.ToolCall("call-" + name, "function", name, argsJson)))
                .build();
    }

    public static AssistantMessage text(String text) {
        return new AssistantMessage(text);
    }
}
