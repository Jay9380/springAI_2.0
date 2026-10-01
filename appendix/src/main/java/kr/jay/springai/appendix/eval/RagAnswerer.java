package kr.jay.springai.appendix.eval;

import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

/**
 * [부록 B] 평가 대상 — 3장식 RAG 답변기. 답과 함께 '검색에 쓴 문서'를 돌려준다.
 *
 * <p>평가 요청(EvaluationRequest)에는 질문·근거 문서·답 세 가지가 필요하다. 근거 문서는 QuestionAnswerAdvisor가 응답 컨텍스트의
 * {@code qa_retrieved_documents}에 넣어 두므로 거기서 꺼낸다 — "답을 만들 때 실제로 본 문서"로 평가해야 의미가 있다.
 */
public class RagAnswerer {

    public record RagResult(String question, String answer, List<Document> documents) {
    }

    static final String CAREFUL = "당신은 사내 문서 도우미입니다. 한국어로 두세 문장 이내로 답하세요.";

    private final ChatClient chatClient;

    public RagAnswerer(ChatClient.Builder builder, VectorStore vectorStore, double similarityThreshold) {
        this(builder, vectorStore, similarityThreshold, CAREFUL);
    }

    /** @param systemPrompt 생성자의 성향. 평가 실험에서는 일부러 '추정해서라도 답하라'는 과신형 생성자를 쓴다. */
    public RagAnswerer(ChatClient.Builder builder, VectorStore vectorStore, double similarityThreshold, String systemPrompt) {
        this.chatClient = builder.clone()
                .defaultSystem(systemPrompt)
                .defaultAdvisors(QuestionAnswerAdvisor.builder(vectorStore)
                        .searchRequest(SearchRequest.builder().topK(2).similarityThreshold(similarityThreshold).build())
                        .build())
                .build();
    }

    /** @param extraInstruction 평가자 피드백 등 다음 시도에 덧붙일 지시 (없으면 빈 문자열) */
    @SuppressWarnings("unchecked")
    public RagResult answer(String question, String extraInstruction) {
        String user = extraInstruction.isBlank() ? question : question + "\n\n[개선 지침]\n" + extraInstruction;
        ChatClientResponse response = chatClient.prompt().user(user).call().chatClientResponse();
        Object docs = response.context().get(QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS);
        String text = response.chatResponse() == null ? "" : response.chatResponse().getResult().getOutput().getText();
        return new RagResult(question, text, docs instanceof List<?> l ? (List<Document>) l : List.of());
    }
}
