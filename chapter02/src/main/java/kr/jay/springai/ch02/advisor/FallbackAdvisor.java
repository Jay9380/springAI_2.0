package kr.jay.springai.ch02.advisor;

import java.util.List;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import reactor.core.publisher.Flux;

/**
 * [2.8.5 예제 2.68] 응답 대체 — 모델이 빈 답을 주면 준비된 안내 문구로 바꾼다.
 *
 * <p>차단(ContentSafetyAdvisor)이 '요청 쪽'을 막는다면, 대체는 '응답 쪽'을 고친다.
 * 사용자는 빈 화면 대신 최소한의 안내를 받는다.
 */
public class FallbackAdvisor implements CallAdvisor, StreamAdvisor {

    static final String FALLBACK = "죄송합니다. 현재 적절한 답변을 생성할 수 없습니다.";

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        ChatClientResponse response = chain.nextCall(request);
        return isEmpty(response) ? fallback() : response;
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        // 스트림이 아무것도 내보내지 않고 끝나면 대체 응답 하나를 내보낸다
        return chain.nextStream(request).switchIfEmpty(Flux.defer(() -> Flux.just(fallback())));
    }

    static boolean isEmpty(ChatClientResponse response) {
        String text = ContentSafetyAdvisor.textOf(response);
        return text == null || text.isBlank();
    }

    private static ChatClientResponse fallback() {
        return ChatClientResponse.builder()
                .chatResponse(new ChatResponse(List.of(new Generation(new AssistantMessage(FALLBACK)))))
                .build();
    }

    @Override
    public String getName() {
        return "FallbackAdvisor";
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
