package kr.jay.springai.appendix;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import kr.jay.springai.appendix.eval.EvaluatedAgentLoop;
import kr.jay.springai.appendix.eval.GroundedJudge;
import kr.jay.springai.appendix.eval.RagAnswerer;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.evaluation.FactCheckingEvaluator;
import org.springframework.ai.chat.evaluation.RelevancyEvaluator;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.evaluation.EvaluationRequest;
import org.springframework.ai.evaluation.EvaluationResponse;
import org.springframework.ai.evaluation.Evaluator;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

/** 부록 B — 평가기의 판정 규칙을 '심판이 이렇게 답하면'으로 고정한다. 소스로 확인한 2.0.1 동작이 그대로인지 지키는 테스트. */
class EvaluationTest {

    /** 매번 같은 답을 하는 가짜 심판. 받은 프롬프트를 기록한다. */
    static final class FakeJudge implements ChatModel {
        final List<String> prompts = new ArrayList<>();
        final Function<String, String> reply;

        FakeJudge(Function<String, String> reply) {
            this.reply = reply;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            prompts.add(prompt.getContents());
            return new ChatResponse(List.of(new Generation(new AssistantMessage(reply.apply(prompt.getContents())))));
        }
    }

    static final List<Document> DOCS = List.of(new Document("모든 임직원은 3개월마다 비밀번호를 변경해야 한다."));

    static EvaluationResponse relevancy(String judgeSays) {
        return RelevancyEvaluator.builder().chatClientBuilder(ChatClient.builder(new FakeJudge(p -> judgeSays))).build()
                .evaluate(new EvaluationRequest("얼마나 자주?", DOCS, "3개월마다"));
    }

    @Test
    void relevancy_passesOnlyOnExactYes_scoreIsZeroOrOne_feedbackAlwaysEmpty() {
        assertThat(relevancy("yes").isPass()).isTrue();
        assertThat(relevancy(" YES \n").isPass()).isTrue();                        // 앞뒤 공백·대소문자는 괜찮다
        assertThat(relevancy("yes").getScore()).isEqualTo(1f);
        // 내용은 '예'인데 형식 때문에 실패하는 경우들 (말이 많은 모델일수록 거짓 실패)
        assertThat(relevancy("Yes.").isPass()).isFalse();
        assertThat(relevancy("YES - the response matches").isPass()).isFalse();
        assertThat(relevancy("예").isPass()).isFalse();
        assertThat(relevancy("Yes.").getScore()).isZero();
        assertThat(relevancy("no, because ...").getFeedback()).isEmpty();          // 심판의 이유는 버려진다
    }

    @Test
    void factChecking_passHasScoreZero_andIgnoresTheQuestion() {
        FakeJudge judge = new FakeJudge(p -> "yes");
        EvaluationResponse r = FactCheckingEvaluator.builder(ChatClient.builder(judge)).build()
                .evaluate(new EvaluationRequest("이 질문은 쓰이지 않는다", DOCS, "3개월마다 바꾼다"));

        assertThat(r.isPass()).isTrue();
        assertThat(r.getScore()).isZero();                                         // 통과해도 0 → 평균 점수로 집계하면 안 된다
        assertThat(judge.prompts.getFirst()).contains("3개월마다 바꾼다").contains("3개월마다 비밀번호")
                .doesNotContain("이 질문은 쓰이지 않는다");                           // 질문을 보지 않는다 → 맞지만 엉뚱한 답도 통과
    }

    @Test
    void factChecking_documentsGivenAsUserText_leaveTheDocumentEmpty() {
        // 근거를 dataList가 아니라 userText에 넣는 실수 (A-RMS 평가 API가 실제로 겪은 일, 노트 3.2)
        FakeJudge judge = new FakeJudge(p -> "no");
        FactCheckingEvaluator.builder(ChatClient.builder(judge)).build()
                .evaluate(new EvaluationRequest("모든 임직원은 3개월마다 비밀번호를 변경해야 한다.", "3개월마다 바꾼다"));
        assertThat(judge.prompts.getFirst()).doesNotContain("모든 임직원은");        // 심판은 빈 문서와 비교했다
    }

    static String verdict(double score, String claims, String feedback) {
        return "{\"score\":%s,\"unsupportedClaims\":\"%s\",\"feedback\":\"%s\"}".formatted(score, claims, feedback);
    }

