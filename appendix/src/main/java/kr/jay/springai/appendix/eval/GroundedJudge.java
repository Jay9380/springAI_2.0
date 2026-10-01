package kr.jay.springai.appendix.eval;

import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.evaluation.EvaluationRequest;
import org.springframework.ai.evaluation.EvaluationResponse;
import org.springframework.ai.evaluation.Evaluator;

/**
 * [부록 B 보강 — 6장-부록 노트 2.5] 피드백과 중간 점수를 돌려주는 커스텀 평가기.
 *
 * <p>2.0.1 내장 평가기는 (소스로 확인)
 * <ul>
 *   <li>RelevancyEvaluator: 심판 응답이 정확히 "yes"일 때만 통과("Yes." "예"는 실패), 점수는 0 또는 1, <b>피드백은 항상 빈 문자열</b></li>
 *   <li>FactCheckingEvaluator: 통과해도 <b>점수는 항상 0</b>(3-인자 생성자), 피드백 빈 문자열</li>
 * </ul>
 * 그래서 책 예제 B.5처럼 "실패하면 피드백을 다음 시도에 넣는" 루프에 내장 평가기를 꽂으면 개선 지침 없는 재시도가 된다.
 *
 * <p>이 평가기는 구조화 출력(네이티브, 2장)으로 판정·점수·이유를 한 번에 받는다 — 문자열 "yes" 비교의 취약성이 없고,
 * 점수에 최소 통과 기준({@code minPassScore})을 걸 수 있다. 심판은 생성 모델과 다른(더 안정적인) 모델을 쓰는 게 좋다(책 p.659).
 *
 * <p><b>근거가 없으면 심판에게 묻지 않는다(실측).</b> 근거 문서 목록을 비운 채 평가하자 qwen3.5:4b 심판이 이 평가기에서 score 1.0으로,
 * 내장 FactCheckingEvaluator에서 "yes"로 <b>거짓 통과</b>시켰다(외부 지식으로 판정한 것). 빈 근거 판정은 코드가 막는다 — status=NO_CONTEXT.
 */
public class GroundedJudge implements Evaluator {

    /** 심판의 판정. unsupportedClaims는 근거에 없는 주장들 — 피드백의 핵심. */
    public record Verdict(double score, String unsupportedClaims, String feedback) {
    }

    static final String SYSTEM = """
            당신은 엄격한 검토자입니다. 답변을 '근거 문서'에만 대조해 판정합니다. 상식이나 외부 지식으로 보완하지 않습니다.
            - score: 1.0 = 모든 주장이 근거 문서로 뒷받침됨, 0.5 = 일부만, 0.0 = 뒷받침되지 않거나 질문과 무관
            - unsupportedClaims: 근거 문서에 없는 주장을 그대로 나열 (없으면 빈 문자열)
            - feedback: 답변을 어떻게 고쳐야 하는지 한국어로 구체적으로 (통과면 빈 문자열)
            """;

    private final ChatClient judge;
    private final double minPassScore;

    public GroundedJudge(ChatClient.Builder judgeBuilder, double minPassScore) {
        this.judge = judgeBuilder.clone().defaultSystem(SYSTEM).build();
        this.minPassScore = minPassScore;
    }

    @Override
    public EvaluationResponse evaluate(EvaluationRequest request) {
        if (doGetSupportingData(request).isBlank()) {
            return new EvaluationResponse(false, 0f, "근거 문서가 없어 평가할 수 없습니다 (재검색하거나 질문을 좁혀야 함).",
                    Map.of("status", "NO_CONTEXT"));
        }
        Verdict v = judge.prompt()
                .user(u -> u.text("질문: {q}\n\n근거 문서:\n{d}\n\n답변:\n{a}")
                        .param("q", request.getUserText())
                        .param("d", doGetSupportingData(request))
                        .param("a", request.getResponseContent()))
                .call()
                .entity(Verdict.class, spec -> spec.useProviderStructuredOutput());
        boolean pass = v.score() >= minPassScore && (v.unsupportedClaims() == null || v.unsupportedClaims().isBlank());
        String feedback = pass ? "" : feedback(v);
        return new EvaluationResponse(pass, (float) v.score(), feedback,
                Map.of("status", "EVALUATED", "unsupportedClaims", nz(v.unsupportedClaims())));
    }

    static String feedback(Verdict v) {
        String claims = nz(v.unsupportedClaims()).isBlank() ? "" : "근거에 없는 주장: " + v.unsupportedClaims() + " / ";
        return (claims + "고칠 방법: " + nz(v.feedback())).strip();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
