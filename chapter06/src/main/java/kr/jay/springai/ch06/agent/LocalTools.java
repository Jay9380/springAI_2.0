package kr.jay.springai.ch06.agent;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/** [6.4.3] 4장의 로컬 툴(날짜·계산)을 에이전트용으로 간단히 다시 둔 것. 프로세스 '안'의 능력. */
public class LocalTools {

    @Tool(description = "현재 날짜와 시각(서울)을 'yyyy-MM-dd HH:mm' 형식으로 돌려줍니다.")
    public String currentDateTime() {
        return LocalDateTime.now(ZoneId.of("Asia/Seoul")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }

    @Tool(description = "두 수를 더합니다. 합계 계산은 반드시 이 툴을 쓰세요.")
    public long add(@ToolParam(description = "첫 번째 수") long a, @ToolParam(description = "두 번째 수") long b) {
        return a + b;
    }
}
