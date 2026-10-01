package kr.jay.springai.ch06.advisor;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * 6.3 실습용 창고 툴. 일부러 결과가 아주 긴 툴(getAuditLog)을 하나 두었다 —
 * 안전 가드의 '툴 결과 자르기'(doGetNextInstructionsForToolCall)가 실제로 컨텍스트를 줄이는지 보려고.
 */
public class WarehouseTools {

    private static final Map<String, Integer> STOCK = Map.of("SKU-100", 12, "SKU-200", 0, "SKU-300", 47);

    @Tool(description = "상품 SKU의 현재 재고 수량을 조회합니다.")
    public int getStock(@ToolParam(description = "상품 SKU (예: SKU-100)") String sku) {
        return STOCK.getOrDefault(sku, -1);
    }

    @Tool(description = "상품 SKU의 입출고 감사 로그 전체를 조회합니다. 결과가 매우 깁니다. 개수·합계가 필요하면 countAuditEvents를 쓰세요.")
    public String getAuditLog(@ToolParam(description = "상품 SKU") String sku) {
        return log(sku).collect(Collectors.joining("\n"));
    }

    /**
     * 집계는 코드가 한다. 실측: 긴 로그를 잘라서 넘기면 모델이 잘린 일부만 보고 "출고 7번"이라고 단정했다(실제 66번).
     * "추정하지 말라"는 문구를 붙여도 같았다. 정답이 정해진 계산은 LLM에게 원문을 읽히지 말고 툴로 주는 게 해법이다.
     */
    @Tool(description = "상품 SKU의 감사 로그에서 특정 유형(입고 또는 출고) 이벤트의 건수와 수량 합계를 정확히 계산합니다.")
    public String countAuditEvents(@ToolParam(description = "상품 SKU") String sku,
                                   @ToolParam(description = "이벤트 유형: 입고 또는 출고") String type) {
        var lines = log(sku).filter(l -> l.contains(" " + type + " ")).toList();
        int quantity = lines.stream().mapToInt(l -> Integer.parseInt(l.replaceAll(".* (\\d+)개$", "$1"))).sum();
        return "%s %s: %d건, 수량 합계 %d개".formatted(sku, type, lines.size(), quantity);
    }

    private static java.util.stream.Stream<String> log(String sku) {
        return IntStream.rangeClosed(1, 200)
                .mapToObj(i -> "2026-09-%02d %s %s %d개".formatted(i % 30 + 1, sku, i % 3 == 0 ? "출고" : "입고", i % 7 + 1));
    }
}