    @Test
    void groundedJudge_returnsScoreAndFeedback_andEnforcesMinScore() {
        var judge = new GroundedJudge(ChatClient.builder(new FakeJudge(p -> verdict(0.5, "2025년에 개정되었다", "연도 주장을 빼라"))), 0.8);
        EvaluationResponse r = judge.evaluate(new EvaluationRequest("q", DOCS, "3개월마다, 2025년 개정"));
        assertThat(r.isPass()).isFalse();
        assertThat(r.getScore()).isEqualTo(0.5f);
        assertThat(r.getFeedback()).contains("2025년에 개정되었다").contains("연도 주장을 빼라");

        var lenientScore = new GroundedJudge(ChatClient.builder(new FakeJudge(p -> verdict(0.9, "", ""))), 0.8);
        assertThat(lenientScore.evaluate(new EvaluationRequest("q", DOCS, "a")).isPass()).isTrue();
        var claimsButHighScore = new GroundedJudge(ChatClient.builder(new FakeJudge(p -> verdict(0.9, "지어낸 주장", "빼라"))), 0.8);
        assertThat(claimsButHighScore.evaluate(new EvaluationRequest("q", DOCS, "a")).isPass()).isFalse();   // 근거 없는 주장이 있으면 점수와 무관하게 실패
    }

    @Test
    void groundedJudge_noDocuments_neverAsksTheJudge() {
        // 실측: 근거가 빈 채로 물으면 4B 심판이 score 1.0으로 거짓 통과시켰다
        FakeJudge judge = new FakeJudge(p -> verdict(1.0, "", ""));
        EvaluationResponse r = new GroundedJudge(ChatClient.builder(judge), 0.8).evaluate(new EvaluationRequest("q", List.of(), "a"));
        assertThat(r.isPass()).isFalse();
        assertThat(r.getMetadata()).containsEntry("status", "NO_CONTEXT");
        assertThat(judge.prompts).isEmpty();
    }

    /** 첫 답은 근거 없는 주장을 넣고, 개선 지침을 받으면 고치는 가짜 생성 모델 + 고정 검색 결과. */
    static RagAnswerer answerer(List<String> userMessagesSeen) {
        ChatModel generator = prompt -> {
            String user = prompt.getUserMessage().getText();
            userMessagesSeen.add(user);
            String text = user.contains("[개선 지침]") ? "3개월마다 바꿉니다. 도입 연도는 문서에 없습니다." : "3개월마다 바꿉니다. 2019년에 도입됐습니다.";
            return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
        };
        VectorStore store = org.mockito.Mockito.mock(VectorStore.class);
        org.mockito.Mockito.when(store.similaritySearch(org.mockito.ArgumentMatchers.any(SearchRequest.class))).thenReturn(DOCS);
        return new RagAnswerer(ChatClient.builder(generator), store, 0.0);
    }

    static final Evaluator CATCHES_YEAR = req -> req.getResponseContent().contains("2019")
            ? new EvaluationResponse(false, 0.5f, "근거에 없는 주장: 2019년 도입", Map.of())
            : new EvaluationResponse(true, 1f, "", Map.of());

    @Test
    void loop_feedsFeedbackIntoNextAttempt_andPasses() {
        List<String> seen = new ArrayList<>();
        var outcome = new EvaluatedAgentLoop(answerer(seen), CATCHES_YEAR, 3).run("정책과 도입 연도는?");

        assertThat(outcome.status()).isEqualTo(EvaluatedAgentLoop.Status.PASSED);
        assertThat(outcome.attempts()).hasSize(2);
        assertThat(seen.get(1)).contains("[개선 지침]").contains("2019년 도입");      // 피드백이 다음 입력으로
        assertThat(outcome.finalAnswer()).contains("문서에 없습니다");
    }

    @Test
    void loop_withEmptyFeedback_isJustARetry_andStopsAtLimit() {
        // 내장 평가기처럼 피드백이 빈 문자열이면 '개선 지침 없는 재시도'가 되어 같은 답이 반복된다 (책 예제 B.5의 반쪽)
        Evaluator alwaysFailsSilently = req -> new EvaluationResponse(false, 0f, "", Map.of());
        List<String> seen = new ArrayList<>();
        var outcome = new EvaluatedAgentLoop(answerer(seen), alwaysFailsSilently, 3).run("정책과 도입 연도는?");

        assertThat(outcome.status()).isEqualTo(EvaluatedAgentLoop.Status.FAILED_AFTER_RETRIES);
        assertThat(outcome.attempts()).hasSize(3).allMatch(a -> a.answer().contains("2019"));
        assertThat(seen).noneMatch(u -> u.contains("[개선 지침]"));
    }

    @Test
    void loop_noRetrievedDocuments_isNoContext_notAFailure() {
        ChatModel generator = prompt -> new ChatResponse(List.of(new Generation(new AssistantMessage("모르겠습니다"))));
        VectorStore empty = org.mockito.Mockito.mock(VectorStore.class);
        org.mockito.Mockito.when(empty.similaritySearch(org.mockito.ArgumentMatchers.any(SearchRequest.class))).thenReturn(List.of());
        Evaluator mustNotBeCalled = req -> { throw new AssertionError("근거가 없으면 평가하지 않아야 한다"); };

        var outcome = new EvaluatedAgentLoop(new RagAnswerer(ChatClient.builder(generator), empty, 0.0), mustNotBeCalled, 3)
                .run("구내식당 메뉴?");
        assertThat(outcome.status()).isEqualTo(EvaluatedAgentLoop.Status.NO_CONTEXT);
    }
}
