package kr.jay.springai.ch02.advisor;

import java.util.List;
import java.util.Set;

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
 * [2.8.5 예제 2.67] 요청 차단 — 금칙어가 들어오면 모델을 부르지 않고 바로 차단 응답.
 *
 * <p>어드바이저는 '통과만 시키는 파이프'가 아니다. chain.nextCall()을 <b>부르지 않으면</b>
 * 그 뒤의 어드바이저와 모델은 아예 실행되지 않는다. 유해 요청을 막고 비용(토큰)도 아낀다.
 *
 * <p>order -100: 가장 바깥. 보안 검사는 RAG 검색이나 로깅보다 먼저 해야 한다(2.8.6 전략 1).
 */
public class ContentSafetyAdvisor implements CallAdvisor, StreamAdvisor {

    static final String BLOCKED = "부적절한 내용이 감지되어 차단되었습니다.";

    private final Set<String> forbiddenWords;

    public ContentSafetyAdvisor(Set<String> forbiddenWords) {
        this.forbiddenWords = forbiddenWords;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        // [요청 검사] 사용자 입력에 금칙어가 있으면 → 모델 호출 없이 즉시 반환
        if (isUnsafe(request.prompt().getUserMessage().getText())) {
            return blocked();
        }
        // [응답 검사] 모델이 만든 답에 금칙어가 있으면 → 답을 버리고 차단 응답
        ChatClientResponse response = chain.nextCall(request);
        if (isUnsafe(textOf(response))) {
            return blocked();
        }
        return response;
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        if (isUnsafe(request.prompt().getUserMessage().getText())) {
            return Flux.just(blocked());
        }
        // 스트림은 조각 단위로 오므로, 지금까지 온 글을 누적해서 검사한다.
        // Flux.defer: 구독할 때마다 새 StringBuilder를 만들어 요청끼리 상태가 섞이지 않게 한다.
        return Flux.defer(() -> {
            StringBuilder soFar = new StringBuilder();
            return chain.nextStream(request)
                    .map(chunk -> {
                        String piece = textOf(chunk);
                        if (piece != null) {
                            soFar.append(piece);
                        }
                        if (isUnsafe(soFar.toString())) {
                            throw new UnsafeContentException();       // 발견 즉시 스트림 중단
                        }
                        return chunk;
                    })
                    // 중단 신호를 '차단 응답 하나'로 바꿔 정상 종료. 이미 출력된 앞부분은 되돌릴 수 없다는 한계가 있다.
                    .onErrorResume(UnsafeContentException.class, e -> Flux.just(blocked()));
        });
    }

    private boolean isUnsafe(String text) {
        return text != null && forbiddenWords.stream().anyMatch(text::contains);
    }

    static String textOf(ChatClientResponse response) {
        if (response == null || response.chatResponse() == null || response.chatResponse().getResult() == null) {
            return null;
        }
        return response.chatResponse().getResult().getOutput().getText();
    }

    private static ChatClientResponse blocked() {
        ChatResponse chatResponse = new ChatResponse(List.of(new Generation(new AssistantMessage(BLOCKED))));
        return ChatClientResponse.builder().chatResponse(chatResponse).build();
    }

    private static final class UnsafeContentException extends RuntimeException {
    }

    @Override
    public String getName() {
        return "ContentSafetyAdvisor";
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
