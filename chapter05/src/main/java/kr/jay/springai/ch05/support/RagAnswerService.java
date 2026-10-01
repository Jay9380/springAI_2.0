package kr.jay.springai.ch05.support;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.ai.chat.client.ChatClient;

/**
 * [5.5.2 Step3] 서버 측 답변 패턴 — 검색과 LLM 답변을 '서버'가 모두 한다.
 *
 * <p>RAG를 MCP로 외부화할 때 '답변을 어디서 만드느냐'로 두 아키텍처가 나뉜다.
 * <ul>
 *   <li>클라이언트 측 답변 (rag_search_documents): 서버는 검색만, 클라이언트가 자기 모델로 답을 쓴다
 *       → 여러 클라이언트가 같은 데이터를 서로 다른 모델로 쓸 때</li>
 *   <li>서버 측 답변 (rag_answer_question): 서버가 답까지 → 한 주체가 비용·정책·프롬프트를 통제할 때</li>
 * </ul>
 */
public class RagAnswerService {

    private final ChatClient chatClient;
    private final RagKnowledgeBase knowledgeBase;

    public RagAnswerService(ChatClient.Builder builder, RagKnowledgeBase knowledgeBase) {
        this.chatClient = builder.build();
        this.knowledgeBase = knowledgeBase;
    }

    public String answer(String question, String category) {
        List<RagKnowledgeBase.Source> sources = knowledgeBase.search(question, 4, category);
        if (sources.isEmpty()) {
            return "근거 문서를 찾지 못했습니다. 검색 가능한 범위(정책·RAG 운영·장애 대응, 자전거 카탈로그, 스프링 부트 가이드) 안에서 다시 질문해 주세요.";
        }
        String context = sources.stream()
                .map(s -> "[" + s.source() + "] " + s.text())
                .collect(Collectors.joining("\n---\n"));
        return chatClient.prompt()
                .system("아래 [문서]만 근거로 한국어로 간결하게 답하세요. 문서에 없는 내용은 모른다고 답하세요. 출처 파일명을 함께 적으세요.")
                .user(u -> u.text("[문서]\n{context}\n\n[질문]\n{question}")
                        .param("context", context)
                        .param("question", question))
                .call()
                .content();
    }
}
