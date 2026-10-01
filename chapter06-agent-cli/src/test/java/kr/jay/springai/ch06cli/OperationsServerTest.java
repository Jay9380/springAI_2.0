package kr.jay.springai.ch06cli;

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

/** 운영 MCP 서버(ops)를 실제로 띄워 발주 승인 게이트를 프로토콜 수준에서 확인한다. 서버 상태는 테스트끼리 공유되므로 SKU를 나눠 쓴다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("ops")
class OperationsServerTest {

    @LocalServerPort
    int port;

    final List<McpSyncClient> clients = new ArrayList<>();

    McpSyncClient client(Function<ElicitFormRequest, ElicitResult> approver) {
        var spec = McpClient.sync(HttpClientStreamableHttpTransport.builder("http://localhost:" + port).endpoint("/mcp").build());
        if (approver != null) {
            spec.capabilities(McpSchema.ClientCapabilities.builder().elicitation().build()).elicitation(approver);
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

    static String call(McpSyncClient c, String tool, Map<String, Object> args) {
        return ((McpSchema.TextContent) c.callTool(new McpSchema.CallToolRequest(tool, args)).content().getFirst()).text();
    }

    @Test
    void exposesTwoTools_withHonestHints() {
        var tools = client(null).listTools().tools();
        assertThat(tools).extracting(McpSchema.Tool::name).containsExactlyInAnyOrder("check_stock", "place_purchase_order");
        assertThat(tools).filteredOn(t -> t.name().equals("place_purchase_order"))
                .singleElement().satisfies(t -> assertThat(t.annotations().destructiveHint()).isTrue());
    }

    @Test
    void approvedOrder_changesStock_andTheQuestionNamesSkuAndQuantity() {
        List<String> asked = new ArrayList<>();
        McpSyncClient c = client(req -> {
            asked.add(req.message());
            return new ElicitResult(ElicitResult.Action.ACCEPT, Map.of(), null);
        });
        assertThat(call(c, "place_purchase_order", Map.of("sku", "SKU-200", "quantity", 43))).contains("발주했습니다").contains("50개");
        assertThat(asked).singleElement().asString().contains("SKU-200").contains("43개");
        assertThat(call(c, "check_stock", Map.of("sku", "SKU-200"))).contains("50개");
    }

    @Test
    void declinedOrder_doesNotChangeStock() {
        McpSyncClient c = client(req -> new ElicitResult(ElicitResult.Action.DECLINE, null, null));
        assertThat(call(c, "place_purchase_order", Map.of("sku", "SKU-100", "quantity", 38))).contains("취소");
        assertThat(call(c, "check_stock", Map.of("sku", "SKU-100"))).contains("12개");
    }

    @Test
    void clientWithoutApprovalChannel_isRefused_andInvalidInputNeverAsks() {
        assertThat(call(client(null), "place_purchase_order", Map.of("sku", "SKU-300", "quantity", 3))).startsWith("거부");

        McpSyncClient strict = client(req -> { throw new AssertionError("잘못된 입력에는 승인을 묻지 않아야 한다"); });
        assertThat(call(strict, "place_purchase_order", Map.of("sku", "SKU-999", "quantity", 3))).startsWith("실패");
        assertThat(call(strict, "place_purchase_order", Map.of("sku", "SKU-300", "quantity", 0))).startsWith("실패");
        assertThat(call(strict, "check_stock", Map.of("sku", "SKU-300"))).contains("47개");
    }
}
