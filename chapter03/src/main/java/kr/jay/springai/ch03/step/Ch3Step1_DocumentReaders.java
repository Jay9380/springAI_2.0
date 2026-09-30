package kr.jay.springai.ch03.step;

import java.util.TreeMap;

import kr.jay.springai.ch03.etl.SourceDocuments;
import org.springframework.ai.document.Document;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * [Step 1 · 3.2] 원천 문서 읽기 — 네 리더의 결과와 메타데이터를 눈으로 확인한다.
 *
 * <p>실행: {@code --spring.ai.cli.step=ch3-step1}  (모델 호출 없음)
 *
 * <p>볼 것: 같은 '문서 하나'라도 리더마다 Document 개수가 다르다.
 * TextReader는 파일 전체를 1개로, JsonReader는 배열 요소마다, 마크다운은 헤더·코드블록마다,
 * HTML은 &lt;p&gt;마다 나눈다. 그리고 메타데이터가 이후의 필터·출처 표시를 결정한다.
 */
@Component
@ConditionalOnProperty(prefix = "spring.ai.cli", name = "step", havingValue = "ch3-step1")
public class Ch3Step1_DocumentReaders implements CommandLineRunner {

    private final SourceDocuments sources;

    public Ch3Step1_DocumentReaders(SourceDocuments sources) {
        this.sources = sources;
    }

    @Override
    public void run(String... args) {
        for (SourceDocuments.DocumentSet set : sources.readAll()) {
            System.out.printf("%n=== [%s] %s → Document %d개%n", set.readerName(), set.fileName(), set.documents().size());
            for (Document d : set.documents()) {
                // TreeMap: 메타데이터를 키 이름순으로 정렬해 보기 좋게
                System.out.println("  metadata : " + new TreeMap<>(d.getMetadata()));
                System.out.println("  text     : " + preview(d.getText()));
            }
        }
    }

    static String preview(String text) {
        String oneLine = text == null ? "" : text.replace('\n', ' ');
        return oneLine.length() > 80 ? oneLine.substring(0, 80) + "…" : oneLine;
    }
}
