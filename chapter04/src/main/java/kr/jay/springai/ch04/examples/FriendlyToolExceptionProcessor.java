package kr.jay.springai.ch04.examples;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.execution.ToolExecutionException;
import org.springframework.ai.tool.execution.ToolExecutionExceptionProcessor;

/**
 * [4.4.2] 도구 실행 중 예외가 나면 무엇을 할까 — ToolExecutionExceptionProcessor.
 *
 * <p>선택지는 둘이다.
 * <ul>
 *   <li>메시지 문자열을 돌려준다 → 그 글이 도구 결과로 모델에게 가고, 모델이 사용자에게 설명한다 (여기서 택한 방식)</li>
 *   <li>예외를 다시 던진다 → 호출 전체가 실패하고 호출자가 처리한다</li>
 * </ul>
 * 원시 스택트레이스나 내부 클래스 이름을 모델(특히 외부 모델)에 보내지 않도록, 사용자에게 보여도 되는 문장으로 바꾼다.
 */
public class FriendlyToolExceptionProcessor implements ToolExecutionExceptionProcessor {

    private static final Logger log = LoggerFactory.getLogger(FriendlyToolExceptionProcessor.class);

    @Override
    public String process(ToolExecutionException exception) {
        String tool = exception.getToolDefinition().name();
        Throwable cause = exception.getCause() != null ? exception.getCause() : exception;
        log.warn("도구 실행 실패: {} — {}", tool, cause.toString());          // 원인은 로그에만 자세히
        return "도구 '" + tool + "' 실행에 실패했습니다: " + cause.getMessage()
                + " (입력값을 확인해 다시 요청하도록 사용자에게 안내하세요)";
    }
}
