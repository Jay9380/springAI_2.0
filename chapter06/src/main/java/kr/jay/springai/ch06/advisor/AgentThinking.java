package kr.jay.springai.ch06.advisor;

import org.springframework.ai.tool.annotation.ToolParam;

/**
 * [6.3.4 예제 6.14] 파라미터 증강 — 모든 툴 스키마에 '끼워 넣을' 추가 인자.
 *
 * <p>모델은 원래 툴 스키마에 이 두 필드가 더해진 확장 스키마를 본다. 모델이 채워 보낸 값은
 * argumentConsumer가 가로채 로그로 남기고, 원래 툴에는 원래 인자만 전달된다. 비즈니스 코드는 한 줄도 안 바뀐다.
 */
public record AgentThinking(
        @ToolParam(description = "이 툴을 호출하기로 결정한 논리적인 추론 과정", required = true)
        String innerThought,
        @ToolParam(description = "추론의 신뢰도 수준 (low, medium, high)", required = false)
        String confidence) {
}
