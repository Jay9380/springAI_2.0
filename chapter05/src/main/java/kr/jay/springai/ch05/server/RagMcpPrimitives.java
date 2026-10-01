package kr.jay.springai.ch05.server;

import java.util.List;
import java.util.stream.Collectors;

import io.modelcontextprotocol.spec.McpSchema.GetPromptResult;
import io.modelcontextprotocol.spec.McpSchema.PromptMessage;
import io.modelcontextprotocol.spec.McpSchema.Role;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import kr.jay.springai.ch05.support.RagKnowledgeBase;
import org.springframework.ai.mcp.annotation.McpArg;
import org.springframework.ai.mcp.annotation.McpComplete;
import org.springframework.ai.mcp.annotation.McpPrompt;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * [5.5.2 Step4] 도구 외의 MCP 프리미티브 — 리소스, 프롬프트, 자동 완성.
 *
 * <table>
 *   <tr><th>프리미티브</th><th>누가 쓰나</th><th>예</th></tr>
 *   <tr><td>Tool</td><td>모델이 '골라서' 실행</td><td>rag_search_documents</td></tr>
 *   <tr><td>Resource</td><td>앱(사람)이 '읽어서' 컨텍스트로 씀 — URI로 식별</td><td>rag://sources</td></tr>
 *   <tr><td>Prompt</td><td>사용자가 '골라 쓰는' 재사용 프롬프트 템플릿</td><td>rag-grounded-answer</td></tr>
 *   <tr><td>Completion</td><td>프롬프트 인자 입력 시 후보 추천</td><td>category 자동 완성</td></tr>
 * </table>
 * 책은 이 단계를 McpServerFeatures 프로그래밍 방식으로 등록하지만, 여기서는 도구와 같은 애너테이션 방식으로 썼다.
 */
@Component
@Profile("server")
public class RagMcpPrimitives {

    private static final List<String> CATEGORIES = List.of("tech_docs", "product_catalog");

    private final RagKnowledgeBase knowledgeBase;

    public RagMcpPrimitives(RagKnowledgeBase knowledgeBase) {
        this.knowledgeBase = knowledgeBase;
    }

    @McpResource(uri = "rag://sources", name = "rag-sources", description = "적재된 원천 문서와 청크 수")
    public String sources() {
        return knowledgeBase.chunksPerSource().entrySet().stream()
                .map(e -> e.getKey() + ": 청크 " + e.getValue() + "개")
                .collect(Collectors.joining("\n"));
    }

    @McpResource(uri = "rag://pipeline", name = "rag-pipeline", description = "검색 파이프라인 설명")
    public String pipeline() {
        return """
                리더(txt·md·json) → 이메일·전화번호 마스킹 → TokenTextSplitter(chunkSize=300, minChunkSizeChars=80)
                → bge-m3 임베딩 + SimpleVectorStore → SearchRequest(topK ≤ 10, threshold 0.50, category 필터)""";
    }

    @McpPrompt(name = "rag-grounded-answer", description = "문서 근거로만 답하게 하는 프롬프트")
    public GetPromptResult groundedAnswer(
            @McpArg(name = "question", description = "질문", required = true) String question,
            @McpArg(name = "category", description = "선택 카테고리") String category) {
        String text = """
                rag_answer_question 도구를 호출해 아래 질문에 답하세요.
                질문: %s
                카테고리: %s
                문서에 없는 내용은 추측하지 마세요.""".formatted(question, category == null ? "(전체)" : category);
        return new GetPromptResult("문서 근거 답변",
                List.of(new PromptMessage(Role.USER, new TextContent(text))));
    }

    /** rag-grounded-answer의 category 인자를 입력할 때 후보를 추천한다 ("t" → tech_docs) */
    @McpComplete(prompt = "rag-grounded-answer")
    public List<String> completeCategory(String prefix) {
        return CATEGORIES.stream().filter(c -> c.startsWith(prefix == null ? "" : prefix)).toList();
    }
}
