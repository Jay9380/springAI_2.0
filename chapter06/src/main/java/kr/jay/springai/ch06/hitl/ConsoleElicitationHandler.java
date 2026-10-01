package kr.jay.springai.ch06.hitl;

import java.util.Map;
import java.util.function.Supplier;

import io.modelcontextprotocol.spec.McpSchema.ElicitRequest;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import kr.jay.springai.ch06.support.ChatConsole;
import org.springframework.ai.mcp.annotation.McpElicitation;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * [6.4.4 예제 6.18] 에이전트(클라이언트) 쪽 승인 핸들러 — 서버 툴이 보낸 승인 요청에 사람이 답한다.
 *
 * <p>외부 MCP 툴의 내부 코드는 우리가 통제할 수 없다. 하지만 그 툴이 던지는 승인 요청에 응답하는 이 핸들러는
 * <b>우리 코드</b>다. 그래서 이 작은 클래스 하나로, 연결된 MCP 서버의 모든 위험한 툴이 같은 게이트를 지난다.
 *
 * <ul>
 *   <li>{@code clients = "operations"} : application-agent.yml의 연결 이름. 그 서버에서 온 요청만 받는다.</li>
 *   <li>y → ACCEPT + 폼 내용(confirm, reason) / 그 외 → DECLINE (서버 툴은 즉시 중단)</li>
 *   <li>입력이 끊기면(null) CANCEL — '대답 없음'을 승인으로 취급하지 않는다.</li>
 * </ul>
 * 웹이라면 콘솔 대신 SSE·웹소켓으로 승인 팝업을 띄우고 비동기로 기다리도록 바꾼다.
 */
@Component
@Profile("agent")
public class ConsoleElicitationHandler {

    private final Supplier<String> input;

    public ConsoleElicitationHandler() {
        this(ChatConsole::readLine);
    }

    /** 테스트·웹 환경용: 사람의 답을 어디서 읽을지 바꿀 수 있다. */
    public ConsoleElicitationHandler(Supplier<String> input) {
        this.input = input;
    }

    @McpElicitation(clients = "operations")
    public ElicitResult onElicitation(ElicitRequest request) {
        System.out.print("\n[승인 요청] " + request.message() + " (y/n) > ");
        String answer = input.get();
        if (answer == null) {
            return new ElicitResult(ElicitResult.Action.CANCEL, null, null);
        }
        if (!"y".equalsIgnoreCase(answer.trim())) {
            return new ElicitResult(ElicitResult.Action.DECLINE, null, null);
        }
        System.out.print("[사유] > ");
        String reason = input.get();
        return new ElicitResult(ElicitResult.Action.ACCEPT,
                Map.of("confirm", true, "reason", reason == null || reason.isBlank() ? "(사유 없음)" : reason.trim()), null);
    }
}
