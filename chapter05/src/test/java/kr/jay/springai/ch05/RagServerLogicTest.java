package kr.jay.springai.ch05;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import io.modelcontextprotocol.spec.McpSchema;
import kr.jay.springai.ch05.client.McpClientPolicyConfig;
import kr.jay.springai.ch05.support.RagDocumentLoader;
import kr.jay.springai.ch05.support.RagKnowledgeBase;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.mcp.McpToolFilter;
import org.springframework.ai.mcp.ToolContextToMcpMetaConverter;
import org.springframework.ai.vectorstore.SimpleVectorStore;

/** MCP를 모르는 순수 로직(지식 베이스)과 클라이언트 정책을 MCP 서버 없이 확인한다. */
class RagServerLogicTest {

    private RagKnowledgeBase knowledgeBase() {
        return new RagKnowledgeBase(SimpleVectorStore.builder(new FakeEmbeddingModel()).build(), new RagDocumentLoader());
    }

    @Test
    void loader_masksContactsBeforeChunking() {
        assertThat(new RagDocumentLoader().loadChunks())
                .noneMatch(d -> d.getText().contains("@example.com") || d.getText().contains("010-"))
                .anyMatch(d -> d.getText().contains("[EMAIL]"));
    }

    @Test
    void index_isIdempotent() {
        RagKnowledgeBase kb = knowledgeBase();
        int first = kb.index();
        int second = kb.index();
        assertThat(second).isEqualTo(first);
        assertThat(kb.chunksPerSource()).containsKeys("policy-docs.txt", "bikes.json");
    }

    @Test
    void search_appliesCategoryFilter_andClampsTopK() {
        RagKnowledgeBase kb = knowledgeBase();
        kb.index();
        List<RagKnowledgeBase.Source> bikes = kb.search("산악 자전거", 50, "product_catalog");
        assertThat(bikes).isNotEmpty().hasSizeLessThanOrEqualTo(10)
                .allMatch(s -> s.category().equals("product_catalog"));
    }

    @Test
    void defaultMetaConverter_leaksEverything_allowListDoesNot() {
        // 실패 경로(기본값의 위험): 기본 변환기는 ToolContext의 값을 거의 전부 서버로 보낸다
        ToolContext ctx = new ToolContext(Map.of("userId", "u1", "password", "secret"));
        assertThat(ToolContextToMcpMetaConverter.defaultConverter().convert(ctx)).containsKey("password");

        ToolContextToMcpMetaConverter allowList = new McpClientPolicyConfig().allowListMetaConverter();
        assertThat(allowList.convert(ctx)).containsEntry("userId", "u1").doesNotContainKey("password");
    }

    @Test
    void toolFilter_acceptsOnlyRagTools_andRejectsExperimental() {
        McpToolFilter filter = new McpClientPolicyConfig().ragOnlyToolFilter();
        assertThat(filter.test(null, tool("rag_search_documents", "검색"))).isTrue();
        assertThat(filter.test(null, tool("delete_all", "전부 삭제"))).isFalse();
        assertThat(filter.test(null, tool("rag_new", "experimental 기능"))).isFalse();
    }

    private static McpSchema.Tool tool(String name, String description) {
        return McpSchema.Tool.builder().name(name).description(description)
                .inputSchema(new McpSchema.JsonSchema("object", Map.of(), List.of(), false, null, null))
                .build();
    }
}
