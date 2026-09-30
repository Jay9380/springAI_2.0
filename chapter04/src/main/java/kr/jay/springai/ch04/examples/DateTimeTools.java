package kr.jay.springai.ch04.examples;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.jspecify.annotations.Nullable;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * [4.3.1] 메서드를 툴로 — 선언적 방식 @Tool.
 *
 * <p>LLM은 '지금 몇 시인지' 모른다(학습 시점에 멈춰 있다). 그래서 날짜·시간은 도구로 알려 줘야 하는 대표적인 정보다.
 *
 * <p>@Tool만 붙인다고 자동 등록되지 않는다(책 p.297). 도구는 모델에게 주는 '실행 권한'이라서,
 * 어떤 요청에 어떤 도구를 노출할지 개발자가 {@code defaultTools(...)}/{@code tools(...)}로 명시한다.
 */
@Component
public class DateTimeTools {

    /**
     * 선택 파라미터 예. required=false(+ @Nullable)면 JSON 스키마의 required 목록에서 빠진다.
     * 필수로 두면 사용자가 시간대를 말하지 않았을 때 모델이 그럴듯한 값을 '지어내' 채운다(책 p.287).
     */
    @Tool(name = ToolNames.CURRENT_DATETIME,
          description = "지정한 시간대(기본: Asia/Seoul)의 현재 날짜와 시각을 ISO-8601 형식으로 반환합니다.")
    public String currentDateTime(
            @Nullable @ToolParam(description = "IANA 시간대 ID (예: Asia/Seoul, Europe/London)", required = false)
            String zoneId) {
        ZoneId zone = (zoneId == null || zoneId.isBlank()) ? ZoneId.of("Asia/Seoul") : ZoneId.of(zoneId);
        return ZonedDateTime.now(zone).withNano(0).toString();
    }

    @Tool(name = ToolNames.CURRENT_DATE, description = "오늘 날짜(Asia/Seoul 기준)를 yyyy-MM-dd로 반환합니다.")
    public String currentDate() {
        return LocalDate.now(ZoneId.of("Asia/Seoul")).toString();
    }
}
