package kr.jay.springai.ch05;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

/**
 * MCP 서버를 실제로 띄우고(임의 포트), 순수 MCP 자바 클라이언트로 접속해 프로토콜 수준에서 확인한다.
 * 임베딩·채팅 모델만 가짜로 바꿔서 Ollama 없이 돈다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("server")
class McpServerIntegrationTest {

    @TestConfiguration
    static class FakeModels {
        @Bean
        @Primary
        EmbeddingModel fakeEmbedding() {
            return new FakeEmbeddingModel();
        }

        @Bean
        @Primary
        ChatModel fakeChat() {
            return prompt -> new ChatResponse(List.of(new Generation(new AssistantMessage("30분 이내입니다."))));
        }
    }

    @LocalServerPort
    int port;

    McpSyncClient client;

    @BeforeEach
    void connect() {
        client = clientWithKey("local-study-key");   // [5.4] 서버 설정과 같은 키
        client.initialize();                     // initialize → notifications/initialized 핸드셰이크
    }

    /** 모든 요청에 X-API-Key 헤더를 붙이는 클라이언트. key가 null이면 헤더를 붙이지 않는다. */
    McpSyncClient clientWithKey(String key) {
        var transport = HttpClientStreamableHttpTransport.builder("http://localhost:" + port)
                .endpoint("/mcp")
                .httpRequestCustomizer((builder, method, endpoint, body, ctx) -> {
                    if (key != null) {
                        builder.header("X-API-Key", key);
                    }
                })
                .build();
        return McpClient.sync(transport).requestTimeout(java.time.Duration.ofSeconds(5)).build();
    }

    @AfterEach
    void close() {
        client.closeGracefully();
    }

    @Test
    void serverAdvertisesThreeToolsWithHonestHints_andHidesMcpMetaFromSchema() {
        List<McpSchema.Tool> tools = client.listTools().tools();
        assertThat(tools).extracting(McpSchema.Tool::name)
                .containsExactlyInAnyOrder("rag_index_summary", "rag_search_documents", "rag_answer_question");

        McpSchema.Tool answer = tools.stream().filter(t -> t.name().equals("rag_answer_question")).findFirst().orElseThrow();
        // mcp-core 2.0에서 inputSchema()는 JSON 스키마를 담은 Map이다
        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) answer.inputSchema().get("properties");
        assertThat(props).containsOnlyKeys("question", "category");                          // McpMeta는 스키마에 없음
        assertThat(answer.annotations().idempotentHint()).isFalse();                             // LLM 답은 매번 다름
        assertThat(answer.annotations().readOnlyHint()).isTrue();
    }

    @Test
    void toolsCall_returnsMaskedSearchResults() {
        McpSchema.CallToolResult result = client.callTool(new McpSchema.CallToolRequest("rag_search_documents",
                Map.of("query", "보안 문의", "topK", 2, "category", "tech_docs")));
        String text = ((McpSchema.TextContent) result.content().getFirst()).text();
        assertThat(result.isError()).isFalse();
        assertThat(text).contains("policy-docs.txt").doesNotContain("@example.com");
    }

    @Test
    void withoutApiKey_handshakeIsRejected() {
        // [5.4] 실패 경로: 키가 없으면 initialize 단계에서 401로 막힌다 → 도구 목록조차 볼 수 없다
        McpSyncClient anonymous = clientWithKey(null);
        try {
            org.assertj.core.api.Assertions.assertThatThrownBy(anonymous::initialize);
        } finally {
            anonymous.close();
        }
    }

    @Test
    void wrongApiKey_isRejected() {
        McpSyncClient intruder = clientWithKey("guess-1234");
        try {
            org.assertj.core.api.Assertions.assertThatThrownBy(intruder::initialize);
        } finally {
            intruder.close();
        }
    }

    @Test
    void unknownTool_isRejectedByServer() {
        // 실패 경로: 서버에 없는 도구 이름은 JSON-RPC 오류로 거절된다
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        client.callTool(new McpSchema.CallToolRequest("delete_everything", Map.of())))
                .hasMessageContaining("Unknown tool");
    }

    @Test
    void resourcesPromptsAndCompletionWork() {
        assertThat(((McpSchema.TextResourceContents) client.readResource(new McpSchema.ReadResourceRequest("rag://sources"))
                .contents().getFirst()).text()).contains("policy-docs.txt");
        assertThat(client.completeCompletion(new McpSchema.CompleteRequest(new McpSchema.PromptReference("rag-grounded-answer"),
                new McpSchema.CompleteRequest.CompleteArgument("category", "pro"))).completion().values())
                .containsExactly("product_catalog");
    }
}
