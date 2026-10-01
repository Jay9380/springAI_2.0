package kr.jay.springai.ch05.server;

import java.util.List;
import java.util.Map;

import kr.jay.springai.ch05.support.RagAnswerService;
import kr.jay.springai.ch05.support.RagKnowledgeBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpMeta;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpTool.McpAnnotations;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * [5.3 / 5.5.2 Step2~3] MCP 도구 — 4장 @Tool과 거의 같은 모양이지만, '다른 프로세스의 클라이언트'에게 공개된다.
 *
 * <p>@McpTool이 붙은 메서드는 서버 기동 시 애너테이션 스캐너가 찾아 tools/list에 올린다.
 * 클라이언트는 tools/list로 이름·설명·입력 스키마를 받고, tools/call로 실행을 요청한다 (JSON-RPC 2.0).
 *
 * <p>annotations(힌트)는 클라이언트에게 주는 '도구의 성격' 정보다. 정직하게 쓴다.
 * <ul>
 *   <li>readOnlyHint=true : 상태를 바꾸지 않는다</li>
 *   <li>destructiveHint=false : 되돌릴 수 없는 일을 하지 않는다 (기본값이 true라서 읽기 도구는 꼭 false로)</li>
 *   <li>idempotentHint : 같은 입력이면 같은 결과인가 — 검색은 true, LLM 답변은 매번 달라지므로 false</li>
 *   <li>openWorldHint=false : 외부 세계(인터넷 등)에 접근하지 않는다</li>
 * </ul>
 */
@Component
@Profile("server")
public class RagMcpTools {

    private static final Logger log = LoggerFactory.getLogger(RagMcpTools.class);

    private final RagKnowledgeBase knowledgeBase;
    private final RagAnswerService answerService;

    public RagMcpTools(RagKnowledgeBase knowledgeBase, RagAnswerService answerService) {
        this.knowledgeBase = knowledgeBase;
        this.answerService = answerService;
    }

    @McpTool(name = "rag_index_summary",
            description = "RAG 서버에 적재된 원천 문서별 청크 수를 반환합니다.",
            annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false))
    public Map<String, Integer> indexSummary() {
        return knowledgeBase.chunksPerSource();
    }

    /** 클라이언트 측 답변 패턴: 검색만 해서 근거(sources)를 돌려준다 */
    @McpTool(name = "rag_search_documents",
            description = "사내 정책·RAG 운영·장애 대응 문서, 자전거 카탈로그, 스프링 부트 가이드에서 질문과 관련된 문서 조각을 검색합니다.",
            annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false))
    public List<RagKnowledgeBase.Source> searchDocuments(
            @McpToolParam(description = "검색할 질문 또는 키워드") String query,
            @McpToolParam(description = "가져올 문서 수. 기본값 5, 최대 10", required = false) Integer topK,
            @McpToolParam(description = "선택 필터. tech_docs, product_catalog 중 하나", required = false) String category) {
        return knowledgeBase.search(query, topK == null ? 5 : topK, category);
    }

    /**
     * 서버 측 답변 패턴. McpMeta는 '특수 파라미터'라서 입력 스키마에 나타나지 않는다(모델은 모른다).
     * 클라이언트가 ToolContext → _meta로 보낸 실행 문맥(사용자 ID, 대화 ID)을 여기서 꺼내 쓴다.
     */
    @McpTool(name = "rag_answer_question",
            description = "질문에 대해 서버가 문서를 검색하고 근거 기반 답변까지 만들어 반환합니다. 사용자가 '서버에서 답변'을 명시적으로 요청할 때만 사용하세요.",
            annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    idempotentHint = false, openWorldHint = false))
    public String answerQuestion(
            @McpToolParam(description = "답변할 질문") String question,
            @McpToolParam(description = "선택 필터. tech_docs, product_catalog 중 하나", required = false) String category,
            McpMeta meta) {
        // 클라이언트가 보낸 _meta 키를 전부 찍는다 — 허용 목록 밖의 값(password 등)이 오지 않았는지 서버에서 확인
        log.info("rag_answer_question 호출 — _meta 키={}, userId={}",
                meta == null ? null : meta.meta().keySet(), meta == null ? null : meta.get("userId"));
        return answerService.answer(question, category);
    }
}
