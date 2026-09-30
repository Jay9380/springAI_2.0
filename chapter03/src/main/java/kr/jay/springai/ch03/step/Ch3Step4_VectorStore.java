package kr.jay.springai.ch03.step;

import java.io.File;
import java.util.List;

import kr.jay.springai.ch03.rag.KnowledgeBase;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * [Step 4 · 3.5 / 3.6] 임베딩과 벡터 검색 — 같은 질문을 필터 없이 / 필터를 걸어 검색해 비교한다.
 *
 * <p>실행: {@code --spring.ai.cli.step=ch3-step4}  (임베딩 모델 bge-m3 필요)
 *
 * <p>SearchRequest의 세 손잡이
 * <ul>
 *   <li>topK : 최대 몇 개를 가져올까</li>
 *   <li>similarityThreshold : 이 점수 미만은 버린다 (SimpleVectorStore는 코사인 유사도 그대로, -1~1)</li>
 *   <li>filterExpression : 메타데이터 조건 — 의미 검색 '전에' 후보를 좁힌다</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(prefix = "spring.ai.cli", name = "step", havingValue = "ch3-step4")
public class Ch3Step4_VectorStore implements CommandLineRunner {

    private final KnowledgeBase knowledgeBase;

    public Ch3Step4_VectorStore(KnowledgeBase knowledgeBase) {
        this.knowledgeBase = knowledgeBase;
    }

    @Override
    public void run(String... args) {
        knowledgeBase.ensureIndexed();
        VectorStore store = knowledgeBase.vectorStore();

        String question = "단종된 자전거도 추천해 줄 수 있나요?";

        // (1) 기본: 필터 없음 — 단종 모델(isActive=false)도 섞여 나온다
        print("basic (topK 5, threshold 0.50, 필터 없음)", store.similaritySearch(SearchRequest.builder()
                .query(question).topK(5).similarityThreshold(0.50).build()));

        // (2) 타입 안전 필터: FilterExpressionBuilder로 조건을 '코드'로 만든다 (오타를 컴파일러가 잡는다)
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        Filter.Expression activeOnly = b.and(
                b.in("category", "tech_docs", "product_catalog"),
                b.eq("isActive", true)).build();
        print("typeSafeFilter (category IN [tech_docs, product_catalog] AND isActive == true)",
                store.similaritySearch(SearchRequest.builder()
                        .query(question).topK(5).similarityThreshold(0.50).filterExpression(activeOnly).build()));

        // (3) 문자열 필터: SQL과 비슷한 문법. 사람이 읽기 쉽지만 오타는 실행 시점에야 드러난다
        //   함정: SimpleVectorStore는 '없는 키'를 null로 보고 null을 가장 작은 값으로 친다(NULLS FIRST).
        //   "bikePrice < 1000000"만 쓰면 가격이 아예 없는 정책·블로그 문서까지 통과한다.
        //   → 비교 필터는 그 키를 가진 문서 범위(category)와 함께 건다.
        print("stringFilter (bikePrice < 1000000 만) — 가격 없는 문서도 섞인다", store.similaritySearch(SearchRequest.builder()
                .query("출퇴근용 자전거").topK(3).filterExpression("bikePrice < 1000000").build()));
        print("stringFilter (category == 'product_catalog' && bikePrice < 1000000)", store.similaritySearch(SearchRequest.builder()
                .query("출퇴근용 자전거").topK(3)
                .filterExpression("category == 'product_catalog' && bikePrice < 1000000").build()));

        // (4) 임계값의 함정: 너무 높이면 '아무것도' 안 나온다 → RAG에서 '빈 컨텍스트'가 된다 (3.7.6)
        print("threshold 0.95 — 너무 높은 임계값", store.similaritySearch(SearchRequest.builder()
                .query(question).topK(5).similarityThreshold(0.95).build()));

        // (5) SimpleVectorStore 저장: 다음 실행 때 load()하면 다시 임베딩하지 않아도 된다
        if (store instanceof SimpleVectorStore simple) {
            File file = new File(new File("chapter03/target").isDirectory() ? "chapter03/target" : "target", "vector-store.json");
            simple.save(file);
            System.out.printf("%n저장: %s (%,d bytes) — 벡터가 JSON 숫자 배열로 들어 있다%n", file.getPath(), file.length());
        }
    }

    private static void print(String title, List<Document> results) {
        System.out.printf("%n── %s → %d건%n", title, results.size());
        for (Document d : results) {
            System.out.printf("  score %.4f | %-15s | active=%-5s | %s%n",
                    d.getScore(), d.getMetadata().get("source"), d.getMetadata().get("isActive"),
                    Ch3Step1_DocumentReaders.preview(d.getText()));
        }
    }
}
