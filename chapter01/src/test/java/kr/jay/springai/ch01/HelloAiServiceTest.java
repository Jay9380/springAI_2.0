package kr.jay.springai.ch01;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * 모델 없이 도는 단위 테스트.
 *
 * <p>요령: ChatModel은 추상 메서드가 call(Prompt) 하나뿐인 인터페이스다.
 * 그래서 람다 하나로 '가짜 모델'을 만들 수 있다. 가짜 모델은
 * (1) 스프링 AI가 실제로 어떤 Prompt를 만들어 보내는지 기록하고,
 * (2) 우리가 정한 응답을 돌려준다.
 * ChatClient가 '결국 ChatModel.call(Prompt)을 부르는 편의 API'라는 것을 이 테스트가 보여 준다.
 */
class HelloAiServiceTest {

    /** 가짜 모델이 받은 Prompt들을 순서대로 기록한다. */
    private final List<Prompt> received = new ArrayList<>();

    private ChatModel fakeModel(String reply, String finishReason) {
        return prompt -> {
            received.add(prompt);
            var generation = new Generation(new AssistantMessage(reply),
                    ChatGenerationMetadata.builder().finishReason(finishReason).build());
            var metadata = ChatResponseMetadata.builder()
                    .model("fake-model")
                    .usage(new DefaultUsage(12, 34))
                    .build();
            return new ChatResponse(List.of(generation), metadata);
        };
    }

    @Test
    void ask_sendsQuestionAsUserMessage_andReturnsText() {
        var service = new HelloAiService(ChatClient.builder(fakeModel("안녕하세요", "stop")));

        String answer = service.ask("스프링 AI가 뭐야?");

        assertThat(answer).isEqualTo("안녕하세요");
        // 스프링 AI가 만든 Prompt 안에는 role=user 메시지 하나가 들어 있다
        List<Message> sent = received.get(0).getInstructions();
        assertThat(sent).hasSize(1);
        assertThat(sent.get(0).getMessageType().getValue()).isEqualTo("user");
        assertThat(sent.get(0).getText()).isEqualTo("스프링 AI가 뭐야?");
    }

    @Test
    void askWithMetadata_exposesTokensAndFinishReason() {
        var service = new HelloAiService(ChatClient.builder(fakeModel("KPI는 ...", "stop")));

        var answer = service.askWithMetadata("KPI가 뭐야?");

        assertThat(answer.text()).isEqualTo("KPI는 ...");
        assertThat(answer.model()).isEqualTo("fake-model");
        assertThat(answer.promptTokens()).isEqualTo(12);
        assertThat(answer.completionTokens()).isEqualTo(34);
        assertThat(answer.finishReason()).isEqualTo("stop");
    }

    @Test
    void askWithMetadata_revealsTruncatedAnswer() {
        // 실패 경로: 최대 출력 길이에 걸려 잘린 답은 finishReason으로만 알 수 있다.
        // content()만 쓰면 잘렸는지 모른다 → 중요한 곳에서는 메타데이터를 확인해야 한다.
        var service = new HelloAiService(ChatClient.builder(fakeModel("이번 주 KPI는 전체 57건 중", "length")));

        var answer = service.askWithMetadata("이번 주 KPI 요약해줘");

        assertThat(answer.finishReason()).isEqualTo("length");
    }

    @Test
    void twoCalls_areIndependent_modelGetsNoHistory() {
        // LLM은 기억하지 않는다: 두 번째 요청에는 첫 번째 대화가 전혀 실리지 않는다.
        var service = new HelloAiService(ChatClient.builder(fakeModel("알겠어", "stop")));

        service.ask("내 이름은 제이야.");
        service.ask("내 이름이 뭐지?");

        List<Message> secondRequest = received.get(1).getInstructions();
        assertThat(secondRequest).hasSize(1);
        assertThat(secondRequest.get(0).getText()).doesNotContain("제이");
    }
}
