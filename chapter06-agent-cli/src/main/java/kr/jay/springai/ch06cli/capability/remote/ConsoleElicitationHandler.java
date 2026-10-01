package kr.jay.springai.ch06cli.capability.remote;

import java.util.Map;
import java.util.function.Supplier;

import io.modelcontextprotocol.spec.McpSchema.ElicitRequest;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import kr.jay.springai.ch06cli.channel.ChatConsole;
import org.springframework.ai.mcp.annotation.McpElicitation;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * [6.6.4 예제 6.41] 운영 서버에서 온 승인 요청에 사람이 답한다 (T1 채널이 T3의 요청에 응답하는 자리).
 *
 * <p>{@code y}일 때만 ACCEPT. 그 밖의 입력이나 입력 끊김은 모두 DECLINE — '대답 없음'을 승인으로 취급하지 않는다.
 * 서버가 늘어나도 연결 이름(clients) 기준으로 승인 범위를 나눠 한곳에서 관리한다.
 */
@Component
@Profile("with-ops")
public class ConsoleElicitationHandler {

    private final Supplier<String> input;

    public ConsoleElicitationHandler() {
        this(ChatConsole::readLine);
    }

    public ConsoleElicitationHandler(Supplier<String> input) {
        this.input = input;
    }

    @McpElicitation(clients = "operations")
    public ElicitResult onElicitation(ElicitRequest request) {
        System.out.print("\n[승인 요청] " + request.message() + " (y/n) > ");
        String answer = input.get();
        if (answer != null && answer.trim().equalsIgnoreCase("y")) {
            return new ElicitResult(ElicitResult.Action.ACCEPT, Map.of(), null);
        }
        return new ElicitResult(ElicitResult.Action.DECLINE, null, null);
    }
}
