package kr.jay.springai.ch04.examples;

/**
 * [4.3.3 예제 4.17] 모델에 노출하는 도구 이름을 상수로 한곳에서 관리한다.
 *
 * <p>도구 이름은 모델이 호출할 때 쓰는 '식별자'다. FunctionToolCallback은 이름을 문자열로 받으므로
 * 오타가 컴파일 때 잡히지 않고, 런타임에 모델이 엉뚱한 이름을 찾게 된다. 상수로 두면 이 실수가 줄어든다.
 * 규칙: 도메인_동작 (weather_current, product_reserve …) → 개별 도구라도 그룹이 보인다.
 */
public final class ToolNames {

    public static final String CURRENT_DATETIME = "current_datetime";
    public static final String CURRENT_DATE = "current_date";
    public static final String CALCULATE = "calculate";
    public static final String PERCENTAGE = "percentage";
    public static final String DISCOUNT_CALCULATOR = "discount_calculator";
    public static final String CUSTOMER_CONTACT_LOOKUP = "customer_contact_lookup";
    public static final String SESSION_SUMMARY = "session_summary";
    public static final String PRODUCT_LIST = "product_list";
    public static final String PRODUCT_LOOKUP = "product_lookup";
    public static final String PRODUCT_RESERVE = "product_reserve";
    public static final String TODO_ADD = "todo_add";
    public static final String TODO_LIST = "todo_list";
    public static final String TODO_COMPLETE = "todo_complete";

    private ToolNames() {
    }
}
