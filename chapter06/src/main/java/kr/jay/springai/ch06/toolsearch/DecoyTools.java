package kr.jay.springai.ch06.toolsearch;

import java.util.List;
import java.util.Map;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

/**
 * [6.4.5 표 6.13 재현] 질문과 무관한 툴 25개. 책의 측정 조건(관련 툴 3개 + 무관한 툴 25개)을 흉내 낸다.
 *
 * <p>툴 정의(이름·설명·JSON 스키마)는 <b>매 요청마다</b> 모델에게 전달된다. 무관한 툴이 많을수록
 * (1) 입력 토큰이 늘고 (2) 비슷한 툴 사이에서 선택을 틀릴 확률이 오른다. 툴 검색은 이 둘을 줄이려는 패턴이다.
 */
public final class DecoyTools {

    private DecoyTools() {
    }

    record Input(String value) {
    }

    private static final Map<String, String> DECOYS = Map.ofEntries(
            Map.entry("sendSlackMessage", "슬랙 채널에 메시지를 보냅니다."),
            Map.entry("createJiraTicket", "지라에 이슈 티켓을 만듭니다."),
            Map.entry("searchConfluence", "컨플루언스 문서를 검색합니다."),
            Map.entry("bookMeetingRoom", "회의실을 예약합니다."),
            Map.entry("translateText", "문장을 다른 언어로 번역합니다."),
            Map.entry("getWeather", "도시의 현재 날씨를 조회합니다."),
            Map.entry("convertCurrency", "통화를 환율로 변환합니다."),
            Map.entry("sendEmail", "이메일을 보냅니다."),
            Map.entry("listCalendarEvents", "캘린더 일정을 조회합니다."),
            Map.entry("createPullRequest", "깃허브 풀 리퀘스트를 만듭니다."),
            Map.entry("runSqlQuery", "분석용 데이터베이스에 SQL을 실행합니다."),
            Map.entry("getEmployeeProfile", "직원 인사 정보를 조회합니다."),
            Map.entry("approveExpense", "경비 청구를 승인합니다."),
            Map.entry("orderOfficeSupplies", "사무용품을 주문합니다."),
            Map.entry("checkVpnStatus", "VPN 연결 상태를 확인합니다."),
            Map.entry("resetPassword", "사내 계정 비밀번호를 초기화합니다."),
            Map.entry("summarizeMeeting", "회의록을 요약합니다."),
            Map.entry("getStockPrice", "상장 주식의 현재 주가를 조회합니다."),
            Map.entry("trackParcel", "택배 배송 위치를 조회합니다."),
            Map.entry("reserveTrain", "기차표를 예매합니다."),
            Map.entry("findRestaurant", "주변 식당을 찾습니다."),
            Map.entry("createSurvey", "설문 조사를 만듭니다."),
            Map.entry("scanVirus", "파일의 악성코드를 검사합니다."),
            Map.entry("generateInvoice", "거래처 청구서를 생성합니다."),
            Map.entry("checkParkingSpot", "주차 가능 자리를 확인합니다."));

    public static List<ToolCallback> all() {
        return DECOYS.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> (ToolCallback) FunctionToolCallback.builder(e.getKey(), (Input in) -> "ok")
                        .description(e.getValue())
                        .inputType(Input.class)
                        .build())
                .toList();
    }
}
