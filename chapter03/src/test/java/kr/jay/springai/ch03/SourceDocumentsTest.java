package kr.jay.springai.ch03;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import kr.jay.springai.ch03.etl.SourceDocuments;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

/** 3.2 리더 — 모델 없이 동작하는 순수 파일 읽기라 단위 테스트로 확인하기 좋다. */
class SourceDocumentsTest {

    private final SourceDocuments sources = new SourceDocuments();

    @Test
    void textReader_readsWholeFileAsOneDocument_withDefaultAndCustomMetadata() {
        List<Document> docs = sources.readText().documents();
        assertThat(docs).hasSize(1);
        assertThat(docs.get(0).getMetadata())
                .containsEntry("source", "policy-docs.txt")      // TextReader 기본
                .containsKey("charset")                          // TextReader 기본
                .containsEntry("category", "tech_docs")          // 우리가 붙인 것
                .containsEntry("version", "1.0");
    }

    @Test
    void jsonReader_makesOneDocumentPerArrayElement_descriptionOnlyAsText() {
        List<Document> docs = sources.readJson().documents();
        assertThat(docs).hasSize(3);
        Document first = docs.get(0);
        assertThat(first.getText()).contains("산악자전거").doesNotContain("Trek");   // 본문은 description만
        assertThat(first.getMetadata()).containsEntry("bikeBrand", "Trek").containsEntry("bikePrice", 1200000);
    }

    @Test
    void jsonReader_marksDiscontinuedModelInactive() {
        // 단종 모델은 isActive=false — 이후 검색 필터가 이 값을 쓴다
        assertThat(sources.readJson().documents())
                .filteredOn(d -> Boolean.FALSE.equals(d.getMetadata().get("isActive")))
                .extracting(d -> d.getMetadata().get("bikeModel"))
                .containsExactly("Road Pro 7");
    }

    @Test
    void markdownReader_separatesCodeBlocks_andKeepsElementTypeUnderOwnKey() {
        List<Document> docs = sources.readMarkdown().documents();
        assertThat(docs).anyMatch(d -> "code_block".equals(d.getMetadata().get("mdElement")));
        // 리더의 'category'(요소 종류)가 업무 분류를 덮어쓰지 않았는지
        assertThat(docs).allMatch(d -> "tech_docs".equals(d.getMetadata().get("category")));
    }

    @Test
    void htmlReader_keepsArticleParagraphsOnly_dropsNavAndFooter() {
        List<Document> docs = sources.readHtml().documents();
        assertThat(docs).hasSize(3);
        assertThat(docs).noneMatch(d -> d.getText().contains("회원가입") || d.getText().contains("이용약관"));
        assertThat(docs.get(0).getMetadata()).containsKey("description");
    }

    @Test
    void everyDocument_hasCommonMetadata() {
        assertThat(sources.readAllDocuments())
                .allSatisfy(d -> assertThat(d.getMetadata())
                        .containsKeys("source", "category", "sourceType", "isActive", "ingestedAt"));
    }
}
