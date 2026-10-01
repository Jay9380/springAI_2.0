package kr.jay.springai.ch05.step;

import java.util.Map;
import java.util.UUID;

import io.modelcontextprotocol.spec.McpSchema;
import kr.jay.springai.ch05.client.McpClientCatalogService;
import kr.jay.springai.ch05.client.McpEnabledChatService;
import kr.jay.springai.ch05.support.ChatConsole;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * [5.5.4] MCP 기반 AI 챗봇 CLI.
 *
 * <p>실행: 서버를 먼저 띄운 뒤 {@code java -jar chapter05/target/chapter05-*.jar --spring.ai.cli.step=ch5-final}
 *
 * <p>일반 입력은 모델이 MCP 도구를 골라 쓴다. 슬래시 명령은 모델 없이 프리미티브를 직접 부른다.
 * <pre>
 *   /tools                  필터를 통과한 원격 도구
 *   /resource rag://sources 리소스 읽기
 *   /prompt 질문            서버 프롬프트 템플릿 가져오기
 *   /complete t             category 자동 완성
 *   /answer-direct 질문      서버 측 답변 도구를 직접 호출 (서버가 검색 + 답변)
 * </pre>
 */
@Component
@Profile("!server")
@ConditionalOnProperty(prefix = "spring.ai.cli", name = "step", havingValue = "ch5-final")
public class Ch5McpCliChatbotApplication implements CommandLineRunner {

    private final McpClientCatalogService catalog;
    private final McpEnabledChatService chatService;
    private final String conversationId = UUID.randomUUID().toString();

    public Ch5McpCliChatbotApplication(McpClientCatalogService catalog, McpEnabledChatService chatService) {
        this.catalog = catalog;
        this.chatService = chatService;
    }

    @Override
    public void run(String... args) {
        System.out.println("─".repeat(60));
        System.out.println(" Spring AI MCP CLI Chatbot  (Chapter 5, Final)");
        System.out.println(" 서버: " + catalog.client().getServerInfo().name() + " @ http://localhost:8085/mcp");
        System.out.println(" 명령: /tools /resource <uri> /prompt <질문> /complete <접두사> /answer-direct <질문>");
        System.out.println("─".repeat(60));

        ChatConsole.run("[Ch5] Final: MCP 도구 챗봇", input -> {
            if (input.startsWith("/")) {
                System.out.print(command(input));
            } else {
                chatService.stream(input, conversationId).doOnNext(System.out::print).blockLast();
            }
        });
    }

    private String command(String input) {
        String[] parts = input.split("\\s+", 2);
        String arg = parts.length > 1 ? parts[1] : "";
        return switch (parts[0]) {
            case "/tools" -> {
                StringBuilder sb = new StringBuilder();
                for (ToolCallback cb : catalog.toolCallbacks()) {
                    sb.append("\n  ").append(cb.getToolDefinition().name());
                }
                yield sb.toString();
            }
            case "/resource" -> ((McpSchema.TextResourceContents) catalog.readResource(arg.isBlank() ? "rag://sources" : arg)
                    .contents().getFirst()).text();
            case "/prompt" -> ((McpSchema.TextContent) catalog.getPrompt("rag-grounded-answer", Map.of("question", arg))
                    .messages().getFirst().content()).text();
            case "/complete" -> catalog.completeCategory(arg).toString();
            case "/answer-direct" -> McpClientCatalogService.text(catalog.callTool("rag_answer_question", Map.of("question", arg)));
            default -> "알 수 없는 명령: " + parts[0];
        };
    }
}
