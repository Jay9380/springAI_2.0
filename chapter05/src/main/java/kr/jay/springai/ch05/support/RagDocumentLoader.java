package kr.jay.springai.ch05.support;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.JsonReader;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.core.io.ClassPathResource;

/**
 * [5.5.2 Step1] 3장 RAG의 오프라인 파이프라인을 '순수 서비스'로 옮긴 것 — 읽기 → 마스킹 → 청킹.
 *
 * <p>MCP 서버로 공개할 때의 원칙(5장-2 노트 '도메인 로직 보호'): MCP 애너테이션은 얇은 어댑터에만 두고,
 * 실제 로직은 MCP를 모르는 평범한 클래스에 둔다. 그래야 MCP 없이도 테스트하고 재사용할 수 있다.
 *
 * <p>이 장의 초점은 MCP라서 리더는 코어에 있는 TextReader·JsonReader만 쓴다
 * (마크다운은 텍스트로 읽고, HTML은 뺐다 — 리더별 차이는 3장에서 다뤘다).
 */
public class RagDocumentLoader {

    private static final Pattern EMAIL = Pattern.compile("[a-zA-Z0-9._-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
    private static final Pattern PHONE = Pattern.compile("01[0-9]-\\d{3,4}-\\d{4}");

    public List<Document> loadChunks() {
        List<Document> raw = new ArrayList<>();
        raw.addAll(text("policy-docs.txt", "tech_docs"));
        raw.addAll(text("spring-boot-guide.md", "tech_docs"));
        raw.addAll(new JsonReader(new ClassPathResource("data/bikes.json"),
                json -> Map.of("source", "bikes.json", "category", "product_catalog",
                        "bikeModel", json.get("model"), "isActive", json.get("active")),
                "description").get("/store/bikes"));

        // 마스킹은 반드시 청킹 '전에' (3장 TransformerTest 참고)
        List<Document> masked = raw.stream()
                .map(d -> new Document(d.getId(), mask(d.getText()), d.getMetadata()))
                .toList();

        return TokenTextSplitter.builder()
                .withChunkSize(300)
                .withMinChunkSizeChars(80)
                .build()
                .split(masked);
    }

    private static List<Document> text(String file, String category) {
        TextReader reader = new TextReader(new ClassPathResource("data/" + file));
        Map<String, Object> meta = new HashMap<>();
        meta.put("category", category);
        meta.put("isActive", true);
        reader.getCustomMetadata().putAll(meta);
        return reader.read();
    }

    static String mask(String text) {
        return PHONE.matcher(EMAIL.matcher(text).replaceAll("[EMAIL]")).replaceAll("[PHONE]");
    }
}
