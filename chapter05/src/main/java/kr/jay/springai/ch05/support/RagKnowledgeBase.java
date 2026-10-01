package kr.jay.springai.ch05.support;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

/**
 * RAG 지식 베이스 — 적재와 검색 (MCP를 모르는 순수 로직).
 *
 * <p>[5.5.2 Step1] 책은 @PostConstruct에서 '기동 시점에' 인덱싱한다. 이유는 둘:
 * 첫 도구 호출 때 큰 지연이 생기지 않게, 그리고 인덱싱이 실패하면 서버 기동 자체를 실패시켜 빨리 드러내게.
 * 여기서는 서버 설정(RagServerConfig)이 빈을 만들 때 index()를 부른다.
 */
public class RagKnowledgeBase {

    /** 검색 결과 한 건 — MCP 도구의 출력(JSON)이 된다 */
    public record Source(String source, String category, Object chunkIndex, double score, String text) {
    }

    private final VectorStore vectorStore;
    private final RagDocumentLoader loader;
    private final Map<String, Integer> chunksPerSource = new LinkedHashMap<>();
    private boolean indexed;

    public RagKnowledgeBase(VectorStore vectorStore, RagDocumentLoader loader) {
        this.vectorStore = vectorStore;
        this.loader = loader;
    }

    public synchronized int index() {
        if (indexed) {                                   // 멱등성: 여러 번 불러도 한 번만 적재
            return chunksPerSource.values().stream().mapToInt(Integer::intValue).sum();
        }
        List<Document> chunks = loader.loadChunks();
        if (chunks.isEmpty()) {
            throw new IllegalStateException("적재할 RAG 문서가 없습니다 — 서버 기동을 중단합니다");
        }
        vectorStore.add(chunks);
        chunks.forEach(c -> chunksPerSource.merge(String.valueOf(c.getMetadata().get("source")), 1, Integer::sum));
        indexed = true;
        return chunks.size();
    }

    public Map<String, Integer> chunksPerSource() {
        return Map.copyOf(chunksPerSource);
    }

    /** topK는 1~10으로 자르고, category가 있으면 메타데이터 필터를 건다 */
    public List<Source> search(String query, int topK, String category) {
        SearchRequest.Builder request = SearchRequest.builder()
                .query(query)
                .topK(Math.max(1, Math.min(topK, 10)))
                .similarityThreshold(0.50);
        if (category != null && !category.isBlank()) {
            request.filterExpression(new FilterExpressionBuilder().eq("category", category).build());
        }
        return vectorStore.similaritySearch(request.build()).stream()
                .map(d -> new Source(String.valueOf(d.getMetadata().get("source")),
                        String.valueOf(d.getMetadata().get("category")),
                        d.getMetadata().get("chunk_index"),
                        d.getScore() == null ? 0 : d.getScore(),
                        d.getText()))
                .toList();
    }
}
