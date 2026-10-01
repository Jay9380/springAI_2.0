package kr.jay.springai.ch06.workflow;

import java.util.List;

import org.springframework.ai.chat.client.ChatClient;

/**
 * [6.1.2 ④ 예제 6.4] 오케스트레이터-워커 — LLM이 작업을 <b>동적으로</b> 쪼갠다.
 *
 * <p>겉모습은 병렬화와 비슷하지만, 병렬화는 하위 작업을 개발자가 정하고 여기서는 LLM(오케스트레이터)이 정한다.
 * 몇 개로, 어떤 기준으로 쪼갤지 코드가 모른다. 그래서 다섯 워크플로 중 에이전트에 가장 가깝다.
 * 6.4.2의 '하위 에이전트 위임'은 이 워커가 툴을 쓰는 에이전트가 된 형태다.
 *
 * <p>책에 없는 안전장치 하나: 오케스트레이터가 하위 작업을 너무 많이 만들면 워커 호출이 그만큼 늘어난다.
 * {@code maxSubtasks}로 상한을 둔다 (비용은 LLM이 아니라 코드가 통제한다).
 */
public class OrchestratorWorkers {

    public record Subtask(String type, String description) {
    }

    public record OrchestratorResponse(String analysis, List<Subtask> subtasks) {
    }

    public record FinalResponse(String analysis, List<Subtask> subtasks, List<String> workerResponses) {
    }

    private final ChatClient chatClient;
    private final int maxSubtasks;

    public OrchestratorWorkers(ChatClient chatClient, int maxSubtasks) {
        this.chatClient = chatClient;
        this.maxSubtasks = maxSubtasks;
    }

    public FinalResponse process(String taskDescription) {
        OrchestratorResponse plan = analyzeTask(taskDescription);
        List<Subtask> subtasks = plan.subtasks().stream().limit(maxSubtasks).toList();
        List<String> workerResponses = subtasks.stream()
                .map(subtask -> chatClient.prompt()
                        .user("""
                                원래 작업: %s
                                당신이 맡은 접근 방식(%s): %s
                                이 접근 방식에 맞춰 결과물만 한국어로 작성하세요.
                                """.formatted(taskDescription, subtask.type(), subtask.description()))
                        .call()
                        .content())
                .toList();
        return new FinalResponse(plan.analysis(), subtasks, workerResponses);
    }

    OrchestratorResponse analyzeTask(String taskDescription) {
        return chatClient.prompt()
                .user("""
                        다음 작업을 분석하고, 서로 다른 가치를 주는 접근 방식 2~3개로 나누세요.
                        analysis에는 작업을 어떻게 이해했는지, subtasks에는 type(짧은 영문 이름)과 description(한국어)을 담으세요.

                        작업: %s
                        """.formatted(taskDescription))
                .call()
                .entity(OrchestratorResponse.class, spec -> spec.useProviderStructuredOutput());
    }
}
