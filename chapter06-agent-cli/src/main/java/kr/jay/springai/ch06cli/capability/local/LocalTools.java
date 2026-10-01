package kr.jay.springai.ch06cli.capability.local;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * [6.6.3 Step 1] T3 로컬 능력 — 메인 에이전트와 같은 프로세스에서 바로 실행되는 툴 7개.
 *
 * <p>툴 경계 원칙: 같은 프로세스에서 바로 실행하는 가벼운 능력은 로컬 툴, 독립 운영하는 능력은 MCP 서버.
 * 책의 실행 예 "지금 한국 시간을 알려주고, SKU-200 재고를 확인한 뒤 2개 예약해줘"를 그대로 해 볼 수 있다.
 * 계산 툴을 따로 두는 이유: 숫자 계산은 LLM에게 맡기지 않는다(책 p.641의 한계 지적, 1장 원칙).
 */
public class LocalTools {

    record Product(String name, int stock) {
    }

    private final Map<String, Product> products = new ConcurrentHashMap<>(Map.of(
            "SKU-100", new Product("로드자전거 헬멧", 12),
            "SKU-200", new Product("자전거 전조등", 7),
            "SKU-300", new Product("물병 거치대", 47)));

    @Tool(name = "current_datetime", description = "지정한 시간대의 현재 날짜와 시각을 알려준다. 시간대 예: Asia/Seoul")
    public String currentDatetime(@ToolParam(description = "IANA 시간대 ID", required = false) String zone) {
        ZoneId zoneId = zone == null || zone.isBlank() ? ZoneId.of("Asia/Seoul") : ZoneId.of(zone);
        return ZonedDateTime.now(zoneId).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm (z)"));
    }

    @Tool(name = "product_lookup", description = "로컬 매장 재고 시스템에서 SKU의 상품명과 매장 재고 수량을 조회한다.")
    public String productLookup(@ToolParam(description = "상품 SKU (예: SKU-200)") String sku) {
        Product p = products.get(normalize(sku));
        return p == null ? "없는 SKU입니다: " + sku : "%s %s, 매장 재고 %d개".formatted(normalize(sku), p.name(), p.stock());
    }

    @Tool(name = "product_reserve", description = "매장 재고에서 지정 수량을 고객 예약으로 잡아 둔다. 재고보다 많이 예약할 수 없다.")
    public String productReserve(@ToolParam(description = "상품 SKU") String sku,
                                 @ToolParam(description = "예약 수량 (1 이상)") int quantity) {
        String key = normalize(sku);
        if (quantity < 1) {
            return "실패: 예약 수량은 1 이상이어야 합니다.";
        }
        Product[] after = new Product[1];
        Product current = products.computeIfPresent(key, (k, p) -> {
            if (p.stock() < quantity) {
                return p;                                         // 재고 부족: 그대로 둔다
            }
            after[0] = new Product(p.name(), p.stock() - quantity);
            return after[0];
        });
        if (current == null) {
            return "실패: 없는 SKU입니다: " + sku;
        }
        if (after[0] == null) {
            return "실패: 재고 부족 (요청 %d개, 재고 %d개)".formatted(quantity, current.stock());
        }
        return "%s %s %d개 예약 완료, 남은 재고 %d개".formatted(key, current.name(), quantity, current.stock());
    }

    @Tool(name = "calculator_add", description = "두 수를 더한다.")
    public BigDecimal add(@ToolParam(description = "a") BigDecimal a, @ToolParam(description = "b") BigDecimal b) {
        return a.add(b);
    }

    @Tool(name = "calculator_subtract", description = "a에서 b를 뺀다. 부족분 계산(기준 − 현재)에 쓴다.")
    public BigDecimal subtract(@ToolParam(description = "a") BigDecimal a, @ToolParam(description = "b") BigDecimal b) {
        return a.subtract(b);
    }

    @Tool(name = "calculator_multiply", description = "두 수를 곱한다.")
    public BigDecimal multiply(@ToolParam(description = "a") BigDecimal a, @ToolParam(description = "b") BigDecimal b) {
        return a.multiply(b);
    }

    @Tool(name = "calculator_divide", description = "a를 b로 나눈다 (소수 둘째 자리 반올림).")
    public String divide(@ToolParam(description = "a") BigDecimal a, @ToolParam(description = "b") BigDecimal b) {
        if (b.signum() == 0) {
            return "실패: 0으로 나눌 수 없습니다.";
        }
        return a.divide(b, 2, RoundingMode.HALF_UP).toPlainString();
    }

    static String normalize(String sku) {
        return sku == null ? "" : sku.trim().toUpperCase();
    }
}
