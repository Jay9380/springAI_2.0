package kr.jay.springai.ch06cli.capability.remote;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * [6.6.4 Step 2·3, 예제 6.40] 운영 MCP 서버(ops 프로파일)의 툴 — 독립 운영되는 능력.
 *
 * <ul>
 *   <li>{@code check_stock} : 운영(물류센터) 재고 조회. 연결만 하면 메인 에이전트의 툴 목록에 합류한다(클라이언트 코드 변경 0줄).</li>
 *   <li>{@code place_purchase_order} : 실제 발주를 흉내 낸 목(mock) 툴. 실행 <b>직전</b>에 {@code createElicitation}으로
 *       사람 승인을 요청하고, 응답이 올 때까지 멈춘다. 승인한 경우에만 상태(재고)가 바뀐다.</li>
 * </ul>
 * "모델이 툴을 호출하는 것"과 "실제 상태가 바뀌는 것" 사이에 사람 승인이라는 실행 경계가 생긴다.
 *
 * <p>책 예제와 다른 점: 클라이언트가 승인 창구(Elicitation)를 지원하지 않으면 묻지 않고 거부한다(fail-closed).
 * 6.4에서 라이브러리도 예외로 막는 것을 확인했지만, 모델이 이해할 수 있는 문장으로 돌려준다.
 */
@Component
@Profile("ops")
public class OperationsMcpTools {

    private final Map<String, Integer> stock = new ConcurrentHashMap<>(Map.of("SKU-100", 12, "SKU-200", 7, "SKU-300", 47));

    @McpTool(name = "check_stock", description = "운영 시스템(물류센터)에서 SKU의 현재 재고 수량을 조회한다.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    public String checkStock(@McpToolParam(description = "상품 SKU (예: SKU-200)", required = true) String sku) {
        Integer qty = stock.get(normalize(sku));
        return qty == null ? "없는 SKU입니다: " + sku : "%s 현재 재고는 %d개입니다.".formatted(normalize(sku), qty);
    }

    @McpTool(name = "place_purchase_order",
            description = "지정한 SKU를 지정 수량만큼 실제로 발주한다. 발주가 필요하면 이 툴을 직접 호출하라.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = true))
    public String placePurchaseOrder(@McpToolParam(description = "발주할 상품 SKU", required = true) String sku,
                                     @McpToolParam(description = "발주 수량", required = true) int quantity,
                                     McpSyncServerExchange exchange) {
        String key = normalize(sku);
        if (!stock.containsKey(key)) {
            return "실패: 없는 SKU입니다: " + sku;
        }
        if (quantity < 1) {
            return "실패: 발주 수량은 1 이상이어야 합니다.";
        }
        if (exchange.getClientCapabilities() == null || exchange.getClientCapabilities().elicitation() == null) {
            return "거부: 이 클라이언트는 승인 요청을 지원하지 않아 발주할 수 없습니다.";
        }
        McpSchema.ElicitResult approval = exchange.createElicitation(McpSchema.ElicitFormRequest.builder(
                        "place_purchase_order 툴로 %s %d개를 실제로 발주합니다. 진행할까요?".formatted(key, quantity),
                        Map.of("type", "object", "properties", Map.of()))      // 승인만 받는 빈 폼
                .build());
        if (approval.action() != McpSchema.ElicitResult.Action.ACCEPT) {
            return "사용자가 승인하지 않아 발주를 취소했습니다.";
        }
        int after = stock.merge(key, quantity, Integer::sum);
        return "%s %d개를 발주했습니다. (승인 완료, 입고 후 재고 %d개)".formatted(key, quantity, after);
    }

    static String normalize(String sku) {
        return sku == null ? "" : sku.trim().toUpperCase();
    }
}
