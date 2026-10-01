package kr.jay.springai.ch06.step;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import kr.jay.springai.ch06.workflow.ChainWorkflow;
import kr.jay.springai.ch06.workflow.EvaluatorOptimizer;
import kr.jay.springai.ch06.workflow.OrchestratorWorkers;
import kr.jay.springai.ch06.workflow.ParallelizationWorkflow;
import kr.jay.springai.ch06.workflow.RoutingWorkflow;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * [Step 1 · 6.1.2] 워크플로 패턴 5종을 차례로 실행해 본다 (대화형 아님).
 *
 * <p>실행: {@code --spring.ai.cli.step=ch6-workflows}  (특정 패턴만: {@code --ch6.pattern=routing})
 *
 * <p>다섯 패턴 모두 "LLM 호출 사이의 흐름을 <b>코드</b>가 정한다"는 점에서 워크플로다.
 * 자율 에이전트는 그 흐름을 LLM이 정한다(Step 2).
 */
@Component
@ConditionalOnProperty(prefix = "spring.ai.cli", name = "step", havingValue = "ch6-workflows")
public class Ch6Step1_Workflows implements CommandLineRunner {

    private final ChatClient chatClient;
    private final String pattern;

    public Ch6Step1_Workflows(ChatClient.Builder builder,
                              @org.springframework.beans.factory.annotation.Value("${ch6.pattern:all}") String pattern) {
        this.chatClient = builder.build();
        this.pattern = pattern;
    }

    @Override
    public void run(String... args) {
        if (on("chain")) chain();
        if (on("routing")) routing();
        if (on("parallel")) parallel();
        if (on("orchestrator")) orchestrator();
        if (on("evaluator")) evaluator();
    }

    private boolean on(String name) {
        return pattern.equals("all") || pattern.equals(name);
    }

    /**
     * ① 체이닝: 원문 → 수치만 추출 → 표준화 → 정렬. 1단계 게이트: 숫자가 하나도 없으면 멈춘다.
     * 실측: 4B 모델은 마지막 '정렬'을 틀렸다(92, 78, 87 …). 정렬처럼 정답이 정해진 일은 LLM 단계가 아니라 코드로 하는 게 맞다.
     */
    void chain() {
        title("① 프롬프트 체이닝");
        var workflow = new ChainWorkflow(chatClient, List.of(
                new ChainWorkflow.Step("추출", "입력에서 숫자 지표만 '값: 설명' 형식으로 한 줄씩 뽑으세요.",
                        out -> out.matches("(?s).*\\d.*")),                 // 게이트: 숫자가 있어야 다음 단계로
                ChainWorkflow.Step.of("표준화", "각 줄을 '백분율%: 설명' 형식으로 바꾸세요. 백분율이 아니면 그대로 두세요."),
                ChainWorkflow.Step.of("정렬", "값이 큰 순서로 정렬해서 줄만 출력하세요.")));
        var result = workflow.chain("""
                3분기 실적: 고객 만족도 92점, 매출 성장률 45%, 시장 점유율 23%, 고객 이탈률 5%,
                신규 고객 획득 비용 43달러, 제품 도입률 78%, 직원 만족도 87점.
                """);
        result.trace().forEach(t -> System.out.println("  " + t.replace("\n", " / ")));
        System.out.println("  완료=" + result.completed() + "\n");
    }

    /** ② 라우팅: 문의 3건을 분류해 각각 전문 프롬프트로. */
    void routing() {
        title("② 라우팅");
        Map<String, String> routes = Map.of(
                "billing", "당신은 결제 전문 상담원입니다. 환불·청구 절차를 단계별로 짧게 안내하세요.",
                "technical", "당신은 기술 지원 엔지니어입니다. 원인 후보와 확인 순서를 번호로 짧게 안내하세요.",
                "general", "당신은 친절한 일반 상담원입니다. 두 문장 이내로 답하세요.");
        var workflow = new RoutingWorkflow(chatClient);
        for (String ticket : List.of(
                "지난달 요금이 두 번 청구됐어요. 환불 받을 수 있나요?",
                "앱 로그인하면 500 에러가 나요. 어제부터 그래요.",
                "영업시간이 어떻게 되나요?")) {
            var r = workflow.route(ticket, routes, "general");
            System.out.println("  [" + r.route() + "] " + ticket + "\n    → " + oneLine(r.answer()));
        }
        System.out.println();
    }

