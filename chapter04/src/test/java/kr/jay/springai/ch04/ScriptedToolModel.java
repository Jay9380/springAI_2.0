package kr.jay.springai.ch04;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;

/**
 * 테스트용 가짜 모델 — 호출 순서대로 미리 정해 둔 응답(텍스트 또는 도구 호출 요청)을 돌려준다.
 *
 * <p>중요: getOptions()가 {@link ToolCallingChatOptions}를 돌려줘야 한다.
 * ToolCallingAdvisor는 요청 옵션이 ToolCallingChatOptions가 아니면 "도구를 못 쓰는 모델"로 보고 루프를 건너뛴다
 * (2.0.1 ToolCallingAdvisor.adviseCall 첫 부분). 실제 OllamaChatOptions는 이 인터페이스를 구현한다.
 * 기본 ChatOptions만 주는 람다 가짜 모델로는 도구 루프가 돌지 않는다 — 처음 테스트가 실패했던 이유.
 *
 * <p>또 하나: 2.0에서 이 메서드 이름이 getDefaultOptions() → getOptions()로 바뀌었다(옛 이름은 deprecated).
 * ChatClient는 getOptions()만 읽으므로, 옛 이름을 재정의하면 조용히 무시된다 — 두 번째로 실패했던 이유.
 */
class ScriptedToolModel implements ChatModel {

    final List<Prompt> prompts = new ArrayList<>();
    private final Function<Integer, AssistantMessage> script;

    ScriptedToolModel(Function<Integer, AssistantMessage> script) {
        this.script = script;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        prompts.add(prompt);
        return new ChatResponse(List.of(new Generation(script.apply(prompts.size()))));
    }

    @Override
    public ChatOptions getOptions() {
        return ToolCallingChatOptions.builder().build();
    }

    static AssistantMessage toolCall(String name, String argsJson) {
        return AssistantMessage.builder().content("")
                .toolCalls(List.of(new AssistantMessage.ToolCall("call-" + name, "function", name, argsJson)))
                .build();
    }

    static AssistantMessage text(String text) {
        return new AssistantMessage(text);
    }
}
