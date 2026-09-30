package kr.jay.springai.ch03.etl;

import java.util.List;

import org.springframework.ai.document.DefaultContentFormatter;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.ContentFormatTransformer;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Component;

/**
 * [3.3.6] 변환 파이프라인 — 순서가 결과를 바꾼다.
 *
 * <pre>
 *   ① 정제(마스킹) → ② 포맷 통일 → ③ 분할(청킹) → (④ 강화: 키워드·요약, LLM 필요 — EnricherDemo)
 * </pre>
 * <ul>
 *   <li>마스킹은 분할 '전에': 자른 뒤에는 전화번호가 조각나 못 잡는다</li>
 *   <li>포맷도 분할 '전에': 잘린 모든 조각이 같은 메타데이터 규칙을 물려받는다</li>
 *   <li>강화는 분할 '후에': 잘려서 약해진 문맥을 키워드·앞뒤 요약으로 보강한다</li>
 * </ul>
 */
@Component
public class EtlPipeline {

    private final PiiMaskingTransformer masking;

    public EtlPipeline(PiiMaskingTransformer masking) {
        this.masking = masking;
    }

    /**
     * 청킹 설정 (책 3.8.2 값). 실습 문서가 짧아서 일부러 작게 잡았다.
     * <ul>
     *   <li>chunkSize 180 : 조각 하나의 목표 크기 — '토큰' 단위</li>
     *   <li>minChunkSizeChars 50 : 문장 부호에서 자를 때 최소한 이만큼(글자)은 채운다</li>
     *   <li>minChunkLengthToEmbed 20 : 이보다 짧은 조각은 버린다(글자) — 의미 없는 파편 제거</li>
     *   <li>maxNumChunks 20 : 문서 하나에서 만들 최대 조각 수</li>
     * </ul>
     */
    public TokenTextSplitter splitter() {
        return TokenTextSplitter.builder()
                .withChunkSize(180)
                .withMinChunkSizeChars(50)
                .withMinChunkLengthToEmbed(20)
                .withMaxNumChunks(20)
                .withKeepSeparator(true)
                .build();
    }

    /**
     * [3.3.2] DefaultContentFormatter — LLM에게 문서를 보여 줄 때 메타데이터를 본문 위에 "키: 값"으로 붙인다.
     * <ul>
     *   <li>excludedEmbedMetadataKeys : 임베딩할 때 뺄 키 (날짜·내부 값이 벡터를 흐리지 않게)</li>
     *   <li>excludedInferenceMetadataKeys : LLM에게 보낼 때 뺄 키 (토큰 절약·내부 정보 보호)</li>
     * </ul>
     */
    public DefaultContentFormatter formatter() {
        return DefaultContentFormatter.builder()
                .withExcludedEmbedMetadataKeys("ingestedAt", "charset", "isActive")
                .withExcludedInferenceMetadataKeys("ingestedAt", "charset", "parent_document_id")
                .build();
    }

    /** ① 마스킹 → ② 포맷 → ③ 청킹 */
    public List<Document> transform(List<Document> raw) {
        List<Document> masked = masking.transform(raw);                                   // ①
        List<Document> formatted = new ContentFormatTransformer(formatter()).transform(masked);  // ②
        return splitter().split(formatted);                                                // ③
    }
}
