package kr.jay.springai.ch05.client;

import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * [5.5.4 예제 5.85] MCP 도구를 쓰는 채팅 서비스 — 4장 ToolEnabledChatService와 거의 같다.
 * 다른 점은 도구가 '원격 MCP 서버'에 있다는 것뿐이다. 이것이 MCP의 핵심 가치: 도구를 앱 밖으로 빼도 쓰는 코드는 같다.
 *
 * <p>defaultToolCallbacks(provider)로 '제공자'를 넘긴다(배열이 아니라). 제공자는 요청 시점에 도구 목록을 다시 읽으므로
 * 서버가 도구를 추가·삭제(list_changed 알림)해도 앱 재시작 없이 반영된다.
 *
 * <p>도구 선택 정책은 시스템 프롬프트에 둔다: 기본은 검색 도구로 근거를 받아 '클라이언트 모델'이 답을 쓰고,
 * 서버 측 답변 도구는 사용자가 명시적으로 원할 때만.
 */
@Service
@Profile("!server")
public class McpEnabledChatService {

    static final String SYSTEM_PROMPT = """
            당신은 사내 문서 도우미입니다.
            - 정책·RAG 운영·장애 대응·자전거 카탈로그·스프링 부트 질문은 rag_search_documents로 검색한 뒤,
              검색 결과만 근거로 한국어로 간결하게 답하고 출처 파일명을 밝힙니다.
            - 문서에 없는 사실은 단정하지 않습니다.
            """;

    private final ChatClient chatClient;

    public McpEnabledChatService(ChatClient.Builder builder, SyncMcpToolCallbackProvider toolCallbackProvider) {
        ChatMemory memory = MessageWindowChatMemory.builder().maxMessages(20).build();
        this.chatClient = builder.clone()
                .defaultSystem(SYSTEM_PROMPT)
                .defaultToolCallbacks(toolCallbackProvider)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(memory).order(BaseAdvisor.HIGHEST_PRECEDENCE + 200).build(),
                        ToolCallingAdvisor.builder().advisorOrder(BaseAdvisor.HIGHEST_PRECEDENCE + 300).build())
                .build();
    }

    public Flux<String> stream(String question, String conversationId) {
        return chatClient.prompt()
                .user(question)
                // ToolContext → 허용 목록 정책을 거쳐 MCP 요청의 _meta로 서버에 전달된다
                .toolContext(Map.of("userId", "chapter5-cli-user", "conversationId", conversationId))
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .stream()
                .content();
    }
}
