package kr.jay.springai.ch03.rag;

import java.util.List;

import kr.jay.springai.ch03.etl.EtlPipeline;
import kr.jay.springai.ch03.etl.SourceDocuments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

/**
 * 오프라인 파이프라인 전체를 한 번에: 읽기 → 변환·청킹 → 임베딩·적재 (책 3.8.4 RagKnowledgeBase).
 *
 * <p>vectorStore.add(chunks) 한 줄 안에서 일어나는 일:
 * <ol>
 *   <li>조각마다 EmbeddingModel로 벡터 계산 (bge-m3 → 1024차원 float[])</li>
 *   <li>(본문, 메타데이터, 벡터)를 저장소에 기록</li>
 * </ol>
 * 여러 번 불러도 한 번만 적재한다. 같은 문서를 두 번 넣으면 검색 결과에 중복이 섞인다.
 */
@Component
public class KnowledgeBase {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBase.class);

    private final SourceDocuments sources;
    private final EtlPipeline pipeline;
    private final VectorStore vectorStore;
    private List<Document> indexed;

    public KnowledgeBase(SourceDocuments sources, EtlPipeline pipeline, VectorStore vectorStore) {
        this.sources = sources;
        this.pipeline = pipeline;
        this.vectorStore = vectorStore;
    }

    public synchronized List<Document> ensureIndexed() {
        if (indexed == null) {
            long start = System.currentTimeMillis();
            List<Document> chunks = pipeline.transform(sources.readAllDocuments());
            vectorStore.add(chunks);
            indexed = chunks;
            log.info("지식 베이스 적재 완료: 청크 {}개, 임베딩 포함 {}ms", chunks.size(), System.currentTimeMillis() - start);
        }
        return indexed;
    }

    public VectorStore vectorStore() {
        return vectorStore;
    }
}
