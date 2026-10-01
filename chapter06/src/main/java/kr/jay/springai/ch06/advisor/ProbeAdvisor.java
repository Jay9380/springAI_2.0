package kr.jay.springai.ch06.advisor;

import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;

/**
 * [6.3.2] 재귀 어드바이저의 '안/밖'을 눈으로 보기 위한 탐침(probe) 어드바이저. 몇 번 실행됐는지만 센다.
 *
 * <p>규칙 하나: ToolCallingAdvisor(order = HIGHEST+300)보다
 * <ul>
 *   <li>order가 <b>작으면</b> 루프 바깥 → 요청당 1번</li>
 *   <li>order가 <b>크면</b> 루프 안 → 모델 호출마다(툴 왕복마다) 실행</li>
 * </ul>
 * 루프 어드바이저가 {@code chain.copy(this)}로 '자기 뒤쪽'만 복제해 다시 부르기 때문이다.
 */
public class ProbeAdvisor implements CallAdvisor {

    private final String name;
    private final int order;
    private final AtomicInteger calls = new AtomicInteger();

    public ProbeAdvisor(String name, int order) {
        this.name = name;
        this.order = order;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        calls.incrementAndGet();
        return chain.nextCall(request);
    }

    public int calls() {
        return calls.get();
    }

    public void reset() {
        calls.set(0);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public int getOrder() {
        return order;
    }
}
