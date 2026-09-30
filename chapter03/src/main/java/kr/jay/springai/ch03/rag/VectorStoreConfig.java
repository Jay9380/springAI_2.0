package kr.jay.springai.ch03.rag;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * [3.6.8] SimpleVectorStore — 벡터 DB 없이 메모리에서 동작하는 참조 구현.
 *
 * <p>원리가 그대로 드러나는 구현이라 학습에 좋다.
 * <ul>
 *   <li>저장: Map&lt;id, (본문, 메타데이터, 벡터)&gt;에 넣는다</li>
 *   <li>검색: 질문을 임베딩한 뒤 저장된 '모든' 벡터와 코사인 유사도를 계산해(전수 조사) 높은 순으로 topK개</li>
 *   <li>필터: 메타데이터를 필터 식에 직접 대 보며 참·거짓 판정</li>
 *   <li>save/load: JSON 파일로 저장·복원 (재시작해도 다시 임베딩하지 않게)</li>
 * </ul>
 * 문서가 수천 건을 넘으면 전수 조사가 느려진다. 그때 pgvector·OpenSearch 같은 인덱스를 가진 저장소로 바꾼다.
 * VectorStore 인터페이스만 쓰므로 이 빈 하나만 바꾸면 나머지 코드는 그대로다.
 */
@Configuration
public class VectorStoreConfig {

    @Bean
    VectorStore vectorStore(EmbeddingModel embeddingModel) {
        // EmbeddingModel: application.yml의 spring.ai.ollama.embedding.model(bge-m3)로 자동 구성된 빈
        return SimpleVectorStore.builder(embeddingModel).build();
    }
}
