package kr.jay.springai.ch03.demo;

import java.util.List;

import kr.jay.springai.ch03.etl.EtlPipeline;
import kr.jay.springai.ch03.etl.SourceDocuments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.model.transformer.KeywordMetadataEnricher;
import org.springframework.ai.model.transformer.SummaryMetadataEnricher;
import org.springframework.ai.model.transformer.SummaryMetadataEnricher.SummaryType;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * [3.3.3 ~ 3.3.4] LLM으로 메타데이터를 '강화'하는 변환기 — 키워드와 앞뒤 요약.
 *
 * <p>실행: {@code --spring.ai.cli.step=ch3-demo-enricher}  (LLM 호출: 조각 수 × 변환기 수)
 *
 * <p>비용 주의: 조각 하나마다 LLM을 부른다. 청크 1만 개면 호출 1만 번이다.
 * 로컬 모델이라도 GPU 시간이 든다. 여기서는 정책 문서 조각만 골라 돌린다.
 */
@Component
@ConditionalOnProperty(prefix = "spring.ai.cli", name = "step", havingValue = "ch3-demo-enricher")
public class EnricherDemo implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(EnricherDemo.class);

    static final String KEYWORD_TEMPLATE = """
            {context_str}

            위 문서의 핵심 키워드 5개를 한국어로 뽑아 쉼표로 구분해 한 줄로만 답하세요.
            """;

    static final String SUMMARY_TEMPLATE = """
            다음은 문서의 한 부분입니다:
            {context_str}

            이 부분의 핵심 주제와 등장하는 대상을 한국어 한 문장으로 요약하세요. 머리말 없이 요약만 쓰세요.
            """;

    private final ChatModel chatModel;
    private final SourceDocuments sources;
    private final EtlPipeline pipeline;

    public EnricherDemo(ChatModel chatModel, SourceDocuments sources, EtlPipeline pipeline) {
        this.chatModel = chatModel;
        this.sources = sources;
        this.pipeline = pipeline;
    }

    @Override
    public void run(String... args) {
        List<Document> chunks = pipeline.transform(sources.readText().documents());   // 정책 문서 조각들
        log.info("정책 문서 조각 {}개에 강화 적용", chunks.size());

        // [3.3.3] 키워드: excerpt_keywords 메타데이터 → 키워드 필터·하이브리드 검색의 재료
        //   기본 템플릿은 영어라서, 실측 결과 마지막 조각은 키워드가 영어(failover, graceful degradation…)로 나왔다.
        //   커스텀 템플릿에는 {context_str}(조각 본문 자리)이 꼭 있어야 하고, 이때 keywordCount는 무시되므로
        //   개수를 프롬프트에 직접 쓴다.
        List<Document> withKeywords = KeywordMetadataEnricher.builder(chatModel)
                .keywordsTemplate(new PromptTemplate(KEYWORD_TEMPLATE))
                .build()
                .transform(chunks);

        // [3.3.4] 앞·현재·뒤 요약: 잘린 조각이 앞뒤 문맥을 잃는 문제를 '물리적 중복' 대신 '요약'으로 보완
        //   PREVIOUS → prev_section_summary (첫 조각은 없음)
        //   CURRENT  → section_summary
        //   NEXT     → next_section_summary (마지막 조각은 없음)
        //   기본 영어 템플릿으로는 모든 요약이 "Based on the content provided, here is a summary…"로 시작하는
        //   영어 문장이 되었다(실측). 한국어 문서면 한국어 템플릿을 준다.
        //   MetadataMode.NONE: 요약할 때 메타데이터는 빼고 본문만 보여 준다 (chunk_index 같은 값이 요약에 섞이지 않게)
        List<Document> enriched = new SummaryMetadataEnricher(chatModel,
                List.of(SummaryType.PREVIOUS, SummaryType.CURRENT, SummaryType.NEXT),
                SUMMARY_TEMPLATE, MetadataMode.NONE)
                .transform(withKeywords);

        for (Document d : enriched) {
            log.info("── chunk {}", d.getMetadata().get("chunk_index"));
            log.info("   excerpt_keywords     : {}", d.getMetadata().get("excerpt_keywords"));
            log.info("   prev_section_summary : {}", short80(d.getMetadata().get("prev_section_summary")));
            log.info("   section_summary      : {}", short80(d.getMetadata().get("section_summary")));
            log.info("   next_section_summary : {}", short80(d.getMetadata().get("next_section_summary")));
        }
        // 확인할 것: 앞 조각의 next_section_summary와 뒤 조각의 section_summary가 같은 내용이다 (서로 맞물림)
    }

    private static String short80(Object o) {
        if (o == null) {
            return "(없음)";
        }
        String s = o.toString().replace('\n', ' ');
        return s.length() > 80 ? s.substring(0, 80) + "…" : s;
    }
}
