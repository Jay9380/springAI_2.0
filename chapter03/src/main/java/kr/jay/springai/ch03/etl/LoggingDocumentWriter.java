package kr.jay.springai.ch03.etl;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentWriter;

/**
 * [3.4.2 예제 3.26] 커스텀 DocumentWriter — 적재 대신 로그로 남긴다.
 *
 * <p>DocumentWriter는 {@code Consumer<List<Document>>}다. accept() 하나만 구현한다.
 * 벡터 DB, RDBMS, 메시지 큐, 로그… '어디로 보낼지'만 바꾸면 ETL의 L(Load)을 갈아 끼울 수 있다.
 * 진짜 목적지인 VectorStore도 DocumentWriter를 상속한다 (Step4).
 */
public class LoggingDocumentWriter implements DocumentWriter {

    private static final Logger log = LoggerFactory.getLogger(LoggingDocumentWriter.class);

    private int written;

    @Override
    public void accept(List<Document> documents) {
        for (Document d : documents) {
            log.info("[write] id={} source={} chunk={} 길이={}자",
                    d.getId(), d.getMetadata().get("source"), d.getMetadata().get("chunk_index"), d.getText().length());
        }
        written += documents.size();
        log.info("[write] 총 {}건 처리", documents.size());
    }

    public int written() {
        return written;
    }
}
