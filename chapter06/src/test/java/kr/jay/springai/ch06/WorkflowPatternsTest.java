package kr.jay.springai.ch06;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import kr.jay.springai.ch06.workflow.ChainWorkflow;
import kr.jay.springai.ch06.workflow.EvaluatorOptimizer;
import kr.jay.springai.ch06.workflow.OrchestratorWorkers;
import kr.jay.springai.ch06.workflow.ParallelizationWorkflow;
import kr.jay.springai.ch06.workflow.RoutingWorkflow;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

/** 워크플로 5종의 '코드가 정하는 흐름'을 가짜 모델로 확인한다. 성공 경로와 멈춰야 하는 경로를 같이 본다. */
class WorkflowPatternsTest {

    @Test
    void chain_passesEachOutputToNextStep() {
        ScriptedModel model = ScriptedModel.replying((n, p) -> "out" + n);
        var result = new ChainWorkflow(ChatClient.create(model), List.of(
                ChainWorkflow.Step.of("a", "A"), ChainWorkflow.Step.of("b", "B"))).chain("in");

        assertThat(result.completed()).isTrue();
        assertThat(result.output()).isEqualTo("out2");
        assertThat(model.prompts.get(1).getContents()).contains("out1");   // 2단계 입력에 1단계 출력
    }

    @Test
    void chain_gateFailure_stopsBeforeNextStep() {
        ScriptedModel model = ScriptedModel.replying((n, p) -> "숫자 없음");
        var result = new ChainWorkflow(ChatClient.create(model), List.of(
                new ChainWorkflow.Step("추출", "A", out -> out.matches("(?s).*\\d.*")),
                ChainWorkflow.Step.of("표준화", "B"))).chain("in");

        assertThat(result.completed()).isFalse();
        assertThat(result.stoppedAt()).isEqualTo("추출");
        assertThat(model.prompts).hasSize(1);                              // 2단계는 호출되지 않았다
    }

    @Test
    void routing_usesSelectedRoutePrompt() {
        ScriptedModel model = ScriptedModel.replying((n, p) -> n == 1 ? " Billing.\n" : "답변");
        var r = new RoutingWorkflow(ChatClient.create(model))
                .route("환불", Map.of("billing", "결제전문", "general", "일반"), "general");

        assertThat(r.route()).isEqualTo("billing");                        // 공백·대문자·마침표 정리 후 매칭
        assertThat(model.prompts.get(1).getSystemMessage().getText()).isEqualTo("결제전문");
    }

    @Test
    void routing_unknownLabel_fallsBackInsteadOfFailing() {
        ScriptedModel model = ScriptedModel.replying((n, p) -> n == 1 ? "refund_team" : "답변");
        var r = new RoutingWorkflow(ChatClient.create(model))
                .route("환불", Map.of("billing", "결제전문", "general", "일반"), "general");

        assertThat(r.route()).isEqualTo("general");
        assertThat(model.prompts.get(1).getSystemMessage().getText()).isEqualTo("일반");
    }

    @Test
    void parallel_keepsInputOrder_andVoteUsesMajority() {
        ScriptedModel model = ScriptedModel.replying((n, p) -> p.contains("입력: ") ? p.substring(p.indexOf("입력: ") + 4).trim() : "");
        var workflow = new ParallelizationWorkflow(ChatClient.create(model));
        assertThat(workflow.parallel("분석", List.of("고객", "직원", "투자자"), 3)).containsExactly("고객", "직원", "투자자");

        ScriptedModel twoOfThree = ScriptedModel.replying((n, p) -> n <= 2 ? "YES" : "NO");
        assertThat(new ParallelizationWorkflow(ChatClient.create(twoOfThree)).vote("취약?", 3)).isTrue();
        ScriptedModel oneOfThree = ScriptedModel.replying((n, p) -> n == 1 ? "YES" : "NO");
        assertThat(new ParallelizationWorkflow(ChatClient.create(oneOfThree)).vote("취약?", 3)).isFalse();
    }

    @Test
    void orchestrator_capsSubtasksDecidedByModel() {
        String plan = """
                {"analysis":"분석","subtasks":[
                 {"type":"a","description":"1"},{"type":"b","description":"2"},{"type":"c","description":"3"},
                 {"type":"d","description":"4"},{"type":"e","description":"5"}]}""";
        ScriptedModel model = ScriptedModel.replying((n, p) -> n == 1 ? plan : "워커결과" + n);
        var result = new OrchestratorWorkers(ChatClient.create(model), 3).process("작업");

        assertThat(result.subtasks()).extracting(OrchestratorWorkers.Subtask::type).containsExactly("a", "b", "c");
        assertThat(model.prompts).hasSize(1 + 3);                          // 계획 1번 + 워커 3번 (5번이 아니라)
    }

    @Test
    void evaluator_stopsEarlyOnPass() {
        ScriptedModel model = ScriptedModel.replying((n, p) -> switch (n) {
            case 1 -> "{\"thoughts\":\"t\",\"response\":\"초안\"}";
            case 2 -> "{\"evaluation\":\"NEEDS_IMPROVEMENT\",\"feedback\":\"마감일 빠짐\"}";
            case 3 -> "{\"thoughts\":\"t\",\"response\":\"수정본\"}";
            default -> "{\"evaluation\":\"PASS\",\"feedback\":\"\"}";
        });
        var result = new EvaluatorOptimizer(ChatClient.create(model), 5).loop("작업");

        assertThat(result.passed()).isTrue();
        assertThat(result.solution()).isEqualTo("수정본");
        assertThat(result.iterations()).isEqualTo(2);
        assertThat(model.prompts.get(2).getContents()).contains("마감일 빠짐");   // 피드백이 재생성 입력에 들어감
    }

    @Test
    void evaluator_neverPassing_stopsAtLimit() {
        // 책 예제(do-while, 상한 없음)라면 영원히 도는 경우
        ScriptedModel model = ScriptedModel.replying((n, p) -> n % 2 == 1
                ? "{\"thoughts\":\"t\",\"response\":\"시도" + n + "\"}"
                : "{\"evaluation\":\"FAIL\",\"feedback\":\"아님\"}");
        var result = new EvaluatorOptimizer(ChatClient.create(model), 3).loop("작업");

        assertThat(result.passed()).isFalse();
        assertThat(result.iterations()).isEqualTo(3);
        assertThat(model.prompts).hasSize(6);                              // (생성+평가) × 3, 그 이상은 없음
    }

    @Test
    void evaluator_codeCheckOverridesLenientLlmPass() {
        // LLM 평가자는 항상 PASS를 주지만, 코드 검사(10자 이내)가 첫 결과를 거절해야 한다
        ScriptedModel model = ScriptedModel.replying((n, p) -> p.contains("엄격하게 평가")
                ? "{\"evaluation\":\"PASS\",\"feedback\":\"\"}"
                : n == 1 ? "{\"thoughts\":\"t\",\"response\":\"아주아주아주아주 긴 제목입니다\"}"
                         : "{\"thoughts\":\"t\",\"response\":\"짧은 제목\"}");
        var result = new EvaluatorOptimizer(ChatClient.create(model), 3,
                c -> c.length() > 10 ? "현재 " + c.length() + "자" : null).loop("작업");

        assertThat(result.passed()).isTrue();
        assertThat(result.solution()).isEqualTo("짧은 제목");
        assertThat(result.iterations()).isEqualTo(2);
        assertThat(model.prompts.get(1).getContents()).contains("현재 ");    // 코드 위반 내용이 재생성 피드백으로
    }
}
