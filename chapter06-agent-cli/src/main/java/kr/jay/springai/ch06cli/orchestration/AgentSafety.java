package kr.jay.springai.ch06cli.orchestration;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

/**
 * [6.6.3 예제 6.33] 루프 안전 가드 — 한 요청에서 툴 실행 라운드를 maxRounds번까지만 허용하고,
 * 같은 툴을 같은 인자로 연속 반복하면(진전 없음) 상한을 기다리지 않고 바로 멈춘다.
 *
 * <p><b>책의 방식:</b> {@code AgentSafety.maxToolRounds(n)}가 AtomicInteger를 품은 ToolExecutionEligibilityChecker를 만들고,
 * 카운터가 요청 사이에 공유되지 않도록 <b>루프 어드바이저를 요청마다 새로</b> 만든다.
 *
 * <p><b>여기서 바꾼 것과 이유 (소스·테스트로 확인):</b>
 * <ol>
 *   <li>요청마다 새 ToolSearchToolCallingAdvisor를 만들면, 어드바이저가 인스턴스 안에 두는 '이미 색인함' 표시가 매번 비어
 *       <b>모든 툴을 매 요청 다시 임베딩</b>한다(VectorToolIndex.clearIndex → indexTools). 그래서 어드바이저는 하나만 두고,
 *       라운드 카운터는 요청 컨텍스트에 둔다({@link #init}).</li>
 *   <li>체커로 루프를 끊으면 '툴을 부르려던 응답'(사람이 읽을 텍스트가 거의 없음)이 최종 답이 된다(6장-3 노트 2.4).
 *       그래서 상한을 넘은 응답은 사용자용 안내문으로 바꿔서 루프를 끝낸다({@link #limit}).</li>
 * </ol>
 */
public final class AgentSafety {

    static final String ROUNDS_KEY = "ch6cli.tool-rounds";
    static final String LAST_CALLS_KEY = "ch6cli.last-tool-calls";

    /** 같은 툴을 같은 인자로 연속 몇 번까지 허용할지. 실측: 4B 모델이 같은 TodoWrite를 11번 반복하며 토큰 8.8만 개를 썼다. */
    static final int MAX_IDENTICAL_REPEATS = 2;

    /** 이번 요청의 직전 툴 호출 서명과 연속 반복 횟수. */
    static final class LastCalls {
        String signature = "";
        int repeats;
    }

    private AgentSafety() {
    }

    /** 루프 진입 시(doInitializeLoop) 이번 요청 전용 카운터를 컨텍스트에 넣는다. 컨텍스트는 반복마다 얕게 복사되므로 같은 객체가 이어진다. */
    public static ChatClientRequest init(ChatClientRequest request) {
        return request.mutate().context(ROUNDS_KEY, new AtomicInteger()).context(LAST_CALLS_KEY, new LastCalls()).build();
    }

    /** 모델 호출 직후(doAfterCall): 툴 요청이면 라운드를 세고, 상한을 넘으면 툴 요청을 지운 안내문 응답으로 바꾼다 → 루프 종료. */
    public static ChatClientResponse limit(ChatClientResponse response, int maxRounds) {
        ChatResponse cr = response.chatResponse();
        if (cr == null || !cr.hasToolCalls()) {
            return response;                                   // 모델이 툴을 더 안 부름 → 정상 종료
        }
        if (response.context().get(LAST_CALLS_KEY) instanceof LastCalls last) {
            String signature = cr.getResult().getOutput().getToolCalls().stream()
                    .map(tc -> tc.name() + tc.arguments()).reduce("", String::concat);
            last.repeats = signature.equals(last.signature) ? last.repeats + 1 : 1;
            last.signature = signature;
            if (last.repeats > MAX_IDENTICAL_REPEATS) {
                return stop(response, cr, "같은 툴을 같은 인자로 %d번 연속 호출해 작업을 중단했습니다 (진전 없음).".formatted(last.repeats));
            }
        }
        if (!(response.context().get(ROUNDS_KEY) instanceof AtomicInteger rounds) || rounds.incrementAndGet() <= maxRounds) {
            return response;                                   // 상한 안 → 한 라운드 더
        }
        return stop(response, cr, "툴 실행이 %d라운드를 넘어 작업을 중단했습니다. 요청을 더 작게 나눠 주세요.".formatted(maxRounds));
    }

    private static ChatClientResponse stop(ChatClientResponse response, ChatResponse cr, String message) {
        return response.mutate()
                .chatResponse(ChatResponse.builder().from(cr).generations(List.of(new Generation(new AssistantMessage(message)))).build())
                .build();
    }
}
