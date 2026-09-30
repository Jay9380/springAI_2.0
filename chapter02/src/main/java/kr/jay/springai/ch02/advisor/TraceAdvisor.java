package kr.jay.springai.ch02.advisor;

import java.util.List;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;

/**
 * [2.8.2] 실행 순서를 눈으로 보기 위한 학습용 어드바이저.
 * 요청이 지나갈 때 "→ 이름", 응답이 돌아올 때 "← 이름"을 기록한다.
 *
 * <p>order가 다른 두 개를 걸면 기록이 "→A, →B, ←B, ←A"가 된다 — 양파 껍질(스택) 구조.
 */
public class TraceAdvisor implements CallAdvisor {

    private final String name;
    private final int order;
    private final List<String> trace;

    public TraceAdvisor(String name, int order, List<String> trace) {
        this.name = name;
        this.order = order;
        this.trace = trace;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        trace.add("→ " + name + "(" + order + ")");
        ChatClientResponse response = chain.nextCall(request);
        trace.add("← " + name + "(" + order + ")");
        return response;
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
