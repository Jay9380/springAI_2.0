package kr.jay.springai.appendix.eval;

import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/**
 * [부록 B] 평가 대상 RAG의 지식. 책의 예("비밀번호 3개월마다 변경", "2025년에 개정됐다"는 근거 없는 주장)를 재현할 수 있게 구성했다.
 * 3장처럼 ETL을 거치지 않고 짧은 문서 몇 개를 바로 적재한다(평가가 주제라서).
 */
@Configuration
public class KnowledgeBase {

    static final List<Document> DOCS = List.of(
            new Document("[보안 정책] 모든 임직원은 3개월마다 사내 계정 비밀번호를 변경해야 한다. 비밀번호는 12자 이상이며 직전 3개와 같을 수 없다.",
                    java.util.Map.of("source", "security-policy.md")),
            new Document("[장애 대응] 장애가 나면 발생 시각, 영향 범위, 조치 내용, 재발 방지책 네 가지를 기록하고 24시간 안에 보고한다.",
                    java.util.Map.of("source", "incident-guide.md")),
            new Document("[재고 정책] 모든 SKU의 안전재고는 50개다. 현재 재고가 안전재고보다 적으면 부족분만큼 재발주한다.",
                    java.util.Map.of("source", "inventory-policy.md")),
            new Document("[휴가 정책] 연차는 입사 1년 차에 15일이 주어지고, 2년마다 1일씩 늘어 최대 25일까지 쓸 수 있다.",
                    java.util.Map.of("source", "leave-policy.md")));

    /** @Lazy: 평가 단계를 실행할 때만 임베딩한다 (A 단계·테스트는 임베딩 모델 없이 돈다). */
    @Bean
    @Lazy
    VectorStore vectorStore(EmbeddingModel embeddingModel) {
        SimpleVectorStore store = SimpleVectorStore.builder(embeddingModel).build();
        store.add(DOCS);
        return store;
    }
}
