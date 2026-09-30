package kr.jay.springai.ch03.step;

import java.util.List;
import java.util.TreeMap;

import kr.jay.springai.ch03.etl.EtlPipeline;
import kr.jay.springai.ch03.etl.PiiMaskingTransformer;
import kr.jay.springai.ch03.etl.SourceDocuments;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.MetadataMode;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * [Step 2 · 3.3] 정제와 청킹 — 마스킹 → 포맷 → 분할을 단계별 개수와 함께 본다.
 *
 * <p>실행: {@code --spring.ai.cli.step=ch3-step2}  (모델 호출 없음)
 *
 * <p>볼 것
 * <ol>
 *   <li>마스킹 후: 이메일·전화번호가 [MASKED_EMAIL]·[MASKED_PHONE]으로 바뀜</li>
 *   <li>청킹 후: 긴 정책 문서 하나가 여러 조각으로. 조각마다 chunk_index·parent_document_id·total_chunks가 붙음</li>
 *   <li>같은 조각이라도 '누가 보느냐'에 따라 모양이 다름 — EMBED(임베딩용) vs INFERENCE(LLM용)</li>
 * </ol>
 */
@Component
@ConditionalOnProperty(prefix = "spring.ai.cli", name = "step", havingValue = "ch3-step2")
public class Ch3Step2_DocumentTransformers implements CommandLineRunner {

    private final SourceDocuments sources;
    private final PiiMaskingTransformer masking;
    private final EtlPipeline pipeline;

    public Ch3Step2_DocumentTransformers(SourceDocuments sources, PiiMaskingTransformer masking, EtlPipeline pipeline) {
        this.sources = sources;
        this.masking = masking;
        this.pipeline = pipeline;
    }

    @Override
    public void run(String... args) {
        List<Document> raw = sources.readAllDocuments();
        List<Document> masked = masking.transform(raw);
        List<Document> chunks = pipeline.transform(raw);
        System.out.printf("원본 %d개 → 마스킹 후 %d개 → 청크 %d개%n%n", raw.size(), masked.size(), chunks.size());

        System.out.println("── 마스킹 결과 (정책 문서의 연락처 부분)");
        masked.get(0).getText().lines().filter(l -> l.contains("MASKED")).forEach(l -> System.out.println("  " + l));

        System.out.println("\n── 청크 메타데이터 (정책 문서에서 나온 조각들)");
        chunks.stream()
                .filter(c -> "policy-docs.txt".equals(c.getMetadata().get("source")))
                .forEach(c -> System.out.printf("  chunk %s/%s  길이 %d자  | %s%n",
                        c.getMetadata().get("chunk_index"), c.getMetadata().get("total_chunks"),
                        c.getText().length(), Ch3Step1_DocumentReaders.preview(c.getText())));

        Document sample = chunks.get(0);
        System.out.println("\n── 한 조각의 세 가지 얼굴 (3.3.2 ContentFormatter)");
        System.out.println("[getText()] 본문만:\n" + indent(sample.getText()));
        System.out.println("[EMBED] 임베딩 모델이 보는 글 (ingestedAt·charset·isActive 제외):\n"
                + indent(sample.getFormattedContent(MetadataMode.EMBED)));
        System.out.println("[INFERENCE] LLM이 보는 글 (ingestedAt·charset·parent_document_id 제외):\n"
                + indent(sample.getFormattedContent(MetadataMode.INFERENCE)));
        System.out.println("전체 메타데이터: " + new TreeMap<>(sample.getMetadata()));
    }

    private static String indent(String s) {
        return "    " + s.replace("\n", "\n    ") + "\n";
    }
}
