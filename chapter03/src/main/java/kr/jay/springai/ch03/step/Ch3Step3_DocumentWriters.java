package kr.jay.springai.ch03.step;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import kr.jay.springai.ch03.etl.EtlPipeline;
import kr.jay.springai.ch03.etl.LoggingDocumentWriter;
import kr.jay.springai.ch03.etl.SourceDocuments;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.writer.FileDocumentWriter;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * [Step 3 · 3.4] 적재 — 벡터 DB에 넣기 전에 '파일'과 '로그'로 먼저 써 본다.
 *
 * <p>실행: {@code --spring.ai.cli.step=ch3-step3}  (모델 호출 없음)
 * <br>결과 파일: chapter03/target/etl-output.txt
 *
 * <p>왜 파일부터? 임베딩은 시간과 비용이 든다. 청크가 예상대로 잘렸는지, 메타데이터가 제대로 붙었는지,
 * 개인정보가 빠졌는지를 사람이 먼저 눈으로 확인하면 잘못된 데이터를 벡터 DB에 넣는 일을 막을 수 있다.
 */
@Component
@ConditionalOnProperty(prefix = "spring.ai.cli", name = "step", havingValue = "ch3-step3")
public class Ch3Step3_DocumentWriters implements CommandLineRunner {

    private final SourceDocuments sources;
    private final EtlPipeline pipeline;

    public Ch3Step3_DocumentWriters(SourceDocuments sources, EtlPipeline pipeline) {
        this.sources = sources;
        this.pipeline = pipeline;
    }

    @Override
    public void run(String... args) throws Exception {
        List<Document> chunks = pipeline.transform(sources.readAllDocuments());

        // (1) 커스텀 Writer: 로그로
        LoggingDocumentWriter logWriter = new LoggingDocumentWriter();
        logWriter.write(chunks);             // write()는 accept()를 부르는 기본 메서드

        // (2) FileDocumentWriter: 파일로
        //   withDocumentMarkers=true : 문서마다 "### Doc: [번호], pages:[시작,끝]" 구분 줄
        //     페이지 값은 page_number·end_page_number 메타데이터에서 온다. PDF 리더만 이 값을 붙이므로
        //     텍스트·JSON 문서는 pages:[null,null]로 찍힌다(실측). 책이 테스트용으로 page_number를 직접 넣은 이유.
        //   MetadataMode.ALL         : 메타데이터도 함께 기록
        //   append=false             : 덮어쓰기
        Path out = Path.of("chapter03/target/etl-output.txt");
        if (!Files.exists(out.getParent())) {
            out = Path.of("target/etl-output.txt");       // chapter03 디렉터리에서 실행한 경우
        }
        Files.createDirectories(out.getParent());
        new FileDocumentWriter(out.toString(), true, MetadataMode.ALL, false).write(chunks);

        System.out.printf("%n파일로 %d개 청크 저장: %s%n%n", chunks.size(), out.toAbsolutePath());
        Files.readAllLines(out).stream().limit(14).forEach(l -> System.out.println("  " + l));
        System.out.println("  …");
    }
}
