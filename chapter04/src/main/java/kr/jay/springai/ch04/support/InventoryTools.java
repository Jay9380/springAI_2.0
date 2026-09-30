package kr.jay.springai.ch04.support;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import kr.jay.springai.ch04.examples.ToolNames;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * [4.5.3 예제 4.38] 재고 도구 — 외부 재고 시스템을 흉내 내는 인메모리 도구.
 *
 * <p>조회(list·lookup)는 읽기 전용이지만 예약(reserve)은 <b>앱 상태를 바꾼다.</b>
 * 읽기 도구와 쓰기 도구는 위험도가 다르다. 쓰기 도구는 승인·감사 로그·권한 검사를 붙일 후보다 (Step4 승인 흐름).
 *
 * <p>실패는 예외 대신 '이유를 담은 결과 문자열'로 돌려준다. 모델이 그 이유를 읽고 사용자에게 설명할 수 있다.
 */
@Component
public class InventoryTools {

    private record Product(String name, int stock) {
    }

    private final Map<String, Product> products = new LinkedHashMap<>(Map.of(
            "SKU-100", new Product("Spring AI 입문 워크북", 12),
            "SKU-200", new Product("Agent 설계 노트", 7),
            "SKU-300", new Product("MCP 핸드북", 0)));

    @Tool(name = ToolNames.PRODUCT_LIST, description = "판매 중인 전체 상품의 SKU, 이름, 재고 수량 목록을 반환합니다.")
    public String listProducts() {
        return products.entrySet().stream()
                .map(e -> e.getKey() + " | " + e.getValue().name() + " | 재고 " + e.getValue().stock())
                .collect(Collectors.joining("\n"));
    }

    @Tool(name = ToolNames.PRODUCT_LOOKUP, description = "SKU로 상품 이름과 재고 수량을 조회합니다.")
    public String lookupProduct(@ToolParam(description = "조회할 상품 SKU (예: SKU-100)") String sku) {
        Product p = products.get(sku.trim().toUpperCase());
        return p == null ? "존재하지 않는 SKU입니다: " + sku : sku + " " + p.name() + " 재고 " + p.stock() + "개";
    }

    @Tool(name = ToolNames.PRODUCT_RESERVE,
          description = "SKU와 수량을 받아 재고를 예약합니다. 재고가 부족하면 실패 사유를 반환합니다.")
    public synchronized String reserveProduct(
            @ToolParam(description = "예약할 상품 SKU") String sku,
            @ToolParam(description = "예약 수량 (1 이상)") int quantity) {
        String key = sku.trim().toUpperCase();
        Product p = products.get(key);
        if (p == null) {
            return "예약 실패: 존재하지 않는 SKU " + sku;
        }
        if (quantity <= 0) {
            return "예약 실패: 수량은 1 이상이어야 합니다";
        }
        if (p.stock() < quantity) {
            return "예약 실패: " + key + " 재고 " + p.stock() + "개로 " + quantity + "개를 예약할 수 없습니다";
        }
        products.put(key, new Product(p.name(), p.stock() - quantity));
        return "예약 완료: " + key + " " + quantity + "개 (남은 재고 " + (p.stock() - quantity) + "개)";
    }

    public int stockOf(String sku) {
        return products.get(sku).stock();
    }
}
