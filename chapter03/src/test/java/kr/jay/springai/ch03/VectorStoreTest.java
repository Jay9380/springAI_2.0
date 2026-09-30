package kr.jay.springai.ch03;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.util.List;
import java.util.Map;

import kr.jay.springai.ch03.demo.EmbeddingDemo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

/** 3.5~3.6 벡터 저장소 — 가짜 임베딩으로 검색·필터·임계값·저장을 확인한다. */
class VectorStoreTest {

    private final FakeEmbeddingModel embedding = new FakeEmbeddingModel();
    private final SimpleVectorStore store = SimpleVectorStore.builder(embedding).build();

    private final List<Document> docs = List.of(
            new Document("긴급 장애 발생 시 30분 안에 보고", Map.of("category", "tech_docs", "isActive", true)),
            new Document("보안 정책: 개인정보 마스킹", Map.of("category", "tech_docs", "isActive", true)),
            new Document("출퇴근용 도시형 자전거", Map.of("category", "product_catalog", "isActive", true, "bikePrice", 450000)),
            new Document("단종된 로드 자전거", Map.of("category", "product_catalog", "isActive", false, "bikePrice", 3800000)));

    @Test
    void search_returnsMostSimilarFirst() {
        store.add(docs);
        List<Document> r = store.similaritySearch(SearchRequest.builder().query("장애 보고 방법").topK(2).build());
        assertThat(r.get(0).getText()).contains("긴급 장애");
        assertThat(r.get(0).getScore()).isGreaterThan(r.get(1).getScore());
    }

    @Test
    void typeSafeFilter_excludesInactiveDocuments() {
        store.add(docs);
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        List<Document> r = store.similaritySearch(SearchRequest.builder()
                .query("자전거").topK(5)
                .filterExpression(b.eq("isActive", true).build())
                .build());
        assertThat(r).extracting(Document::getText).contains("출퇴근용 도시형 자전거").doesNotContain("단종된 로드 자전거");
    }

    @Test
    void comparisonFilter_alsoMatchesDocumentsMissingTheKey() {
        // 함정(실패 경로): SimpleVectorStore 필터는 없는 키를 null로 보고 null을 '가장 작은 값'으로 친다(NULLS FIRST).
        // 그래서 bikePrice가 아예 없는 정책 문서들도 "bikePrice < 1000000"을 통과한다.
        store.add(docs);
        List<Document> r = store.similaritySearch(SearchRequest.builder()
                .query("자전거").topK(5).filterExpression("bikePrice < 1000000").build());
        assertThat(r).extracting(Document::getText)
                .contains("출퇴근용 도시형 자전거", "긴급 장애 발생 시 30분 안에 보고")   // 가격 없는 문서도 섞임
                .doesNotContain("단종된 로드 자전거");
    }

    @Test
    void comparisonFilter_scopedByCategory_returnsOnlyProducts() {
        // 해결: 비교 필터는 그 키를 가진 문서 범위(category)와 함께 건다
        store.add(docs);
        List<Document> r = store.similaritySearch(SearchRequest.builder()
                .query("자전거").topK(5)
                .filterExpression("category == 'product_catalog' && bikePrice < 1000000").build());
        assertThat(r).extracting(Document::getText).containsExactly("출퇴근용 도시형 자전거");
    }

    @Test
    void tooHighThreshold_returnsNothing() {
        // 실패 경로: 임계값이 높으면 결과가 0건 → RAG에서는 '빈 컨텍스트'가 된다
        store.add(docs);
        List<Document> r = store.similaritySearch(SearchRequest.builder()
                .query("스프링 부트 실행 방법").topK(5).similarityThreshold(0.95).build());
        assertThat(r).isEmpty();
    }

    @Test
    void ollamaStyleEmbedding_usesTextOnly_notMetadata() {
        // 2.0.1 기본 getEmbeddingContent()는 getText()만 쓴다 (OllamaEmbeddingModel도 재정의하지 않음)
        // → 메타데이터(category 등)는 벡터에 들어가지 않는다
        // (SimpleVectorStore는 차원 수를 알아내려고 처음에 "Test String"을 한 번 임베딩한다 — dimensions())
        store.add(List.of(new Document("본문만", Map.of("category", "tech_docs"))));
        assertThat(embedding.inputs).contains("본문만").noneMatch(t -> t.contains("category"));
    }

    @Test
    void saveAndLoad_restoresWithoutReEmbedding(@TempDir File dir) {
        store.add(docs);
        File file = new File(dir, "store.json");
        store.save(file);
        int embeddedBefore = embedding.inputs.size();

        SimpleVectorStore restored = SimpleVectorStore.builder(embedding).build();
        restored.load(file);

        assertThat(embedding.inputs).hasSize(embeddedBefore);             // 문서는 다시 임베딩하지 않았다
        assertThat(restored.similaritySearch(SearchRequest.builder().query("보안").topK(1).build()))
                .extracting(Document::getText).containsExactly("보안 정책: 개인정보 마스킹");
    }

    @Test
    void cosine_matchesTextbookValues() {
        assertThat(EmbeddingDemo.cosine(new float[]{1, 0}, new float[]{1, 0})).isEqualTo(1.0);
        assertThat(EmbeddingDemo.cosine(new float[]{1, 0}, new float[]{0, 1})).isEqualTo(0.0);
        assertThat(EmbeddingDemo.cosine(new float[]{1, 0}, new float[]{-1, 0})).isEqualTo(-1.0);
    }
}
