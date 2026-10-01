package kr.jay.springai.ch06.context;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.core.io.ClassPathResource;

/**
 * [6.2 컨텍스트 엔지니어링] 모델에게 '무엇을 보여 줄지'를 코드가 결정하는 에이전트.
 *
 * <p>책 표 6.6을 그대로 코드로 옮겼다.
 * <table>
 *   <tr><th>요소</th><th>LLM에 전달 (참고)</th><th>코드가 집행 (강제)</th></tr>
 *   <tr><td>시스템 정책</td><td>{@link #POLICY} 텍스트</td><td>매 호출 맨 앞에 자동 주입 (defaultSystem)</td></tr>
 *   <tr><td>프로젝트 지침</td><td>AGENTS.md 내용</td><td><b>어느 파일을 언제</b> 넣을지 — 여기선 시작 시 1번</td></tr>
 *   <tr><td>툴</td><td>이름·설명·스키마</td><td><b>역할별 노출 필터링</b> — VIEWER에게는 cancelOrder 스키마 자체가 없다</td></tr>
 *   <tr><td>메모리</td><td>최근 대화</td><td>최근 20개만 유지 (슬라이딩 윈도우)</td></tr>
 * </table>
 *
 * <p>책의 한 문장(p.546): "권한 없는 사용자에게는 삭제 툴의 스키마 자체를 제거하는 것이 '삭제하지 마시오'라는 텍스트보다 강력하다."
 * 스키마가 없으면 모델은 그 툴이 있는지조차 모른다. 혹시 이름을 지어내 호출해도 ToolCallingManager가
 * "No ToolCallback found"로 거절한다.
 *
 * <p><b>실측에서 배운 것:</b> 스키마만 지웠더니 VIEWER 에이전트가 "ORD-1001은 취소가 가능합니다. 취소하시겠습니까?"라고
 * 할 수 없는 일을 제안했다. 모델은 툴이 '없다'는 사실도 모른다. 그래서 집행(스키마 제거)과 함께
 * 전달(역할 안내 {@link #roleNote})도 넣는다. 둘은 대체 관계가 아니라 짝이다.
 */
public class ContextEngineeredAgent {

    public enum Role { VIEWER, OPERATOR }

    /** 예제 6.8처럼 '판단 기준'을 주입한다. 추론 모델에게 "단계별로 생각하라"는 이제 불필요하다(표 6.5). */
    static final String POLICY = """
            당신은 주문 지원 에이전트입니다.
            <정책>
            - 주문 정보는 반드시 툴로 확인한 뒤 답합니다. 확인하지 않은 상태를 추측하지 않습니다.
            - 주문 취소 전에는 주문 상태를 먼저 조회합니다.
            - 툴이 '실패' 또는 '거부'를 돌려주면 같은 호출을 반복하지 말고, 그 메시지의 안내를 따릅니다.
            </정책>
            """;

    private final ChatClient chatClient;
    private final List<String> exposedToolNames;

    public ContextEngineeredAgent(ChatClient.Builder builder, Role role, OrderTools orderTools,
                                  ProjectFileTools fileTools) {
        List<ToolCallback> tools = exposedTools(role, orderTools, fileTools);
        this.exposedToolNames = tools.stream().map(t -> t.getToolDefinition().name()).toList();
        ChatMemory memory = MessageWindowChatMemory.builder().maxMessages(20).build();
        this.chatClient = builder.clone()
                .defaultSystem(POLICY + roleNote(role)
                        + "\n<프로젝트 지침 (AGENTS.md)>\n" + load("project/AGENTS.md") + "</프로젝트 지침>")
                .defaultToolCallbacks(tools)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(memory).build())
                .build();
    }

    /** 전달 영역: 지금 사용자의 권한을 모델에게 알려 할 수 없는 일을 제안하지 않게 한다. */
    static String roleNote(Role role) {
        return switch (role) {
            case VIEWER -> "현재 사용자는 조회 권한만 있습니다. 취소·변경은 할 수 없으니, 요청받으면 운영 담당자에게 요청하도록 안내합니다.\n";
            case OPERATOR -> "현재 사용자는 주문 취소 권한이 있습니다.\n";
        };
    }

    /** 집행 영역: 역할에 따라 툴 '목록 자체'를 다르게 만든다. */
    static List<ToolCallback> exposedTools(Role role, OrderTools orderTools, ProjectFileTools fileTools) {
        var all = new java.util.ArrayList<ToolCallback>();
        all.addAll(Arrays.asList(ToolCallbacks.from(orderTools)));
        all.addAll(Arrays.asList(ToolCallbacks.from(fileTools)));
        return all.stream()
                .filter(t -> role == Role.OPERATOR || !OrderTools.CANCEL_ORDER.equals(t.getToolDefinition().name()))
                .toList();
    }

    public String ask(String question, String conversationId) {
        return chatClient.prompt()
                .user(question)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .content();
    }

    public List<String> exposedToolNames() {
        return exposedToolNames;
    }

    private static String load(String path) {
        try {
            return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
