package kr.jay.springai.ch06;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.ElicitFormRequest;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

/**
 * 운영 MCP 서버를 실제로 띄우고 순수 MCP 클라이언트로 붙어, 승인(Elicitation) 경로를 프로토콜 수준에서 확인한다.
 * 실패 경로(거절·취소·폼 미체크·승인 창구 없음)가 '실행되지 않음'으로 끝나는지가 핵심이다.
 * 서버 빈은 테스트끼리 공유되므로 테스트마다 서비스 이름을 다르게 쓴다(처음엔 같은 이름을 써서 재시작 횟수가 섞였다).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("ops-server")
class OpsServerElicitationTest {

    @LocalServerPort
    int port;

    final List<McpSyncClient> clients = new ArrayList<>();
    final List<ElicitFormRequest> asked = new ArrayList<>();

    McpSyncClient client(Function<ElicitFormRequest, ElicitResult> handler) {
        var transport = HttpClientStreamableHttpTransport.builder("http://localhost:" + port).endpoint("/mcp").build();
        var spec = McpClient.sync(transport);
        if (handler != null) {
            spec.capabilities(McpSchema.ClientCapabilities.builder().elicitation().build())
                    .elicitation(req -> {
                        asked.add(req);
                        return handler.apply(req);
                    });
        }
        McpSyncClient c = spec.build();
        c.initialize();
        clients.add(c);
        return c;
    }

    @AfterEach
    void close() {
        clients.forEach(McpSyncClient::closeGracefully);
    }

    static String call(McpSyncClient c, String tool, String service) {
        var result = c.callTool(new McpSchema.CallToolRequest(tool, Map.of("service", service)));
        return ((McpSchema.TextContent) result.content().getFirst()).text();
    }

    @Test
    void readOnlyTool_runsWithoutAsking() {
        McpSyncClient c = client(req -> { throw new AssertionError("조회 툴은 승인을 묻지 않아야 한다"); });
        assertThat(call(c, "get_service_status", "status-only")).contains("RUNNING").contains("재시작 0회");
        assertThat(asked).isEmpty();
    }

    @Test
    void accept_runsTheDangerousTool() {
        McpSyncClient c = client(req -> new ElicitResult(ElicitResult.Action.ACCEPT,
                Map.of("confirm", true, "reason", "메모리 누수"), null));

        assertThat(call(c, "restart_service", "payment-api")).contains("재시작 완료").contains("메모리 누수");
        assertThat(asked).singleElement().satisfies(req -> {
            assertThat(req.message()).contains("payment-api");
            assertThat(req.requestedSchema().toString()).contains("confirm").contains("reason");   // record → 폼 스키마
        });
        assertThat(call(c, "get_service_status", "payment-api")).contains("재시작 1회");
    }

    @Test
    void decline_or_cancel_doesNotRun() {
        McpSyncClient declining = client(req -> new ElicitResult(ElicitResult.Action.DECLINE, null, null));
        assertThat(call(declining, "restart_service", "billing")).startsWith("중단").contains("DECLINE");

        McpSyncClient cancelling = client(req -> new ElicitResult(ElicitResult.Action.CANCEL, null, null));
        assertThat(call(cancelling, "restart_service", "billing")).startsWith("중단").contains("CANCEL");

        assertThat(call(declining, "get_service_status", "billing")).contains("재시작 0회");
    }

    @Test
    void acceptWithoutConfirmCheck_doesNotRun() {
        McpSyncClient c = client(req -> new ElicitResult(ElicitResult.Action.ACCEPT,
                Map.of("confirm", false, "reason", "실수로 누름"), null));
        assertThat(call(c, "restart_service", "search")).contains("확인(confirm)이 체크되지 않았습니다");
        assertThat(call(c, "get_service_status", "search")).contains("재시작 0회");
    }

    @Test
    void clientWithoutApprovalChannel_isRefused_failClosed() {
        McpSyncClient noElicitation = client(null);               // 승인 창구가 없는 클라이언트
        assertThat(call(noElicitation, "restart_service", "no-channel")).startsWith("거부");
        assertThat(call(noElicitation, "get_service_status", "no-channel")).contains("재시작 0회");
    }
}
