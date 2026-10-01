package kr.jay.springai.ch06.context;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * [6.2.3 예제 6.9] ReAct 예제의 주문 툴 + 위험한 쓰기 툴 하나(cancelOrder).
 *
 * <p>조회 툴과 취소 툴이 한 클래스에 있다. 누구에게 취소 툴을 '보여 줄지'는 이 클래스가 아니라
 * {@link ContextEngineeredAgent}가 역할을 보고 결정한다 — 툴 스펙이 '전달'과 '집행' 두 영역에 걸친다는 책의 말.
 */
public class OrderTools {

    public static final String CANCEL_ORDER = "cancelOrder";

    private final Map<String, String> status = new ConcurrentHashMap<>(Map.of(
            "ORD-1001", "배송 준비", "ORD-1002", "배송 중"));

    @Tool(description = "고객의 주문 이력(주문번호와 상태)을 조회합니다.")
    public List<String> getCustomerOrders(@ToolParam(description = "고객 ID") String customerId) {
        if (!"C-42".equals(customerId)) {
            return List.of();
        }
        return status.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + " (" + e.getValue() + ")").toList();
    }

    @Tool(description = "주문을 취소합니다. 배송 시작 전(배송 준비) 주문만 취소할 수 있습니다.")
    public String cancelOrder(@ToolParam(description = "주문번호 (예: ORD-1001)") String orderId) {
        String current = status.get(orderId);
        if (current == null) {
            return "실패: 없는 주문번호입니다. getCustomerOrders로 주문번호를 먼저 확인하세요.";   // 실패 기록 + 다음 행동 힌트
        }
        if (!"배송 준비".equals(current)) {
            return "실패: 이미 '" + current + "' 상태라 취소할 수 없습니다. 고객에게 반품 절차를 안내하세요.";
        }
        status.put(orderId, "취소됨");
        return orderId + " 취소 완료";
    }

    public String statusOf(String orderId) {
        return status.get(orderId);
    }
}
