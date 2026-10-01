package kr.jay.springai.ch06.workflow;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.springframework.ai.chat.client.ChatClient;

/**
 * [6.1.2 ⑤ 예제 6.5] 평가자-최적화자 — 생성하고, 평가하고, 통과할 때까지 개선.
 *
 * <p>생성자와 평가자를 분리하는 게 핵심이다. 자기 글을 스스로 고치라고 하면 잘 못 고치지만,
 * "평가자가 남긴 구체적인 피드백"을 붙여 다시 쓰게 하면 잘 고친다.
 *
 * <p><b>책 예제와 다른 점:</b> 책은 {@code do { ... } while (!pass)} 라서 평가를 영원히 통과 못 하면 영원히 돈다.
 * 실무에서는 반드시 상한({@code maxIterations})을 두고, 상한에 걸리면 마지막 결과와 함께 "미통과"를 알린다.
 * 루프를 멈추는 건 LLM의 판단이 아니라 코드여야 한다 — 6.3의 안전 가드와 같은 원리.
 *
 * <p><b>실측에서 얻은 두 번째 수정 — 코드 검사(codeCheck).</b> qwen3.5:4b 평가자는 "20자 이내, '10월 15일' 포함"을 어긴
 * "보안 교육 필수 이수 안내 (마감 10 월 15 일)"(26자, 날짜 띄어쓰기)를 PASS로 판정했다. LLM 평가자는 글자 수·포함 여부 같은
 * <b>기계적 요구 사항에 관대하다.</b> 그래서 코드로 확인할 수 있는 규칙은 코드가 먼저 검사하고(위반 시 그 내용이 곧 피드백),
 * 코드로 못 보는 품질(어조, 자연스러움)만 LLM에게 맡긴다. 6.2의 '집행 영역'을 평가 루프에 적용한 것이다.
 */
public class EvaluatorOptimizer {

    public enum Evaluation { PASS, NEEDS_IMPROVEMENT, FAIL }

    public record Generation(String thoughts, String response) {
    }

    public record EvaluationResponse(Evaluation evaluation, String feedback) {
    }

    /** chainOfThought = 매 회차 생성물, feedbacks = 매 회차 평가 결과. passed=false면 상한에 걸려 멈춘 것. */
    public record RefinedResponse(String solution, List<Generation> chainOfThought, List<String> feedbacks,
                                  boolean passed, int iterations) {
    }

    private final ChatClient chatClient;
    private final int maxIterations;
    private final Function<String, String> codeCheck;

    public EvaluatorOptimizer(ChatClient chatClient, int maxIterations) {
        this(chatClient, maxIterations, content -> null);
    }

    /** @param codeCheck 결과물을 받아 위반 내용을 돌려준다. 위반이 없으면 null. */
    public EvaluatorOptimizer(ChatClient chatClient, int maxIterations, Function<String, String> codeCheck) {
        this.chatClient = chatClient;
        this.maxIterations = maxIterations;
        this.codeCheck = codeCheck;
    }

    public RefinedResponse loop(String task) {
        List<Generation> history = new ArrayList<>();
        List<String> feedbacks = new ArrayList<>();
        String context = "";
        for (int i = 1; i <= maxIterations; i++) {
            Generation g = generate(task, context);
            history.add(g);
            String violation = codeCheck.apply(g.response());
            EvaluationResponse eval = violation != null
                    ? new EvaluationResponse(Evaluation.NEEDS_IMPROVEMENT, violation)   // 코드가 잡으면 LLM 평가는 생략
                    : evaluate(g.response(), task);
            feedbacks.add(eval.evaluation() + (violation != null ? " (코드 검사) " : " (LLM 평가) ") + eval.feedback());
            if (eval.evaluation() == Evaluation.PASS) {
                return new RefinedResponse(g.response(), history, feedbacks, true, i);
            }
            // 다음 회차 입력 = 지금까지의 모든 시도 + 각 피드백 (책 예제 6.5처럼 누적 — 같은 실수를 반복하지 않게)
            context += "\n이전 시도 " + i + ": " + g.response() + "\n  → 피드백: " + eval.feedback();
        }
        return new RefinedResponse(history.getLast().response(), history, feedbacks, false, maxIterations);
    }

    Generation generate(String task, String context) {
        return chatClient.prompt()
                .user("""
                        작업을 수행하세요. 이전 시도와 피드백이 있으면 반영해서 개선하세요.
                        thoughts에는 무엇을 고쳤는지, response에는 결과물만 담으세요.

                        작업: %s
                        %s
                        """.formatted(task, context))
                .call()
                .entity(Generation.class, spec -> spec.useProviderStructuredOutput());
    }

    EvaluationResponse evaluate(String content, String task) {
        return chatClient.prompt()
                .user("""
                        다음 결과물이 작업 요구 사항을 '모두' 지켰는지 엄격하게 평가하세요.
                        모두 지켰으면 PASS, 하나라도 어겼으면 NEEDS_IMPROVEMENT, 완전히 틀렸으면 FAIL.
                        feedback에는 어긴 요구 사항과 고칠 방법을 구체적으로 쓰세요.

                        작업: %s
                        결과물: %s
                        """.formatted(task, content))
                .call()
                .entity(EvaluationResponse.class, spec -> spec.useProviderStructuredOutput());
    }
}
