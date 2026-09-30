package kr.jay.springai.ch03.etl;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.JsonReader;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.reader.jsoup.JsoupDocumentReader;
import org.springframework.ai.reader.jsoup.config.JsoupDocumentReaderConfig;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * [3.2] DocumentReader — 형식이 다른 네 원천을 모두 'Document 목록'으로 읽는다.
 *
 * <p>ETL의 E(Extract). 형식은 달라도 결과는 같은 모양이다:
 * <pre>
 *   Document = id + text(본문) + metadata(Map)
 * </pre>
 * 본문은 '의미로 찾을 것'(임베딩 대상), 메타데이터는 '정확히 거를 것'(필터·출처 표시)이다.
 * 여기서 붙인 메타데이터가 이후 필터링, 출처 표기, 폐기 문서 제외에 모두 쓰인다.
 *
 * <p>모든 문서에 공통 메타데이터를 붙인다 (책 3.8.1):
 * source(파일명), category(업무 분류), sourceType(원천 형식), isActive(사용 여부), ingestedAt(적재일)
 */
@Component
public class SourceDocuments {

    /** 읽은 결과를 원천별로 묶어 보여 주기 위한 값 객체 */
    public record DocumentSet(String readerName, String fileName, List<Document> documents) {
    }

    private final String today = LocalDate.now().toString();

    /** 네 원천을 모두 읽는다. */
    public List<DocumentSet> readAll() {
        return List.of(readText(), readJson(), readMarkdown(), readHtml());
    }

    /** 원천 구분 없이 Document만 한 줄로 펼친다. */
    public List<Document> readAllDocuments() {
        return readAll().stream().flatMap(set -> set.documents().stream()).toList();
    }

    /**
     * [3.2.1] TextReader — 파일 전체를 Document '하나'로.
     * 기본 메타데이터 source(파일명)·charset이 자동으로 붙고, getCustomMetadata()에 넣은 값이 합쳐진다.
     * 파일을 통째로 메모리에 올리므로 큰 파일은 뒤의 청킹이 필수다.
     */
    public DocumentSet readText() {
        TextReader reader = new TextReader(data("policy-docs.txt"));
        reader.getCustomMetadata().putAll(common("tech_docs", "text"));
        reader.getCustomMetadata().put("version", "1.0");
        return new DocumentSet("TextReader", "policy-docs.txt", reader.read());
    }

    /**
     * [3.2.2] JsonReader — 배열 요소마다 Document 하나.
     * <ul>
     *   <li>"description"만 본문으로 쓴다(jsonKeysToUse). 의미 검색 대상은 설명 문장이다</li>
     *   <li>브랜드·모델·가격·판매 여부는 JsonMetadataGenerator로 메타데이터에 넣는다. 정확히 거를 값이다</li>
     *   <li>get("/store/bikes"): JSON Pointer(RFC 6901)로 읽을 위치를 지정</li>
     * </ul>
     * 실행해 보면 본문이 "description: 험한 산길을…"처럼 '키: 값' 형태로 만들어진다.
     * 키 이름도 임베딩에 섞이므로, 영어 키를 쓴 JSON이라면 본문 키를 의미 있는 이름으로 두는 편이 낫다.
     */
    public DocumentSet readJson() {
        JsonReader reader = new JsonReader(data("bikes.json"),
                json -> {
                    Map<String, Object> m = new HashMap<>(common("product_catalog", "json"));
                    m.put("source", "bikes.json");
                    m.put("bikeBrand", json.get("brand"));
                    m.put("bikeModel", json.get("model"));
                    m.put("bikePrice", json.get("price"));
                    m.put("isActive", json.get("active"));      // 단종 모델은 false → 검색에서 뺄 수 있다
                    return m;
                },
                "description");
        return new DocumentSet("JsonReader", "bikes.json", reader.get("/store/bikes"));
    }

    /**
     * [3.2.4] MarkdownDocumentReader — 헤더·코드 블록·인용문 단위로 나눠 읽는다.
     * withIncludeCodeBlock(false): 코드 블록을 별도 Document로 분리 → '코드 자체'를 찾는 검색에 유리.
     *
     * <p>주의: 이 리더는 자기 메타데이터 'category'에 header_1, code_block 같은 '요소 종류'를 넣는다.
     * 우리가 쓰는 업무 분류 category와 이름이 겹치므로, 요소 종류는 mdElement로 옮기고 category를 덮어쓴다.
     */
    public DocumentSet readMarkdown() {
        MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
                .withHorizontalRuleCreateDocument(true)   // --- 가로줄에서 문서를 나눔
                .withIncludeCodeBlock(false)              // 코드 블록은 별도 Document
                .withIncludeBlockquote(false)             // 인용문도 별도 Document
                .build();
        List<Document> docs = new MarkdownDocumentReader(data("spring-boot-guide.md"), config).get();
        return new DocumentSet("MarkdownDocumentReader", "spring-boot-guide.md",
                withCommon(docs, "spring-boot-guide.md", "tech_docs", "markdown"));
    }

    /**
     * [3.2.5] JsoupDocumentReader — CSS 선택자로 '본문만' 골라낸다.
     * selector("article p"): 메뉴(nav)·푸터(footer)의 "로그인 | 회원가입" 같은 잡음을 버린다.
     * metadataTags: &lt;meta name="description"&gt; 같은 태그 값을 메타데이터로 가져온다.
     */
    public DocumentSet readHtml() {
        JsoupDocumentReaderConfig config = JsoupDocumentReaderConfig.builder()
                .selector("article p")
                .groupByElement(true)                     // <p> 하나마다 Document 하나
                .metadataTags(List.of("description", "keywords"))
                .build();
        List<Document> docs = new JsoupDocumentReader(data("my-page.html"), config).get();
        return new DocumentSet("JsoupDocumentReader", "my-page.html",
                withCommon(docs, "my-page.html", "blog", "html"));
    }

    // ── 공통 메타데이터 ─────────────────────────────────────────

    private Map<String, Object> common(String category, String sourceType) {
        Map<String, Object> m = new HashMap<>();
        m.put("category", category);
        m.put("sourceType", sourceType);
        m.put("isActive", true);
        m.put("ingestedAt", today);
        return m;
    }

    /** 리더가 만든 Document에 공통 메타데이터를 덧붙인 새 Document를 만든다 (id와 본문은 그대로). */
    private List<Document> withCommon(List<Document> docs, String source, String category, String sourceType) {
        List<Document> result = new ArrayList<>();
        for (Document d : docs) {
            Map<String, Object> m = new HashMap<>(d.getMetadata());
            Object element = m.remove("category");               // 마크다운 리더의 요소 종류
            if (element != null) {
                m.put("mdElement", element);
            }
            m.putAll(common(category, sourceType));
            m.put("source", source);
            result.add(new Document(d.getId(), d.getText(), m));
        }
        return result;
    }

    private static Resource data(String fileName) {
        return new ClassPathResource("data/" + fileName);
    }
}
