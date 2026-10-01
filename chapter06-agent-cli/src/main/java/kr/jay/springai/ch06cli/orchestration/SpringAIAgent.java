package kr.jay.springai.ch06cli.orchestration;

import java.util.List;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.tool.ToolCallback;

/**
 * [6.6.3 예제 6.31] T2 오케스트레이션의 중심 — 코어 메인 에이전트.
 *
 * <pre>
 *   +200  MessageChatMemoryAdvisor  루프 바깥, 요청당 1번 (이전 대화 로드 / 최종 질문·답 저장)
 *   +300  툴 루프 어드바이저         SafeToolCallingAdvisor 또는 OrchestrationToolCallingAdvisor (AgentConfig가 고름)
 *   +350  ToolCallTraceAdvisor       루프 안: [툴 호출]·[툴 결과]
 *   +400  ToolLoopMetricsAdvisor     루프 안: 반복 수·툴별 호출·반복당 토큰
 *         ChatModel                  (gen_ai.client.operation 관측은 스프링 AI가 자동)
 * </pre>
 * 로컬 툴, 커뮤니티 메타 툴, MCP 원격 툴이 모두 {@code List<ToolCallback>} 하나로 들어온다 — "툴 경계가 곧 시스템 경계".
 * 이 클래스는 툴이 어디서 왔는지 모른다(OCP: T3가 늘어도 T2는 그대로).
 *
 * <p>책은 {@code .stream()}으로 최종 답을 흘려보낸다. 여기서는 루프 안 추적 출력과 섞이지 않도록 {@code .call()}을 쓴다.
 */
public class SpringAIAgent {

    private final ChatClient chatClient;
    private final List<String> toolNames;
    private final String loopAdvisorName;

    public SpringAIAgent(ChatClient.Builder builder, String systemPrompt, List<ToolCallback> tools,
                         ToolCallingAdvisor loopAdvisor, ChatMemory chatMemory, MeterRegistry meterRegistry,
                         ToolCallTraceAdvisor trace) {
        this.toolNames = tools.stream().map(t -> t.getToolDefinition().name()).toList();
        this.loopAdvisorName = loopAdvisor.getClass().getSimpleName();
        this.chatClient = builder.clone()
                .defaultSystem(systemPrompt)
                .defaultToolCallbacks(tools)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).order(BaseAdvisor.HIGHEST_PRECEDENCE + 200).build(),
                        loopAdvisor,
                        trace,
                        new ToolLoopMetricsAdvisor(meterRegistry))
                .build();
    }

    public String run(String userMessage, String conversationId) {
        return chatClient.prompt()
                .user(userMessage)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .content();
    }

    public List<String> toolNames() {
        return toolNames;
    }

    public String loopAdvisorName() {
        return loopAdvisorName;
    }
}
