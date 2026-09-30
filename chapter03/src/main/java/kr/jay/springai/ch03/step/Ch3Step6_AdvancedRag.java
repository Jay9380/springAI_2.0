package kr.jay.springai.ch03.step;

import kr.jay.springai.ch03.rag.KnowledgeBase;
import kr.jay.springai.ch03.rag.RagService;
import kr.jay.springai.ch03.support.ChatConsole;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * [Step 6 · 3.7.7] Advanced RAG 대화 — RagService에 스트리밍으로 묻는다.
 *
 * <p>실행: {@code --spring.ai.cli.step=ch3-step6}
 * <br>해 볼 질문: "보안 문의는 누구에게 하나요?" (마스킹 확인), "출퇴근용 자전거 추천해 줘",
 * "오늘 점심 뭐 먹지?" (근거 없음 → 거절 안내)
 */
@Component
@ConditionalOnProperty(prefix = "spring.ai.cli", name = "step", havingValue = "ch3-step6")
public class Ch3Step6_AdvancedRag implements CommandLineRunner {

    private final KnowledgeBase knowledgeBase;
    private final RagService ragService;

    public Ch3Step6_AdvancedRag(KnowledgeBase knowledgeBase, RagService ragService) {
        this.knowledgeBase = knowledgeBase;
        this.ragService = ragService;
    }

    @Override
    public void run(String... args) {
        knowledgeBase.ensureIndexed();
        ChatConsole.run("[Ch3] Step6: Advanced RAG (stream)", input ->
                ragService.stream(input).doOnNext(System.out::print).blockLast());
    }
}
