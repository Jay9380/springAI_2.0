package kr.jay.springai.ch03;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import kr.jay.springai.ch03.etl.EtlPipeline;
import kr.jay.springai.ch03.etl.PiiMaskingTransformer;
import kr.jay.springai.ch03.etl.SimpleLengthSplitter;
import kr.jay.springai.ch03.etl.SourceDocuments;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.MetadataMode;

/** 3.3 변환 — 마스킹, 청킹 메타데이터, 파이프라인 순서의 중요성을 모델 없이 확인한다. */
class TransformerTest {

    private final PiiMaskingTransformer masking = new PiiMaskingTransformer();
    private final EtlPipeline pipeline = new EtlPipeline(masking);

    @Test
    void masking_replacesEmailAndPhone_keepsMetadata() {
        Document d = new Document("연락처 kim@example.com, 010-1234-5678", Map.of("source", "a.txt"));
        Document out = masking.transform(List.of(d)).get(0);
        assertThat(out.getText()).isEqualTo("연락처 [MASKED_EMAIL], [MASKED_PHONE]");
        assertThat(out.getMetadata()).containsEntry("source", "a.txt");
        assertThat(out.getId()).isEqualTo(d.getId());
    }

    @Test
    void maskingAfterSplitting_missesPhoneNumberCutAcrossChunks() {
        // 실패 경로(순서가 틀린 파이프라인): 먼저 자르면 전화번호가 조각나 마스킹을 빠져나간다
        String text = "담당자 연락처는 010-1234-5678 입니다";          // 번호가 10글자 경계에 걸침
        List<Document> chunks = new SimpleLengthSplitter(15).split(List.of(new Document(text)));
        List<Document> maskedLate = masking.transform(chunks);

        String joined = String.join("", maskedLate.stream().map(Document::getText).toList());
        assertThat(joined).doesNotContain("[MASKED_PHONE]").contains("010-1");   // 원문 조각이 남아 있다

        // 올바른 순서: 먼저 마스킹하고 자른다
        List<Document> maskedFirst = new SimpleLengthSplitter(15).split(masking.transform(List.of(new Document(text))));
        assertThat(String.join("", maskedFirst.stream().map(Document::getText).toList()))
                .contains("[MASKED_PHONE]").doesNotContain("5678");
    }

    @Test
    void pipeline_masksBeforeSplitting_forRealPolicyDocument() {
        List<Document> chunks = pipeline.transform(new SourceDocuments().readText().documents());
        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).noneMatch(c -> c.getText().contains("010-") || c.getText().contains("@example.com"));
    }

    @Test
    void splitter_addsChunkMetadata_andKeepsOriginalMetadata() {
        List<Document> chunks = pipeline.transform(new SourceDocuments().readText().documents());
        Document first = chunks.get(0);
        assertThat(first.getMetadata())
                .containsEntry("chunk_index", 0)
                .containsEntry("total_chunks", chunks.size())
                .containsKey("parent_document_id")
                .containsEntry("source", "policy-docs.txt");     // 원본 메타데이터 상속
    }

    @Test
    void splitter_keepsShortTextWhole() {
        // 2.0 동작: chunkSize보다 짧은 글은 자르지 않고 그대로 한 조각
        List<Document> chunks = pipeline.transform(List.of(new Document("짧은 문서지만 버려지지 않을 만큼은 길게 씁니다.")));
        assertThat(chunks).hasSize(1);
    }

    @Test
    void formatter_hidesDifferentKeysForEmbeddingAndForLlm() {
        List<Document> chunks = pipeline.transform(new SourceDocuments().readText().documents());
        Document c = chunks.get(0);
        String embed = c.getFormattedContent(MetadataMode.EMBED);
        String inference = c.getFormattedContent(MetadataMode.INFERENCE);

        assertThat(embed).contains("category: tech_docs").doesNotContain("ingestedAt");
        assertThat(inference).contains("category: tech_docs").doesNotContain("parent_document_id");
        assertThat(embed).contains("parent_document_id");            // 임베딩용에는 남아 있음 (제외 목록이 다름)
    }
}
