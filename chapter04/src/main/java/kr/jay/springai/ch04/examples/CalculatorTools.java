package kr.jay.springai.ch04.examples;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * [4.3.1] 계산 도구 — LLM에게 맡기면 틀리는 일을 코드로.
 *
 * <p>LLM은 다음 토큰을 확률로 고르는 기계라 "37 × 89" 같은 계산을 종종 틀린다.
 * 계산은 도구로 빼고 모델은 '어떤 계산을 할지'만 고르게 하는 것이 올바른 분업이다.
 *
 * <p>@ToolParam 설명에 허용 값·형식을 구체적으로 적으면 모델이 잘못된 인자를 넘길 확률이 크게 줄어든다.
 * 스프링 AI는 자바 파라미터 이름(a, operator, b)을 JSON 스키마의 키로 쓴다 — 그래서 -parameters 컴파일 옵션이 필요하다
 * (spring-boot-starter-parent가 기본으로 켜 준다).
 */
@Component
public class CalculatorTools {

    @Tool(name = ToolNames.CALCULATE, description = "두 수에 사칙연산을 적용한 결과를 반환합니다.")
    public double calculate(
            @ToolParam(description = "첫 번째 수") double a,
            @ToolParam(description = "연산자. +, -, *, / 중 하나") String operator,
            @ToolParam(description = "두 번째 수") double b) {
        return switch (operator.trim()) {
            case "+" -> a + b;
            case "-" -> a - b;
            case "*", "x", "×" -> a * b;
            case "/", "÷" -> {
                if (b == 0) {
                    // 예외는 ToolExecutionExceptionProcessor가 모델에게 전할 메시지로 바꾼다 (Step4)
                    throw new IllegalArgumentException("0으로 나눌 수 없습니다.");
                }
                yield a / b;
            }
            default -> throw new IllegalArgumentException("지원하지 않는 연산자: " + operator);
        };
    }

    @Tool(name = ToolNames.PERCENTAGE, description = "값의 퍼센트(%)를 계산합니다. 예: 200의 15% = 30")
    public double percentage(
            @ToolParam(description = "기준 값") double value,
            @ToolParam(description = "퍼센트 수치 (예: 15는 15%)") double percent) {
        return value * percent / 100.0;
    }
}
