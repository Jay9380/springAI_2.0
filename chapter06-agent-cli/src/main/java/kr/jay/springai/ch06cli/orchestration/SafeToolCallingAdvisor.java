package kr.jay.springai.ch06cli.orchestration;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.model.tool.ToolCallingManager;

/**
 * [6.6.3] 툴이 적을 때(임계값 미만) 쓰는 기본 툴 루프 + 라운드 상한.
 * 스프링 AI 기본 동작 그대로: 루프 안의 툴 기록은 이 어드바이저가 관리하고(내부 기록 켬), 메모리는 루프 바깥(+200)에서 1번.
 */
public class SafeToolCallingAdvisor extends ToolCallingAdvisor {

    private final int maxRounds;

    public SafeToolCallingAdvisor(int maxRounds) {
        super(ToolCallingManager.builder().build(), DEFAULT_TOOL_EXECUTION_ELIGIBILITY_CHECKER,
                BaseAdvisor.HIGHEST_PRECEDENCE + 300, true);
        this.maxRounds = maxRounds;
    }

    @Override
    protected ChatClientRequest doInitializeLoop(ChatClientRequest request, CallAdvisorChain chain) {
        return AgentSafety.init(super.doInitializeLoop(request, chain));
    }

    @Override
    protected ChatClientResponse doAfterCall(ChatClientResponse response, CallAdvisorChain chain) {
        return AgentSafety.limit(super.doAfterCall(response, chain), maxRounds);
    }
}
