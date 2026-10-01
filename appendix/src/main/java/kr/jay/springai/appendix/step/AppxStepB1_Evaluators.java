package kr.jay.springai.appendix.step;

import java.util.List;

import kr.jay.springai.appendix.eval.GroundedJudge;
import kr.jay.springai.appendix.eval.RagAnswerer;
import kr.jay.springai.appendix.support.RecordingChatModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.evaluation.FactCheckingEvaluator;
import org.springframework.ai.chat.evaluation.RelevancyEvaluator;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.evaluation.EvaluationRequest;
import org.springframework.ai.evaluation.EvaluationResponse;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * [부록 B 예제 B.3·B.4] 관련성 평가와 사실성 평가를 같은 답에 나란히 돌려 본다 (대화형 아님).
 *
 * <p>실행: {@code --spring.ai.cli.step=appx-eval}
 *
 * <p>네 가지 경우:
 * <ol>
 *   <li>RAG가 만든 정상 답</li>
 *   <li>책의 예: 주제는 맞지만 근거에 없는 주장("2025년에 개정")을 덧붙인 답 → 관련성 통과, 사실성 실패가 기대값</li>
 *   <li>질문과 무관한 답 (연차 얘기)</li>
 *   <li>근거 문서가 비어 있을 때 — 실패는 "답이 나쁘다"가 아니라 "근거가 없다"</li>
 * </ol>
 * 내장 평가기 두 개 + 커스텀 GroundedJudge를 비교하고, 내장 심판이 실제로 쓴 원문도 출력한다.
 */
@Component
@ConditionalOnProperty(prefix = "spring.ai.cli", name = "step", havingValue = "appx-eval")
public class AppxStepB1_Evaluators implements CommandLineRunner {

    static final String QUESTION = "사내 계정 비밀번호는 얼마나 자주 바꿔야 해?";

    private final RagAnswerer answerer;
    private final RecordingChatModel judgeModel;
    private final RelevancyEvaluator relevancy;
    private final FactCheckingEvaluator factChecking;
    private final GroundedJudge grounded;

    public AppxStepB1_Evaluators(ChatClient.Builder builder, ChatModel chatModel, VectorStore vectorStore) {
        this.answerer = new RagAnswerer(builder, vectorStore, 0.4);
        this.judgeModel = new RecordingChatModel(chatModel);                          // 심판 원문을 보려고 감쌈
        ChatClient.Builder judgeBuilder = ChatClient.builder(judgeModel);
        this.relevancy = RelevancyEvaluator.builder().chatClientBuilder(judgeBuilder).build();
        this.factChecking = FactCheckingEvaluator.builder(judgeBuilder).build();
        this.grounded = new GroundedJudge(ChatClient.builder(chatModel), 0.8);
    }

    @Override
    public void run(String... args) {
        RagAnswerer.RagResult rag = answerer.answer(QUESTION, "");
        System.out.println("질문: " + QUESTION);
        System.out.println("검색된 근거: " + rag.documents().stream().map(d -> d.getMetadata().get("source")).toList() + "\n");

        report("① RAG가 만든 답", rag.answer(), rag.documents());
        report("② 근거에 없는 주장을 덧붙인 답 (책의 예)", "3개월마다 변경해야 합니다. 이 정책은 2025년에 개정되었습니다.", rag.documents());
        report("③ 질문과 무관한 답", "연차는 입사 1년 차에 15일이 주어집니다.", rag.documents());
        report("④ 근거 문서 없이 평가", rag.answer(), List.of());
    }

    void report(String label, String answer, List<Document> docs) {
        var request = new EvaluationRequest(QUESTION, docs, answer);
        System.out.println("── " + label + "\n   답: " + answer.replace("\n", " "));
        EvaluationResponse r = relevancy.evaluate(request);
        System.out.printf("   Relevancy     pass=%-5s score=%.1f feedback='%s'  ← 심판 원문: %s%n",
                r.isPass(), r.getScore(), r.getFeedback(), quote(judgeModel.last()));
        EvaluationResponse f = factChecking.evaluate(request);
        System.out.printf("   FactChecking  pass=%-5s score=%.1f feedback='%s'  ← 심판 원문: %s%n",
                f.isPass(), f.getScore(), f.getFeedback(), quote(judgeModel.last()));
        EvaluationResponse g = grounded.evaluate(request);
        System.out.printf("   GroundedJudge pass=%-5s score=%.1f feedback='%s'%n%n", g.isPass(), g.getScore(), g.getFeedback());
    }

    static String quote(String s) {
        String one = s.replace("\n", "\\n");
        return "\"" + (one.length() > 60 ? one.substring(0, 60) + "…" : one) + "\"";
    }
}
