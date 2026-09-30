package kr.jay.springai.ch02.advisor;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import reactor.core.publisher.Flux;

/**
 * [Step 5 · 예제 2.80] 응답 시간을 재는 커스텀 어드바이저.
 *
 * <p>어드바이저의 기본 모양: "앞에서 할 일 → chain.next...(request) → 뒤에서 할 일".
 * chain.nextCall()이 다음 어드바이저(마지막엔 실제 모델)를 부르는 지점이다.
 * 이 호출을 감싸는 앞뒤가 곧 AOP의 @Around와 같다.
 *
 * <p>CallAdvisor와 StreamAdvisor를 둘 다 구현한다(2.8.6 원칙 3 '이중 구현').
 * 사용하는 쪽이 call()을 쓰든 stream()을 쓰든 똑같이 동작하게 하기 위해서다.
 */
public class ElapsedTimeAdvisor implements CallAdvisor, StreamAdvisor {

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        long start = System.currentTimeMillis();                  // [앞] 시작 시각
        ChatClientResponse response = chain.nextCall(request);    // 다음 단계(→ 모델) 호출
        System.out.printf("%n[응답 시간] %dms%n", System.currentTimeMillis() - start);   // [뒤]
        return response;
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        long start = System.currentTimeMillis();
        return chain.nextStream(request)
                // 스트림은 조각이 여러 번 온다. '마지막 조각이 도착한 순간'(완료 신호)에 시간을 찍는다.
                .doOnComplete(() -> System.out.printf("%n[응답 시간] %dms%n", System.currentTimeMillis() - start));
    }

    @Override
    public String getName() {
        return "ElapsedTimeAdvisor";
    }

    /**
     * order 100: 기본(0)보다 안쪽(모델에 가까운 쪽).
     * 로깅(SimpleLoggerAdvisor, 기본 0) 시간은 빼고 '모델 호출' 시간에 가깝게 재기 위해.
     */
    @Override
    public int getOrder() {
        return 100;
    }
}
