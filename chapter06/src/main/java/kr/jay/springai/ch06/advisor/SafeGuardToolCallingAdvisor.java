package kr.jay.springai.ch06.advisor;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;

/**
 * [6.3.4 표 6.8] ToolCallingAdvisor의 훅 5개로 만든 안전 가드.
 *
 * <table>
 *   <tr><th>훅</th><th>시점</th><th>여기서 하는 일</th></tr>
 *   <tr><td>doInitializeLoop</td><td>루프 진입 (1회)</td><td>이번 요청의 상태(반복 수·토큰·시작 시각) 생성</td></tr>
 *   <tr><td>doBeforeCall</td><td>모델 호출 직전 (매 반복)</td><td><b>반복 상한</b> — 넘으면 예외로 차단</td></tr>
 *   <tr><td>doAfterCall</td><td>모델 호출 직후 (매 반복)</td><td><b>토큰 예산</b> — 누적 초과 시 차단</td></tr>
 *   <tr><td>doGetNextInstructionsForToolCall</td><td>툴 실행 후</td><td><b>컨텍스트 최적화</b> — 너무 긴 툴 결과를 잘라 다음 반복에 넘김</td></tr>
 *   <tr><td>doFinalizeLoop</td><td>루프 종료 (1회)</td><td>총 반복·토큰·시간 로그</td></tr>
 * </table>
 *
 * <p><b>상태를 필드에 두지 않는다.</b> 어드바이저 인스턴스 하나를 모든 요청이 공유하므로, 필드에 카운터를 두면 동시 요청끼리
 * 숫자가 섞이고 이전 요청의 카운트가 다음 요청을 막는다. 그래서 doInitializeLoop에서 요청 컨텍스트에 상태 객체를 넣는다.
 * ToolCallingAdvisor는 반복마다 컨텍스트 맵을 '얕게' 복사하므로, 맵 안의 상태 객체는 같은 것이 계속 전달된다
 * (2.0.1 ToolCallingAdvisor.adviseCall과 ChatModelCallAdvisor의 Map.copyOf로 확인).
 *
 * <p>2.0의 ToolCallingManager에도 기본 한도(툴당 40회, 전체 150회)가 있다. 이 가드는 그보다 훨씬 작은 '업무 기준' 한도다.
 */
public class SafeGuardToolCallingAdvisor extends ToolCallingAdvisor {

    private static final Logger log = LoggerFactory.getLogger(SafeGuardToolCallingAdvisor.class);
    static final String STATE_KEY = "ch6.safeguard.state";

    /** 한 요청(한 번의 루프) 동안만 사는 상태. */
    static final class LoopState {
        int iterations;
        long tokens;
        final long startedAt = System.currentTimeMillis();
    }

    public static class LoopGuardException extends RuntimeException {
        public LoopGuardException(String message) {
            super(message);
        }
    }

    private final int maxIterations;
    private final long tokenBudget;
    private final int maxToolResultChars;

    public SafeGuardToolCallingAdvisor(int maxIterations, long tokenBudget, int maxToolResultChars) {
        super(ToolCallingManager.builder().build(), DEFAULT_TOOL_EXECUTION_ELIGIBILITY_CHECKER,
                BaseAdvisor.HIGHEST_PRECEDENCE + 300, true);
        this.maxIterations = maxIterations;
        this.tokenBudget = tokenBudget;
        this.maxToolResultChars = maxToolResultChars;
    }

    @Override
    protected ChatClientRequest doInitializeLoop(ChatClientRequest request, CallAdvisorChain chain) {
        return request.mutate().context(STATE_KEY, new LoopState()).build();
    }

    @Override
    protected ChatClientRequest doBeforeCall(ChatClientRequest request, CallAdvisorChain chain) {
        LoopState state = state(request.context().get(STATE_KEY));
        if (++state.iterations > maxIterations) {
            throw new LoopGuardException("툴 루프가 " + maxIterations + "회를 넘어 중단했습니다 (같은 툴을 반복 호출하는지 확인).");
        }
        return request;
    }

    @Override
    protected ChatClientResponse doAfterCall(ChatClientResponse response, CallAdvisorChain chain) {
        LoopState state = state(response.context().get(STATE_KEY));
        ChatResponse cr = response.chatResponse();
        Usage usage = cr == null ? null : cr.getMetadata().getUsage();
        if (usage != null && usage.getTotalTokens() != null) {
            state.tokens += usage.getTotalTokens();
        }
        if (state.tokens > tokenBudget) {
            throw new LoopGuardException("토큰 예산 " + tokenBudget + "을 넘었습니다 (누적 " + state.tokens + ").");
        }
        return response;
    }

    @Override
    protected List<Message> doGetNextInstructionsForToolCall(ChatClientRequest request, ChatClientResponse response,
                                                             ToolExecutionResult result) {
        List<Message> next = new ArrayList<>(super.doGetNextInstructionsForToolCall(request, response, result));
        // 너무 긴 툴 결과를 잘라서 다음 반복에 넘긴다 — 컨텍스트가 매 반복 불어나는 걸 막는다.
        // 대가: 정보가 사라진다. 실측에서 600자로 자른 감사 로그(200줄)를 보고 모델이 "출고 8번"이라고 단정했다(실제 66번).
        // 그래서 잘렸다는 사실과 '추정 금지'를 결과 끝에 붙인다. 근본 해결은 집계 툴(countOutbound 같은)을 따로 주는 것.
        // 주의: '마지막 메시지만' 자르면 안 된다. 부모는 자르지 않은 전체 이력(fullTurnHistory)을 따로 들고 있다가
        // 매 반복 그걸로 다시 만들어 주므로, 앞 반복의 긴 결과가 원래 길이로 되살아난다. 그래서 목록 전체를 본다.
        next.replaceAll(m -> m instanceof ToolResponseMessage trm ? trim(trm) : m);
        return next;
    }

    private ToolResponseMessage trim(ToolResponseMessage trm) {
        List<ToolResponseMessage.ToolResponse> trimmed = trm.getResponses().stream()
                .map(r -> r.responseData().length() <= maxToolResultChars ? r
                        : new ToolResponseMessage.ToolResponse(r.id(), r.name(),
                                r.responseData().substring(0, maxToolResultChars)
                                        + " …(이하 " + (r.responseData().length() - maxToolResultChars)
                                        + "자 생략. 잘린 결과이므로 전체 개수·합계는 추정하지 말고 잘렸다고 알릴 것)"))
                .toList();
        return ToolResponseMessage.builder().responses(trimmed).metadata(trm.getMetadata()).build();
    }

    @Override
    protected ChatClientResponse doFinalizeLoop(ChatClientResponse response, CallAdvisorChain chain) {
        LoopState state = state(response.context().get(STATE_KEY));
        log.info("[safeguard] 반복 {}회, 토큰 {}, {}ms", state.iterations, state.tokens,
                System.currentTimeMillis() - state.startedAt);
        return response;
    }

    @Override
    public String getName() {
        return "SafeGuard Tool Calling Advisor";
    }

    private static LoopState state(Object value) {
        if (value instanceof LoopState s) {
            return s;
        }
        throw new IllegalStateException("doInitializeLoop가 상태를 넣지 않았다");
    }
}
