package kr.jay.springai.appendix.eval;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.evaluation.EvaluationRequest;
import org.springframework.ai.evaluation.EvaluationResponse;
import org.springframework.ai.evaluation.Evaluator;

/**
 * [부록 B 예제 B.5] 평가로 에이전트 루프 보강 — 생성 → 평가 → (실패면) 피드백을 실어 재생성.
 *
 * <p>책 예제에 더한 안전장치(책 p.661이 권하는 것들):
 * <ul>
 *   <li>최대 시도 횟수 — 넘으면 마지막 답과 함께 '미통과'로 끝낸다.</li>
 *   <li>근거 없음(NO_CONTEXT) 분리 — 검색된 문서가 없으면 평가하지 않는다. 심판은 빈 근거와 비교해 거의 항상 실패를 내는데,
 *       그건 "답이 나쁘다"가 아니라 "근거가 없었다"는 뜻이라 섞어 집계하면 품질 지표가 왜곡된다(노트 2.6).</li>
 * </ul>
 */
public class EvaluatedAgentLoop {

    public enum Status { PASSED, FAILED_AFTER_RETRIES, NO_CONTEXT }

    public record Attempt(String answer, boolean pass, float score, String feedback) {
    }

    public record Outcome(Status status, String finalAnswer, List<Attempt> attempts) {
    }

    private final RagAnswerer answerer;
    private final Evaluator evaluator;
    private final int maxAttempts;

    public EvaluatedAgentLoop(RagAnswerer answerer, Evaluator evaluator, int maxAttempts) {
        this.answerer = answerer;
        this.evaluator = evaluator;
        this.maxAttempts = maxAttempts;
    }

    public Outcome run(String question) {
        List<Attempt> attempts = new ArrayList<>();
        String instruction = "";
        RagAnswerer.RagResult result = null;
        for (int i = 1; i <= maxAttempts; i++) {
            result = answerer.answer(question, instruction);
            if (result.documents().isEmpty()) {
                return new Outcome(Status.NO_CONTEXT, result.answer(), attempts);
            }
            EvaluationResponse verdict = evaluator.evaluate(new EvaluationRequest(question, result.documents(), result.answer()));
            attempts.add(new Attempt(result.answer(), verdict.isPass(), verdict.getScore(), verdict.getFeedback()));
            if (verdict.isPass()) {
                return new Outcome(Status.PASSED, result.answer(), attempts);
            }
            instruction = verdict.getFeedback();                      // 내장 평가기라면 여기가 항상 빈 문자열이다
        }
        return new Outcome(Status.FAILED_AFTER_RETRIES, result.answer(), attempts);
    }
}
