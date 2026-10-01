package kr.jay.springai.ch06.loop;

import java.util.List;
import java.util.Map;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * [6.1.4 예제 6.7] 수동 루프에서 쓸 고객 툴. 데이터는 메모리 맵(학습용).
 *
 * <p>툴 두 개를 연쇄로 써야 답할 수 있게 만들었다: 고객 → 주문. 모델이 한 번에 끝내지 못하고
 * "고객 조회 → 결과 관찰 → 주문 조회 → 결과 관찰 → 답변"을 거치므로 루프가 실제로 몇 번 도는지 볼 수 있다.
 */
public class CustomerTools {

    private static final Map<Long, String> CUSTOMERS = Map.of(
            42L, "{\"id\":42,\"name\":\"김지니\",\"grade\":\"GOLD\"}",
            7L, "{\"id\":7,\"name\":\"박하늘\",\"grade\":\"SILVER\"}");

    private static final Map<Long, List<String>> ORDERS = Map.of(
            42L, List.of("ORD-1001 로드자전거 헬멧", "ORD-1002 전조등"),
            7L, List.of());

    @Tool(description = "고객 ID로 고객 기본 정보(이름, 등급)를 조회합니다.")
    public String getCustomer(@ToolParam(description = "고객 ID (숫자)") long id) {
        return CUSTOMERS.getOrDefault(id, "{\"error\":\"고객 없음\"}");
    }

    @Tool(description = "고객 ID로 최근 주문 목록을 조회합니다.")
    public List<String> getRecentOrders(@ToolParam(description = "고객 ID (숫자)") long customerId) {
        return ORDERS.getOrDefault(customerId, List.of());
    }
}
