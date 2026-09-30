package kr.jay.springai.ch03.step;

import java.util.List;

import kr.jay.springai.ch03.rag.KnowledgeBase;
import kr.jay.springai.ch03.rag.RagService;
import kr.jay.springai.ch03.support.ChatConsole;
import org.springframework.ai.document.Document;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * [3.8.4] 최종 RAG CLI — 근거 문서를 먼저 보여 주고, 그다음 답한다.
 *
 * <p>실행: {@code ./mvnw -pl chapter03 spring-boot:run}  (기본 단계)
 *
 * <p>RAG의 신뢰성은 '어디서 왔는가'를 보여 줄 때 생긴다. 답만 보여 주면 사용자는 환각인지 근거가 있는지 구분할 수 없다.
 * 그래서 [검색된 문서]에 점수·출처·분류·청크 번호를 먼저 출력하고 [최종 답변]을 스트리밍한다.
 *
 * <pre>
 *   오프라인: KnowledgeBase.ensureIndexed()   읽기 → 마스킹·포맷·청킹 → 임베딩·저장
 *   런타임  : RagService.retrieve()           근거 보여 주기
 *             RagService.stream()             재작성 → 검색 → 후처리 → 증강 → 생성
 * </pre>
 */
@Component
@ConditionalOnProperty(prefix = "spring.ai.cli", name = "step", havingValue = "ch3-final", matchIfMissing = true)
public class Ch3RagCliChatbotApplication implements CommandLineRunner {

    private final KnowledgeBase knowledgeBase;
    private final RagService ragService;

    public Ch3RagCliChatbotApplication(KnowledgeBase knowledgeBase, RagService ragService) {
        this.knowledgeBase = knowledgeBase;
        this.ragService = ragService;
    }

    @Override
    public void run(String... args) {
        int chunks = knowledgeBase.ensureIndexed().size();
        System.out.println("─".repeat(60));
        System.out.println(" Spring AI RAG CLI Chatbot  (Chapter 3, Final)");
        System.out.println(" 문서: 보안·RAG 운영·장애 대응 정책, 자전거 카탈로그, 스프링 부트 가이드, 블로그 글");
        System.out.println(" 적재된 청크: " + chunks + "개 (bge-m3 임베딩, SimpleVectorStore)");
        System.out.println("─".repeat(60));

        ChatConsole.run("[Ch3] Final: 근거 표시형 RAG", input -> {
            printRetrievedDocuments(ragService.retrieve(input));
            System.out.println("[최종 답변]");
            ragService.stream(input).doOnNext(System.out::print).blockLast();
        });
    }

    private static void printRetrievedDocuments(List<Document> docs) {
        System.out.println("\n[검색된 문서] " + docs.size() + "건");
        if (docs.isEmpty()) {
            System.out.println("  (근거 없음 — 답변은 거절 안내가 된다)");
        }
        for (Document d : docs) {
            System.out.printf("  score %.4f | %s | %s | %s | chunk %s%n", d.getScore(),
                    d.getMetadata().get("source"), d.getMetadata().get("category"),
                    d.getMetadata().get("sourceType"), d.getMetadata().get("chunk_index"));
        }
        System.out.println();
    }
}
