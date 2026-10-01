package kr.jay.springai.ch06cli.orchestration;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.toolsearch.ToolSearchToolCallingAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.toolsearch.ToolIndex;
import org.springframework.ai.tool.toolsearch.eviction.LruEvictionStrategy;

/**
 * [6.6.5 예제 6.43] 메타 툴 레이어 — 도메인 툴은 검색으로 찾고, 메타 툴은 항상 보여 준다.
 *
 * <p>부모(ToolSearchToolCallingAdvisor)의 색인·검색은 그대로 쓰고, 부모가 이번 라운드에 고른 툴 위에 메타 툴
 * (Skill·TodoWrite·AskUserQuestionTool·Task)을 중복 없이 얹는다. 이유: "절차대로 해줘", "위임해줘" 같은 메타 툴의 설명은
 * 도메인 질의와 의미 거리가 멀어 검색 상위에서 빠질 수 있다. 스킬이 사내 규칙(안전재고 50)을 담고 있다면 그것이 검색 순위에 밀려
 * 적용되지 않는 사고가 된다 — 메타 툴 레이어는 토큰 최적화가 아니라 <b>절차 준수</b> 장치다(6장-3 노트 2.7).
 *
 * <p>시스템 메시지 접미사도 덮어쓴다. 기본 문구("필요한 툴이 없으면 검색하라")만 두면 모델이 메타 툴까지 검색하려 든다.
 * 라운드 상한은 {@link AgentSafety}로 요청 컨텍스트에 둔다(어드바이저는 하나만 — 매 요청 재색인을 피하려고).
 */
public class OrchestrationToolCallingAdvisor extends ToolSearchToolCallingAdvisor {

    private final List<ToolCallback> alwaysOnTools;
    private final int maxRounds;

    public OrchestrationToolCallingAdvisor(ToolIndex toolIndex, List<ToolCallback> alwaysOnTools, int maxResults, int maxRounds) {
        super(ToolCallingManager.builder().build(), BaseAdvisor.HIGHEST_PRECEDENCE + 300,
                DEFAULT_TOOL_EXECUTION_ELIGIBILITY_CHECKER, toolIndex, suffix(alwaysOnTools),
                true, maxResults, true, ChatMemory.CONVERSATION_ID, new LruEvictionStrategy(1000));
        this.alwaysOnTools = List.copyOf(alwaysOnTools);
        this.maxRounds = maxRounds;
    }

    static String suffix(List<ToolCallback> alwaysOnTools) {
        String names = String.join(", ", alwaysOnTools.stream().map(t -> t.getToolDefinition().name()).toList());
        String metaRule = alwaysOnTools.isEmpty() ? "" : """
                - 다음 오케스트레이션 툴은 검색하지 말고 필요하면 바로 호출하세요: %s
                - 사내 절차가 있는 업무(재발주 등)는 가장 먼저 Skill로 절차를 불러와 그 순서대로 진행하세요.
                - 대상이 모호하면 직접 묻지 말고 AskUserQuestionTool로 선택지를 제시하세요.
                - TodoWrite는 계획을 처음 만들 때와 단계 상태가 바뀔 때만 호출하세요. 같은 내용으로 다시 부르지 마세요.
                """.formatted(names);
        return """

                [툴 사용 규칙]
                %s- 업무 능력(재고 조회, 발주, 사내 문서 질의, 시간, 계산 등)이 필요하면 toolSearchTool로 먼저 찾으세요.
                - 검색 결과가 맞지 않으면 다른 표현으로 다시 검색하세요.
                """.formatted(metaRule);
    }

    @Override
    protected ChatClientRequest doInitializeLoop(ChatClientRequest request, CallAdvisorChain chain) {
        return AgentSafety.init(super.doInitializeLoop(request, chain));
    }

    @Override
    protected ChatClientRequest doBeforeCall(ChatClientRequest request, CallAdvisorChain chain) {
        return appendAlwaysOnTools(super.doBeforeCall(request, chain));      // 부모가 고른 툴 + 메타 툴
    }

    @Override
    protected ChatClientResponse doAfterCall(ChatClientResponse response, CallAdvisorChain chain) {
        return AgentSafety.limit(super.doAfterCall(response, chain), maxRounds);
    }

    private ChatClientRequest appendAlwaysOnTools(ChatClientRequest request) {
        if (!(request.prompt().getOptions() instanceof ToolCallingChatOptions options)) {
            return request;
        }
        List<ToolCallback> merged = new ArrayList<>(options.getToolCallbacks());
        Set<String> present = new LinkedHashSet<>();
        merged.forEach(cb -> present.add(cb.getToolDefinition().name()));
        for (ToolCallback meta : alwaysOnTools) {
            if (present.add(meta.getToolDefinition().name())) {
                merged.add(meta);
            }
        }
        ToolCallingChatOptions mergedOptions = ((ToolCallingChatOptions.Builder<?>) options.mutate()).toolCallbacks(merged).build();
        return request.mutate().prompt(request.prompt().mutate().chatOptions(mergedOptions).build()).build();
    }
}
