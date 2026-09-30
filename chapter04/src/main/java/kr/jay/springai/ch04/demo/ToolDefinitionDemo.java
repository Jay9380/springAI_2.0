package kr.jay.springai.ch04.demo;

import kr.jay.springai.ch04.examples.CalculatorTools;
import kr.jay.springai.ch04.examples.Chapter4ToolCallbacks;
import kr.jay.springai.ch04.examples.DateTimeTools;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.execution.DefaultToolCallResultConverter;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * [4.2.3] 툴 명세(ToolDefinition) — 모델에게 실제로 전달되는 '도구 설명서'를 출력한다.
 *
 * <p>실행: {@code --spring.ai.cli.step=ch4-demo-definitions}  (모델 호출 없음)
 *
 * <p>모델은 도구의 자바 코드를 보지 못한다. 보는 것은 이것뿐이다:
 * <pre>
 *   name        : 무엇이라 부를지
 *   description : 언제 쓸지 (모델의 판단 근거 — '두 번째 프롬프트')
 *   inputSchema : 어떤 인자를 어떤 형태로 넣을지 (JSON 스키마)
 * </pre>
 * 도구 선택이 엉뚱하면 코드보다 이 세 가지를 먼저 의심한다.
 */
@Component
@ConditionalOnProperty(prefix = "spring.ai.cli", name = "step", havingValue = "ch4-demo-definitions")
public class ToolDefinitionDemo implements CommandLineRunner {

    private final DateTimeTools dateTimeTools;
    private final CalculatorTools calculatorTools;

    public ToolDefinitionDemo(DateTimeTools dateTimeTools, CalculatorTools calculatorTools) {
        this.dateTimeTools = dateTimeTools;
        this.calculatorTools = calculatorTools;
    }

    @Override
    public void run(String... args) {
        // @Tool 객체 → ToolCallback 배열 (ChatModel에 직접 넘길 때 쓰는 방법, 예제 4.19)
        for (ToolCallback cb : ToolCallbacks.from(dateTimeTools, calculatorTools)) {
            print(cb);
        }
        print(Chapter4ToolCallbacks.discountCalculator());

        // [4.2.5] 결과 변환: String을 반환하는 도구는 결과가 '따옴표로 감싼 JSON 문자열'이 된다
        var converter = new DefaultToolCallResultConverter();
        System.out.println("── 결과 변환 (DefaultToolCallResultConverter)");
        System.out.println("  String \"맑음, 3도\" → " + converter.convert("맑음, 3도", String.class));
        System.out.println("  double 28700.0    → " + converter.convert(28700.0, double.class));
        System.out.println("  void              → " + converter.convert(null, void.class));
        System.out.println("  null              → " + converter.convert(null, Object.class));
    }

    private static void print(ToolCallback cb) {
        var def = cb.getToolDefinition();
        System.out.println("── " + def.name() + "  (returnDirect=" + cb.getToolMetadata().returnDirect() + ")");
        System.out.println("  description : " + def.description());
        System.out.println("  inputSchema : " + def.inputSchema().replace("\n", "\n                "));
        System.out.println();
    }
}
