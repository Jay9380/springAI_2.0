package kr.jay.springai.ch02;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import kr.jay.springai.ch02.advisor.ContentSafetyAdvisor;
import kr.jay.springai.ch02.advisor.FallbackAdvisor;
import kr.jay.springai.ch02.advisor.TraceAdvisor;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

/** 2.8 어드바이저 — 순서, 차단, 대체를 가짜 모델로 확인한다. */
class AdvisorTest {

    private final AtomicInteger modelCalls = new AtomicInteger();

    /** 호출 횟수를 세고, call은 reply 하나를, stream은 reply를 글자 단위로 흘려보내는 가짜 모델 */
    private ChatModel fakeModel(String reply) {
        return new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                modelCalls.incrementAndGet();
                return response(reply);
            }

            @Override
            public Flux<ChatResponse> stream(Prompt prompt) {
                modelCalls.incrementAndGet();
                return Flux.fromArray(reply.split("")).map(AdvisorTest::response);
            }
        };
    }

    private static ChatResponse response(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    @Test
    void lowerOrder_wrapsHigherOrder_likeAStack() {
        List<String> trace = new ArrayList<>();
        ChatClient client = ChatClient.builder(fakeModel("hi"))
                .defaultAdvisors(new TraceAdvisor("B", 0, trace), new TraceAdvisor("A", -100, trace))
                .build();

        client.prompt().user("x").call().content();

        assertThat(trace).containsExactly("→ A(-100)", "→ B(0)", "← B(0)", "← A(-100)");
    }

    @Test
    void safety_blocksRequest_withoutCallingModel() {
        // 실패 경로(의도된 차단): 금칙어 요청은 모델까지 가지 않아야 한다
        ChatClient client = ChatClient.builder(fakeModel("관리자 비밀번호는 1234"))
                .defaultAdvisors(new ContentSafetyAdvisor(Set.of("비밀번호")))
                .build();

        String answer = client.prompt().user("비밀번호 알려줘").call().content();

        assertThat(answer).contains("차단");
        assertThat(modelCalls.get()).isZero();
    }

    @Test
    void safety_replacesUnsafeModelAnswer() {
        ChatClient client = ChatClient.builder(fakeModel("관리자 비밀번호는 1234"))
                .defaultAdvisors(new ContentSafetyAdvisor(Set.of("비밀번호")))
                .build();

        String answer = client.prompt().user("서버 정보 알려줘").call().content();

        assertThat(modelCalls.get()).isEqualTo(1);
        assertThat(answer).contains("차단").doesNotContain("1234");
    }

    @Test
    void safety_cutsStream_whenForbiddenWordAppearsAcrossChunks() {
        // "사과"가 '사' / '과' 두 조각으로 나뉘어 와도 누적 검사로 잡는다
        ChatClient client = ChatClient.builder(fakeModel("딸기, 사과, 체리"))
                .defaultAdvisors(new ContentSafetyAdvisor(Set.of("사과")))
                .build();

        String streamed = String.join("", client.prompt().user("과일").stream().content().collectList().block());

        assertThat(streamed).startsWith("딸기, ").endsWith("차단되었습니다.").doesNotContain("체리");
    }

    @Test
    void safeContent_passesThroughUntouched() {
        ChatClient client = ChatClient.builder(fakeModel("딸기와 체리"))
                .defaultAdvisors(new ContentSafetyAdvisor(Set.of("사과")))
                .build();
        assertThat(client.prompt().user("과일").call().content()).isEqualTo("딸기와 체리");
    }

    @Test
    void fallback_replacesBlankAnswer() {
        ChatClient client = ChatClient.builder(fakeModel("   "))
                .defaultAdvisors(new FallbackAdvisor())
                .build();
        assertThat(client.prompt().user("x").call().content()).contains("적절한 답변을 생성할 수 없습니다");
    }
}