    /** ③ 병렬화: 분할(이해관계자별) + 투표(3회 다수결). */
    void parallel() {
        title("③ 병렬화");
        var workflow = new ParallelizationWorkflow(chatClient);
        long start = System.currentTimeMillis();
        List<String> results = workflow.parallel(
                "회사가 '주 4일 근무제'를 도입합니다. 다음 이해관계자에게 미칠 영향을 두 문장으로 분석하세요.",
                List.of("고객", "직원", "투자자", "협력사"), 4);
        results.forEach(r -> System.out.println("  - " + oneLine(r)));
        System.out.println("  (4건 동시 실행: " + (System.currentTimeMillis() - start) + "ms)");
        boolean vulnerable = workflow.vote("""
                다음 코드에 SQL 인젝션 취약점이 있습니까?
                String q = "SELECT * FROM users WHERE name = '" + name + "'";
                """, 3);
        System.out.println("  투표(3회 다수결) SQL 인젝션 취약? " + vulnerable + "\n");
    }

    /** ④ 오케스트레이터-워커: 몇 개로 쪼갤지 LLM이 정한다. */
    void orchestrator() {
        title("④ 오케스트레이터-워커");
        var result = new OrchestratorWorkers(chatClient, 3)
                .process("친환경 물병의 제품 설명 문구를 작성하세요.");
        System.out.println("  분석: " + oneLine(result.analysis()));
        for (int i = 0; i < result.subtasks().size(); i++) {
            System.out.println("  워커[" + result.subtasks().get(i).type() + "] " + oneLine(result.workerResponses().get(i)));
        }
        System.out.println();
    }

    /** ⑤ 평가자-최적화자: 요구 사항이 까다로운 글을 통과할 때까지 (최대 3회). */
    void evaluator() {
        title("⑤ 평가자-최적화자");
        // 기계적 규칙은 코드가 검사한다 (LLM 평가자만 두었을 때 26자짜리 제목이 PASS를 받았다)
        Function<String, String> rules = title -> {
            String t = title.strip();
            List<String> v = new ArrayList<>();
            // 피드백은 '행동 가능하게': 4B 모델은 글자 수를 셀 줄 모른다(토큰 단위로 보기 때문). 몇 자를 줄일지 코드가 알려 준다
            if (t.length() > 20) v.add("현재 " + t.length() + "자 → " + (t.length() - 20) + "자 이상 줄일 것 (불필요한 단어를 빼라)");
            if (!t.contains("보안 교육")) v.add("'보안 교육'을 그대로 포함할 것");
            // qwen은 숫자와 한글 단위 사이에 공백을 넣는 경향이 있다("10 월", "9 시"). 의미가 같으니 공백을 지우고 비교한다
            if (!t.replace(" ", "").contains("10월15일")) v.add("마감일 '10월 15일'을 포함할 것");
            if (t.contains("!")) v.add("느낌표를 지울 것");
            return v.isEmpty() ? null : String.join(", ", v);
        };
        var result = new EvaluatorOptimizer(chatClient, 4, rules).loop("""
                사내 공지 제목을 하나 쓰세요. 요구 사항:
                1) 20자 이내 2) '보안 교육'이라는 단어 포함 3) 마감일 '10월 15일' 포함 4) 느낌표 금지
                """);
        for (int i = 0; i < result.chainOfThought().size(); i++) {
            System.out.println("  " + (i + 1) + "회차: " + oneLine(result.chainOfThought().get(i).response()));
            System.out.println("        └ " + oneLine(result.feedbacks().get(i)));
        }
        System.out.println("  통과=" + result.passed() + ", 반복=" + result.iterations() + "\n");
    }

    private static void title(String t) {
        System.out.println("── " + t);
    }

    private static String oneLine(String s) {
        return s == null ? "" : s.replace("\n", " ").strip();
    }
}
