package kr.jay.springai.ch06;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.Map;

import io.modelcontextprotocol.spec.McpSchema.ElicitFormRequest;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import kr.jay.springai.ch06.hitl.ConsoleElicitationHandler;
import org.junit.jupiter.api.Test;

class ConsoleElicitationHandlerTest {

    static ConsoleElicitationHandler answering(String... lines) {
        Deque<String> q = new ArrayDeque<>(Arrays.asList(lines));
        return new ConsoleElicitationHandler(q::poll);              // 입력이 바닥나면 null
    }

    static final ElicitFormRequest REQ = new ElicitFormRequest("payment-api 재시작?", Map.of(), null);

    @Test
    void yes_acceptsWithForm() {
        ElicitResult r = answering("y", "배포 후 오류").onElicitation(REQ);
        assertThat(r.action()).isEqualTo(ElicitResult.Action.ACCEPT);
        assertThat(r.content()).containsEntry("confirm", true).containsEntry("reason", "배포 후 오류");
    }

    @Test
    void anythingElse_declines() {
        assertThat(answering("yes please").onElicitation(REQ).action()).isEqualTo(ElicitResult.Action.DECLINE);
        assertThat(answering("n").onElicitation(REQ).action()).isEqualTo(ElicitResult.Action.DECLINE);
        assertThat(answering("").onElicitation(REQ).action()).isEqualTo(ElicitResult.Action.DECLINE);
    }

    @Test
    void noInput_cancels_neverAccepts() {
        assertThat(answering().onElicitation(REQ).action()).isEqualTo(ElicitResult.Action.CANCEL);
    }
}
