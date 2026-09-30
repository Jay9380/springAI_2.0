package kr.jay.springai.ch03.step;

import java.util.List;

import kr.jay.springai.ch03.rag.KnowledgeBase;
import kr.jay.springai.ch03.rag.RagService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * [Step 5 · 3.7.3 ~ 3.7.6] Advanced RAG 구성 요소를 하나씩 눈으로 확인한다.
 *
 * <p>실행: {@code --spring.ai.cli.step=ch3-step5}
 *
 * <ol>
 *   <li>질문 재작성 결과 (RewriteQueryTransformer)</li>
 *   <li>검색 + 후처리 결과 (어떤 조각이 근거로 뽑혔나)</li>
 *   <li>답변 + 응답 메타데이터에 담긴 근거 문서 (RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT)</li>
 * </ol>
 */
@Component
@ConditionalOnProperty(prefix = "spring.ai.cli", name = "step", havingValue = "ch3-step5")
public class Ch3Step5_AdvancedRagModules implements CommandLineRunner {

    private final KnowledgeBase knowledgeBase;
    private final RagService ragService;
    private final ChatClient.Builder builder;

    public Ch3Step5_AdvancedRagModules(KnowledgeBase knowledgeBase, RagService ragService, ChatClient.Builder builder) {
        this.knowledgeBase = knowledgeBase;
        this.ragService = ragService;
        this.builder = builder;
    }

    @Override
    public void run(String... args) {
        knowledgeBase.ensureIndexed();
        String question = "안녕하세요, 궁금한 게 있는데요. 긴급 장애 대응 문서는 어떤 정보를 기록해야 하나요?";

        // ① 전처리만 따로 실행해 보기
        Query rewritten = RewriteQueryTransformer.builder()
                .chatClientBuilder(builder.clone().defaultOptions(ChatOptions.builder().temperature(0.0)))
                .targetSearchSystem("Spring AI RAG vector store")
                .build()
                .transform(new Query(question));
        System.out.println("① 원래 질문 : " + question);
        System.out.println("   재작성    : " + rewritten.text());

        // ②③ 검색 + 후처리 ('긴급'이 있으므로 키워드 필터가 '긴급'이 든 조각만 남긴다)
        List<Document> docs = ragService.retrieve(question);
        System.out.printf("%n②③ 검색·후처리 결과 %d건%n", docs.size());
        docs.forEach(d -> System.out.printf("   score %.4f | %s chunk %s | %s%n", d.getScore(),
                d.getMetadata().get("source"), d.getMetadata().get("chunk_index"),
                Ch3Step1_DocumentReaders.preview(d.getText())));

        // ④ 답변. 어드바이저는 자신이 쓴 근거 문서를 응답 메타데이터(DOCUMENT_CONTEXT)에 넣어 준다
        ChatResponse response = ragService.chatClient().prompt().user(question).call().chatResponse();
        System.out.println("\n④ 답변:\n" + response.getResult().getOutput().getText());
        Object used = response.getMetadata().get(RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT);
        if (used instanceof List<?> list) {
            System.out.printf("%n   (응답 메타데이터 rag_document_context: 근거 문서 %d건 — 출처 표시에 쓸 수 있다)%n", list.size());
        }
    }
}
