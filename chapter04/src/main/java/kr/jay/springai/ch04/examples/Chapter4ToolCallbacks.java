package kr.jay.springai.ch04.examples;

import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.tool.metadata.ToolMetadata;

/**
 * [4.3.2] 함수를 툴로 — FunctionToolCallback으로 '코드에서 조립'하는 도구들.
 *
 * <p>@Tool과 비교
 * <ul>
 *   <li>@Tool : 기존 서비스 메서드에 애너테이션만 붙여 여러 도구를 한 클래스로 묶는다 (그룹화)</li>
 *   <li>FunctionToolCallback : 도구 하나가 독립 부품. 설명·메타데이터·결과 변환기를 실행 시점에 조립할 수 있다 (모듈화)</li>
 * </ul>
 * 함수 방식의 입력·출력은 public 레코드/POJO여야 한다. 원시 타입이나 List를 '단독'으로 쓸 수 없다(책 p.300).
 */
public final class Chapter4ToolCallbacks {

    private Chapter4ToolCallbacks() {
    }

    // ── 할인 계산 ────────────────────────────────────────────────

    /** 필드 설명은 @JsonPropertyDescription으로 스키마에 들어간다 → 모델이 무엇을 넣을지 안다 */
    public record DiscountRequest(
            @JsonPropertyDescription("원래 가격(원)") long price,
            @JsonPropertyDescription("할인율(%). 18은 18% 할인") double discountRate) {
    }

    public record DiscountResult(long originalPrice, double discountRate, long discountAmount, long finalPrice) {
    }

    /** 예제 4.16 방식: builder(이름, 함수) + description + inputType(스키마 자동 생성의 근거) */
    public static ToolCallback discountCalculator() {
        Function<DiscountRequest, DiscountResult> fn = req -> {
            long discount = Math.round(req.price() * req.discountRate() / 100.0);
            return new DiscountResult(req.price(), req.discountRate(), discount, req.price() - discount);
        };
        return FunctionToolCallback.builder(ToolNames.DISCOUNT_CALCULATOR, fn)
                .description("상품 가격에 할인율을 적용한 최종 금액을 계산합니다.")
                .inputType(DiscountRequest.class)
                .build();
    }

    // ── 고객 연락처 (결과 변환기) ────────────────────────────────────

    public record CustomerRequest(@JsonPropertyDescription("고객 ID (예: C-100)") String customerId) {
    }

    public record CustomerContact(String customerId, String name, String email, String grade) {
    }

    private static final Map<String, CustomerContact> CUSTOMERS = Map.of(
            "C-100", new CustomerContact("C-100", "김고객", "kim.customer@example.com", "GOLD"),
            "C-200", new CustomerContact("C-200", "이고객", "lee.customer@example.com", "SILVER"));

    /**
     * 결과 변환기로 이메일을 가린다 — 도구는 원본을 돌려주지만 '모델이 보는 글'에서는 [EMAIL]로 바뀐다.
     */
    public static ToolCallback customerContactLookup() {
        Function<CustomerRequest, CustomerContact> fn = req -> {
            CustomerContact c = CUSTOMERS.get(req.customerId());
            if (c == null) {
                throw new IllegalArgumentException("고객을 찾을 수 없습니다: " + req.customerId());
            }
            return c;
        };
        return FunctionToolCallback.builder(ToolNames.CUSTOMER_CONTACT_LOOKUP, fn)
                .description("고객 ID로 고객 이름·이메일·등급을 조회합니다.")
                .inputType(CustomerRequest.class)
                .toolCallResultConverter(new EmailMaskingToolCallResultConverter())
                .build();
    }

    // ── 세션 요약 (ToolContext + returnDirect) ─────────────────────────

    public record SessionRequest(@JsonPropertyDescription("요약에 포함할 학습 주제") String topic) {
    }

    /**
     * [4.2.4] ToolContext — 모델이 모르는(알면 안 되는) 앱 상태를 실행 시점에 주입한다.
     * BiFunction의 두 번째 인자로 들어온다. 이 값은 모델에게 전송되지 않는다.
     *
     * <p>returnDirect=true면 도구 결과를 모델에게 돌려보내지 않고 그대로 사용자에게 반환한다.
     * 요약문을 모델이 다시 다듬을 필요가 없을 때 LLM 호출 한 번을 아낀다.
     * (주의: 한 번에 여러 도구가 불리면 '모든' 도구가 returnDirect여야 직접 반환된다)
     */
    public static ToolCallback sessionSummary(boolean returnDirect) {
        BiFunction<SessionRequest, ToolContext, String> fn = (req, ctx) -> """
                세션 요약
                - 사용자: %s
                - 대화 ID: %s
                - 학습 주제: %s""".formatted(
                ctx.getContext().getOrDefault("userName", "unknown"),
                ctx.getContext().getOrDefault("conversationId", "unknown"),
                req.topic());
        return FunctionToolCallback.builder(ToolNames.SESSION_SUMMARY, fn)
                .description("현재 CLI 세션의 사용자·대화 ID·학습 주제를 요약합니다.")
                .inputType(SessionRequest.class)
                .toolMetadata(ToolMetadata.builder().returnDirect(returnDirect).build())
                .build();
    }
}
