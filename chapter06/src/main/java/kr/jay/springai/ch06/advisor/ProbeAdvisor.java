package kr.jay.springai.ch06.advisor;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;

/**
 * [6.3.2] 재귀 어드바이저의 '안/밖'을 눈으로 보기 위한 탐침(probe) 어드바이저. 몇 번 실행됐는지 세고, 모델이 요청한 툴 호출을 기록한다.
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
    private final List<String> toolCalls = new CopyOnWriteArrayList<>();

    public ProbeAdvisor(String name, int order) {
        this.name = name;
        this.order = order;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        calls.incrementAndGet();
        ChatClientResponse response = chain.nextCall(request);
        // 루프 안에 두면 모델이 매 반복 어떤 툴을 어떤 인자로 요청했는지도 보인다
        if (response.chatResponse() != null && response.chatResponse().getResult() != null) {
            response.chatResponse().getResult().getOutput().getToolCalls()
                    .forEach(tc -> toolCalls.add(tc.name() + tc.arguments()));
        }
        return response;
    }

    public int calls() {
        return calls.get();
    }

    public List<String> toolCalls() {
        return toolCalls;
    }

    public void reset() {
        calls.set(0);
        toolCalls.clear();
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
