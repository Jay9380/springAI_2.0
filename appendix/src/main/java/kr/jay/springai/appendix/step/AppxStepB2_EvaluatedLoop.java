package kr.jay.springai.appendix.step;

import kr.jay.springai.appendix.eval.EvaluatedAgentLoop;
import kr.jay.springai.appendix.eval.GroundedJudge;
import kr.jay.springai.appendix.eval.RagAnswerer;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.evaluation.RelevancyEvaluator;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * [부록 B 예제 B.5] 평가로 에이전트 루프 보강 — 같은 루프에 내장 평가기와 커스텀 평가기를 꽂아 비교한다 (대화형 아님).
 *
 * <p>실행: {@code --spring.ai.cli.step=appx-eval-loop}
 *
 * <p>질문은 근거 문서에 없는 정보(도입 연도)를 일부러 함께 묻는다. 모델이 그럴듯한 연도를 지어내면
 * <ul>
 *   <li>RelevancyEvaluator: 주제가 맞으니 통과시키기 쉽다. 실패해도 피드백이 빈 문자열이라 다음 시도는 '개선 지침 없는 재시도'</li>
 *   <li>GroundedJudge: 근거에 없는 주장을 집어 피드백으로 돌려준다 → 다음 시도가 그 주장을 빼고 "문서에 없다"고 답한다</li>
 * </ul>
 * 근거가 검색되지 않는 질문은 NO_CONTEXT로 따로 끝난다.
 *
 * <p><b>실측 결과 — 루프가 고칠 일이 없었다.</b> 신중한 생성자도, 일부러 "모르면 추정해서라도 채우라"고 지시한 과신형 생성자도
 * "문서에 도입 연도 정보가 없다"고 답해 1회차에 통과했다(2번 실행 모두). 이유는 QuestionAnswerAdvisor의 기본 템플릿이
 * 사용자 메시지 끝에 "not prior knowledge … If the answer is not in the context, inform the user that you can't answer"를 붙이기 때문이다
 * (2.0.1 소스). 메시지 끝의 지시가 시스템 프롬프트의 "추정하라"를 이겼다 — RAG 템플릿 자체가 1차 방어선이다.
 * 피드백으로 답을 고치는 경로는 가짜 모델 테스트(EvaluationTest)로 확인한다.
 */
@Component
@ConditionalOnProperty(prefix = "spring.ai.cli", name = "step", havingValue = "appx-eval-loop")
public class AppxStepB2_EvaluatedLoop implements CommandLineRunner {

    static final String QUESTION = "사내 비밀번호 변경 정책을 설명하고, 이 정책이 몇 년도에 처음 도입됐는지도 알려줘";
    static final String OFF_TOPIC = "회사 구내식당 이번 주 메뉴가 뭐야?";
    static final String EAGER = "당신은 사내 문서 도우미입니다. 질문의 모든 항목에 빠짐없이 구체적으로 답하세요. 모르는 부분은 그럴듯하게 추정해서라도 채우세요. 한국어로 두세 문장.";

    private final EvaluatedAgentLoop withBuiltIn;
    private final EvaluatedAgentLoop withGrounded;

    public AppxStepB2_EvaluatedLoop(ChatClient.Builder builder, ChatModel chatModel, VectorStore vectorStore) {
        RagAnswerer answerer = new RagAnswerer(builder, vectorStore, 0.55, EAGER);
        this.withBuiltIn = new EvaluatedAgentLoop(answerer,
                RelevancyEvaluator.builder().chatClientBuilder(ChatClient.builder(chatModel)).build(), 3);
        this.withGrounded = new EvaluatedAgentLoop(answerer, new GroundedJudge(ChatClient.builder(chatModel), 0.8), 3);
    }

    @Override
    public void run(String... args) {
        System.out.println("질문: " + QUESTION + "\n");
        print("RelevancyEvaluator(내장)", withBuiltIn.run(QUESTION));
        print("GroundedJudge(커스텀)", withGrounded.run(QUESTION));
        System.out.println("질문: " + OFF_TOPIC + "\n");
        print("GroundedJudge(커스텀)", withGrounded.run(OFF_TOPIC));
    }

    static void print(String label, EvaluatedAgentLoop.Outcome outcome) {
        System.out.println("── " + label + " → " + outcome.status());
        for (int i = 0; i < outcome.attempts().size(); i++) {
            var a = outcome.attempts().get(i);
            System.out.printf("   %d회차 pass=%s score=%.1f%n      답: %s%n      피드백: '%s'%n", i + 1, a.pass(), a.score(),
                    a.answer().replace("\n", " "), a.feedback());
        }
        if (outcome.attempts().isEmpty()) {
            System.out.println("   (평가 생략) 답: " + outcome.finalAnswer().replace("\n", " "));
        }
        System.out.println();
    }
}
