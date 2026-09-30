package kr.jay.springai.ch03;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import kr.jay.springai.ch03.etl.LoggingDocumentWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.writer.FileDocumentWriter;

/** 3.4 DocumentWriter — 쓰기 결과를 파일로 확인한다. */
class WriterTest {

    private final List<Document> docs = List.of(
            new Document("첫 번째 조각", Map.of("source", "a.txt", "page_number", 1, "end_page_number", 1)),
            new Document("두 번째 조각", Map.of("source", "a.txt", "page_number", 2, "end_page_number", 2)));

    @Test
    void fileWriter_writesMarkersMetadataAndText(@TempDir Path dir) throws Exception {
        Path out = dir.resolve("out.txt");
        new FileDocumentWriter(out.toString(), true, MetadataMode.ALL, false).write(docs);

        String content = Files.readString(out);
        assertThat(content)
                .contains("### Doc: 0").contains("### Doc: 1")          // 문서 구분 줄
                .contains("pages:[1,1]").contains("pages:[2,2]")
                .contains("source: a.txt")
                .contains("첫 번째 조각").contains("두 번째 조각");
    }

    @Test
    void fileWriter_overwritesByDefault_appendsWhenAsked(@TempDir Path dir) throws Exception {
        Path out = dir.resolve("out.txt");
        new FileDocumentWriter(out.toString(), false, MetadataMode.NONE, false).write(docs);
        new FileDocumentWriter(out.toString(), false, MetadataMode.NONE, false).write(docs);
        long afterOverwrite = count(Files.readString(out), "첫 번째 조각");

        new FileDocumentWriter(out.toString(), false, MetadataMode.NONE, true).write(docs);
        long afterAppend = count(Files.readString(out), "첫 번째 조각");

        assertThat(afterOverwrite).isEqualTo(1);
        assertThat(afterAppend).isEqualTo(2);
    }

    @Test
    void customWriter_receivesAllDocuments() {
        LoggingDocumentWriter writer = new LoggingDocumentWriter();
        writer.write(docs);
        assertThat(writer.written()).isEqualTo(2);
    }

    private static long count(String s, String needle) {
        return s.split(needle, -1).length - 1L;
    }
}
