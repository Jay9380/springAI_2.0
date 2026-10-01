package kr.jay.springai.ch06cli.orchestration;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.micrometer.core.instrument.MeterRegistry;
import kr.jay.springai.ch06cli.capability.local.LocalTools;
import kr.jay.springai.ch06cli.orchestration.meta.ConsoleQuestionHandler;
import kr.jay.springai.ch06cli.orchestration.meta.LenientTodoWriteCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.agent.tools.AskUserQuestionTool;
import org.springaicommunity.agent.tools.SkillsTool;
import org.springaicommunity.agent.tools.TodoWriteTool;
import org.springaicommunity.agent.tools.task.TaskTool;
import org.springaicommunity.agent.tools.task.claude.ClaudeSubagentReferences;
import org.springaicommunity.agent.tools.task.claude.ClaudeSubagentType;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.toolsearch.ToolIndex;
import org.springframework.ai.tool.toolsearch.index.vectorstore.VectorToolIndex;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;

/**
 * [6.6.3 예제 6.32, 6.6.5 예제 6.44, 6.6.6 예제 6.46] T2 조립. 에이전트는 툴을 '받기만' 하고, 여기서 어떻게 노출할지만 정한다.
 *
 * <ul>
 *   <li><b>도메인 툴</b> = 로컬 @Tool + MCP 서버 툴. MCP는 {@code ObjectProvider}로 느슨하게 받는다 — 서버가 없으면 로컬 툴만으로 동작하고,
 *       있으면 원격 툴이 같은 인터페이스로 합류한다.</li>
 *   <li><b>메타 툴</b> = Skill·TodoWrite·AskUserQuestionTool·Task (강화 에이전트만).</li>
 *   <li>코어 에이전트: 도메인 툴 수가 임계값 이상이면 툴 검색(VectorToolIndex), 아니면 기본 루프. 시작 시 어느 쪽인지 출력한다.</li>
 *   <li>강화 에이전트: 도메인 툴은 검색, 메타 툴은 항상 노출 (OrchestrationToolCallingAdvisor).</li>
 * </ul>
 * 에이전트 빈은 {@code @Lazy} — 실행한 단계(step)의 에이전트만 만든다(코어 단계는 agents/·skills/ 폴더가 없어도 돈다).
 *
 * <p><b>메타 툴을 {@code List<ToolCallback>} 빈으로 두지 않는 이유(실측):</b> 스프링 AI의 ToolCallingAutoConfiguration은
 * ToolCallback·List&lt;ToolCallback&gt; 타입 빈을 전부 모아 전역 툴 리졸버에 넣는다. 처음엔 메타 툴을 그 타입 빈으로 만들었더니
 * {@code @Lazy}를 무시하고 즉시 만들어졌고, ChatClient.Builder와 순환 참조가 나서 기동이 실패했다.
 * 게다가 전역 리졸버에 들어가면 '이름만으로 어디서든 호출 가능한 툴'이 된다. 그래서 전용 타입 {@link MetaTools}로 감쌌다.
 */
@Configuration
@Profile("!ops")
public class AgentConfig {

    private static final Logger log = LoggerFactory.getLogger(AgentConfig.class);

    @Bean
    ChatMemory chatMemory() {
        return MessageWindowChatMemory.builder().maxMessages(40).build();
    }

    @Bean
    LocalTools localTools() {
        return new LocalTools();
    }

    @Bean
    @Lazy
    ToolIndex toolIndex(EmbeddingModel embeddingModel) {
        // 인메모리 의미 색인 (임계값 미만이면 임베딩 호출 없음). 검색 결과는 최소 3개 — 모델이 maxResults:1로 줄이는 걸 막는다
        return new MinResultsToolIndex(new VectorToolIndex(SimpleVectorStore.builder(embeddingModel).build()), 3);
    }

    @Bean
    ToolCallTraceAdvisor toolCallTraceAdvisor() {
        return new ToolCallTraceAdvisor(System.out::println);
    }

