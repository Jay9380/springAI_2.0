package kr.jay.springai.appendix.support;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * 심판(평가 모델)이 실제로 무슨 글자를 돌려줬는지 기록하는 ChatModel 데코레이터.
 *
 * <p>내장 평가기는 심판의 원문을 버리고 통과/실패만 남긴다. 그런데 2.0.1 RelevancyEvaluator의 통과 조건은
 * "앞뒤 공백을 뗀 응답이 정확히 yes"라서, 심판이 "Yes."나 "예"라고 쓰면 내용이 맞아도 실패한다.
 * 원문을 봐야 "답이 나빠서 실패"인지 "형식 때문에 실패"인지 구분할 수 있다.
 */
public class RecordingChatModel implements ChatModel {

    private final ChatModel delegate;
    private final List<String> outputs = new CopyOnWriteArrayList<>();

    public RecordingChatModel(ChatModel delegate) {
        this.delegate = delegate;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        ChatResponse response = delegate.call(prompt);
        outputs.add(response.getResult() == null ? "" : String.valueOf(response.getResult().getOutput().getText()));
        return response;
    }

    @Override
    public ChatOptions getOptions() {
        return delegate.getOptions();
    }

    public String last() {
        return outputs.isEmpty() ? "" : outputs.getLast();
    }
}
