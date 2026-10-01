package kr.jay.springai.ch05.client;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CompleteRequest;
import io.modelcontextprotocol.spec.McpSchema.GetPromptRequest;
import io.modelcontextprotocol.spec.McpSchema.PromptReference;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceRequest;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * MCP 클라이언트 기능을 한곳에 모은 서비스 — 단계별 러너와 최종 CLI가 함께 쓴다.
 *
 * <ul>
 *   <li>McpSyncClient : 자동 구성이 만든 '연결' 객체 (application.yml의 streamable-http.connections.rag).
 *       기동할 때 initialize 핸드셰이크를 마쳤다(initialized: true). JSON-RPC 메서드를 그대로 부르는 저수준 API</li>
 *   <li>SyncMcpToolCallbackProvider : 원격 도구 목록(tools/list)을 ToolCallback 배열로 바꿔 준다 —
 *       ChatClient에 넣으면 4장의 로컬 도구와 똑같이 쓰인다. 실행(call)하면 내부에서 tools/call을 보낸다</li>
 * </ul>
 */
@Service
@Profile("!server")
public class McpClientCatalogService {

    private final List<McpSyncClient> clients;
    private final SyncMcpToolCallbackProvider toolCallbackProvider;

    public McpClientCatalogService(List<McpSyncClient> clients, SyncMcpToolCallbackProvider toolCallbackProvider) {
        this.clients = clients;
        this.toolCallbackProvider = toolCallbackProvider;
    }

    public McpSyncClient client() {
        return clients.getFirst();          // 연결이 하나뿐이다
    }

    public ToolCallback[] toolCallbacks() {
        return toolCallbackProvider.getToolCallbacks();
    }

    public ToolCallback toolCallback(String name) {
        return Arrays.stream(toolCallbacks())
                .filter(c -> c.getToolDefinition().name().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("도구 없음 (필터에 걸렸거나 서버에 없음): " + name));
    }

    // ── 모델 없이 프리미티브 직접 호출 (Step2) ─────────────────────────────

    public McpSchema.CallToolResult callTool(String name, Map<String, Object> args) {
        return client().callTool(new CallToolRequest(name, args));
    }

    public McpSchema.ReadResourceResult readResource(String uri) {
        return client().readResource(new ReadResourceRequest(uri));
    }

    public McpSchema.GetPromptResult getPrompt(String name, Map<String, Object> args) {
        return client().getPrompt(new GetPromptRequest(name, args));
    }

    public List<String> completeCategory(String prefix) {
        return client().completeCompletion(new CompleteRequest(
                        new PromptReference("rag-grounded-answer"),
                        new CompleteRequest.CompleteArgument("category", prefix)))
                .completion().values();
    }

    /** CallToolResult의 첫 번째 텍스트 내용 */
    public static String text(McpSchema.CallToolResult result) {
        return result.content().stream()
                .filter(c -> c instanceof McpSchema.TextContent)
                .map(c -> ((McpSchema.TextContent) c).text())
                .findFirst().orElse("");
    }
}
