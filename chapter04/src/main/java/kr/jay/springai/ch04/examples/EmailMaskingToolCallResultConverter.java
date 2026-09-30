package kr.jay.springai.ch04.examples;

import java.lang.reflect.Type;

import org.jspecify.annotations.Nullable;
import org.springframework.ai.tool.execution.DefaultToolCallResultConverter;
import org.springframework.ai.tool.execution.ToolCallResultConverter;

/**
 * [4.2.5 예제 4.12] 도구 결과를 모델에게 보내기 '전에' 이메일을 가린다.
 *
 * <p>도구 결과는 모델에게 가는 프롬프트의 일부가 된다(tool 메시지). 외부 모델을 쓴다면 고객 이메일이 그대로
 * 밖으로 나간다. 결과 변환기는 그 경계에서 민감 정보를 걸러 내는 자리다.
 *
 * <p>기본 변환기(DefaultToolCallResultConverter)로 먼저 JSON 문자열을 만든 뒤(위임), 그 결과를 한 번 더 가공한다.
 */
public class EmailMaskingToolCallResultConverter implements ToolCallResultConverter {

    private final ToolCallResultConverter delegate = new DefaultToolCallResultConverter();

    @Override
    public String convert(@Nullable Object result, @Nullable Type returnType) {
        return delegate.convert(result, returnType)
                .replaceAll("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}", "[EMAIL]");
    }
}