    /** 도메인 툴: 로컬 + (연결돼 있으면) MCP 원격. */
    static List<ToolCallback> domainTools(LocalTools localTools, ObjectProvider<SyncMcpToolCallbackProvider> mcp) {
        List<ToolCallback> tools = new ArrayList<>(Arrays.asList(ToolCallbacks.from(localTools)));
        SyncMcpToolCallbackProvider provider = mcp.getIfAvailable();
        if (provider != null) {
            tools.addAll(Arrays.asList(provider.getToolCallbacks()));
        }
        return tools;
    }

    @Bean
    @Lazy
    SpringAIAgent coreAgent(ChatClient.Builder builder, LocalTools localTools, ObjectProvider<SyncMcpToolCallbackProvider> mcp,
                            ObjectProvider<ToolIndex> toolIndex, ChatMemory chatMemory, MeterRegistry meterRegistry,
                            ToolCallTraceAdvisor trace,
                            @Value("${spring.ai.cli.agent.core.system-prompt}") String systemPrompt,
                            @Value("${spring.ai.cli.tool-search.min-tools}") int minTools,
                            @Value("${spring.ai.cli.safety.max-tool-rounds}") int maxRounds) {
        List<ToolCallback> tools = domainTools(localTools, mcp);
        ToolCallingAdvisor loop = tools.size() >= minTools
                ? new OrchestrationToolCallingAdvisor(toolIndex.getObject(), List.of(), 5, maxRounds)
                : new SafeToolCallingAdvisor(maxRounds);
        log.info("코어 에이전트: 툴 {}개 → {}", tools.size(), loop.getClass().getSimpleName());
        return new SpringAIAgent(builder, systemPrompt, tools, loop, chatMemory, meterRegistry, trace);
    }

    /** 메타 툴 묶음. ToolCallback 타입 빈이 아니므로 전역 툴 리졸버에 자동 수집되지 않는다. */
    public record MetaTools(List<ToolCallback> tools) {
    }

    @Bean
    @Lazy
    MetaTools metaTools(ChatClient.Builder builder, @Value("${spring.ai.cli.home}") String home) {
        List<ToolCallback> meta = new ArrayList<>();
        meta.add(SkillsTool.builder().addSkillsDirectory(Path.of(home, "skills").toString()).build());       // 절차 주입
        meta.add(new LenientTodoWriteCallback(TodoWriteTool.builder()                                          // 계획
                .todoEventHandler(t -> {
                    System.out.println("  [계획]");
                    t.todos().forEach(i -> System.out.printf("   %-11s %s%n", i.status(), i.content()));
                }).build()));
        meta.addAll(Arrays.asList(ToolCallbacks.from(                                                            // 명확화 질문
                AskUserQuestionTool.builder().questionHandler(new ConsoleQuestionHandler()).build())));
        meta.add(TaskTool.builder()                                                                              // 위임
                .subagentTypes(ClaudeSubagentType.builder().chatClientBuilder("default", builder.clone()).build())
                .subagentReferences(ClaudeSubagentReferences.fromRootDirectory(Path.of(home, "agents").toString()))
                .build());
        return new MetaTools(List.copyOf(meta));
    }

    @Bean
    @Lazy
    SpringAIAgent enhancedAgent(ChatClient.Builder builder, LocalTools localTools, ObjectProvider<SyncMcpToolCallbackProvider> mcp,
                                ToolIndex toolIndex, MetaTools meta, ChatMemory chatMemory,
                                MeterRegistry meterRegistry, ToolCallTraceAdvisor trace,
                                @Value("${spring.ai.cli.agent.core.system-prompt}") String systemPrompt,
                                @Value("${spring.ai.cli.safety.max-tool-rounds}") int maxRounds) {
        List<ToolCallback> metaTools = meta.tools();
        List<ToolCallback> domain = domainTools(localTools, mcp);
        List<ToolCallback> all = new ArrayList<>(domain);
        all.addAll(metaTools);
        log.info("강화 에이전트: 도메인 툴 {}개(검색) + 메타 툴 {}개(항상 노출)", domain.size(), metaTools.size());
        return new SpringAIAgent(builder, systemPrompt, all,
                new OrchestrationToolCallingAdvisor(toolIndex, metaTools, 5, maxRounds), chatMemory, meterRegistry, trace);
    }
}
