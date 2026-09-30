package kr.jay.springai.ch03.rag;

import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.postretrieval.document.DocumentPostProcessor;

/**
 * [3.7.6 예제] 후처리 모듈 — 질문에 '긴급'이 있으면 '긴급'이 들어 있는 문서만 남긴다.
 *
 * <p>DocumentPostProcessor는 기본 구현이 없다. 서비스마다 직접 만든다 (요약, 마스킹, 재정렬, 필터…).
 * 검색은 '의미'로 넉넉히 가져오고(topK↑, 임계값↓), 후처리에서 '규칙'으로 좁히는 패턴이다.
 */
public class KeywordFilteringPostProcessor implements DocumentPostProcessor {

    private static final String KEYWORD = "긴급";

    @Override
    public List<Document> process(Query query, List<Document> documents) {
        if (query.text().contains(KEYWORD)) {
            return documents.stream().filter(d -> d.getText().contains(KEYWORD)).toList();
        }
        return documents;
    }
}
