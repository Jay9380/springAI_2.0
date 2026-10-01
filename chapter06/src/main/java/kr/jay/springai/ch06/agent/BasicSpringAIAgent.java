package kr.jay.springai.ch06.agent;

import java.util.List;

import kr.jay.springai.ch06.advisor.WarehouseTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallbackProvider;

/**
 * [6.4.3 예제 6.17] 코어만으로 만든 싱글 에이전트 — while도 for도 없다.
 *
 * <ul>
 *   <li>루트 에이전트(루프 + LLM) = ChatClient + ToolCallingAdvisor</li>
 *   <li>프로세스 안의 능력 = 로컬 @Tool (LocalTools, WarehouseTools)</li>
 *   <li>프로세스 밖의 능력 = MCP 툴 (운영 서버: 상태 조회, 승인이 필요한 재시작)</li>
 *   <li>기억 = 메모리 어드바이저를 <b>루프 안쪽(+400)</b>에 둬서 툴 요청·결과까지 저장</li>
 * </ul>
 * 메모리를 루프 안에 두므로 ToolCallingAdvisor의 내부 기록은 끈다(disableInternalConversationHistory).
 * 끄지 않으면 같은 툴 결과가 두 번 컨텍스트에 들어간다. 명시 등록한 이유: 순서를 코드에 드러내고, 기록 관리를 한곳에 맡기려고.
 *
 * <p>MCP 제공자는 '있으면 쓰는' 선택 사항으로 받았다(null 가능). 서버 없이도 로컬 툴만으로 동작해야 학습하기 편하다.
 */
public class BasicSpringAIAgent {

    static final String SYSTEM_PROMPT = """
            당신은 사용자의 요청을 끝까지 처리하는 AI 에이전트입니다.
            - 작업에 필요한 툴이 있으면 직접 호출하고, 결과를 확인한 뒤 다음 행동을 결정합니다.
            - 툴로 확인하지 않은 사실은 함부로 단정하지 않습니다.
            - 툴이 '중단' 또는 '거부'를 돌려주면 다시 시도하지 말고 그 사실을 사용자에게 알립니다.
            - 모든 작업이 끝나면 수행한 내용을 한국어로 간결하게 요약합니다.
            """;

    private final ChatClient chatClient;
    private final ChatMemory memory = MessageWindowChatMemory.builder().maxMessages(40).build();

    public BasicSpringAIAgent(ChatClient.Builder builder, SyncMcpToolCallbackProvider mcpTools) {
        ChatClient.Builder b = builder.clone()
                .defaultSystem(SYSTEM_PROMPT)
                .defaultTools(new LocalTools(), new WarehouseTools())
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(memory).order(BaseAdvisor.HIGHEST_PRECEDENCE + 400).build(),
                        ToolCallingAdvisor.builder()
                                .disableInternalConversationHistory()
                                .advisorOrder(BaseAdvisor.HIGHEST_PRECEDENCE + 300)
                                .build());
        if (mcpTools != null) {
            b.defaultToolCallbacks((ToolCallbackProvider) mcpTools);      // 프로세스 밖 툴 (요청마다 목록을 다시 읽음)
        }
        this.chatClient = b.build();
    }

    public String run(String userMessage, String conversationId) {
        return chatClient.prompt()
                .user(userMessage)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .content();
    }

    public List<String> memoryTypes(String conversationId) {
        return memory.get(conversationId).stream().map(m -> m.getMessageType().name()).toList();
    }
}
