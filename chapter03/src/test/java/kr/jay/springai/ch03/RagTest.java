package kr.jay.springai.ch03;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import kr.jay.springai.ch03.rag.KeywordFilteringPostProcessor;
import kr.jay.springai.ch03.rag.RagService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;

/**
 * 3.7 RAG — 가짜 채팅 모델이 '마지막으로 받은 프롬프트'를 들여다보면
 * 어드바이저가 무엇을 끼워 넣었는지(증강된 프롬프트) 정확히 확인할 수 있다.
 */
class RagTest {

    private final List<String> prompts = new ArrayList<>();

    /** 질문 재작성 요청에는 '재작성된 질문'을, 그 외에는 고정 답을 돌려주는 가짜 모델 */
    private final ChatModel fakeChat = prompt -> {
        String text = prompt.getContents();
        prompts.add(text);
        String reply = text.contains("Rewritten query") || text.contains("rewrite") ? rewriteOf(text) : "답변";
        return new ChatResponse(List.of(new Generation(new AssistantMessage(reply))));
    };

    /** 재작성 프롬프트 안의 원래 질문을 그대로 돌려준다 (검색이 원래 질문으로 되도록) */
    private static String rewriteOf(String prompt) {
        return prompt.contains("보안") ? "보안 문의 담당자" : prompt.contains("장애") ? "긴급 장애 보고" : "스프링 부트 실행";
    }

    private SimpleVectorStore storeWith(Document... docs) {
        SimpleVectorStore store = SimpleVectorStore.builder(new FakeEmbeddingModel()).build();
        store.add(List.of(docs));
        return store;
    }

    private final Document security = new Document("보안 문의 담당자: 김보안 [MASKED_EMAIL]",
            Map.of("category", "tech_docs", "isActive", true));
    private final Document incident = new Document("긴급 장애 발생 시 30분 안에 보고",
            Map.of("category", "tech_docs", "isActive", true));
    private final Document oldIncident = new Document("(폐기) 장애 보고는 하루 안에",
            Map.of("category", "tech_docs", "isActive", false));
    private final Document bike = new Document("출퇴근용 자전거", Map.of("category", "product_catalog", "isActive", true));

    @Test
    void augmentedPrompt_containsRetrievedDocumentAndQuestion_inKoreanTemplate() {
        RagService rag = new RagService(ChatClient.builder(fakeChat), storeWith(security, bike));

        rag.call("보안 관련 문의는 누구에게 하나요?");

        String finalPrompt = prompts.get(prompts.size() - 1);
        assertThat(finalPrompt)
                .contains("[기술 문서]").contains("김보안")          // 검색된 문서가 {context} 자리에
                .contains("[질문]");                                  // 질문이 {query} 자리에
    }

    @Test
    void emptyRetrieval_sendsRefusalTemplate_insteadOfQuestion() {
        // 실패 경로: 근거 문서가 0건이면 allowEmptyContext(false)라 질문 대신 거절 안내 템플릿이 간다
        RagService rag = new RagService(ChatClient.builder(fakeChat), storeWith(bike));

        rag.call("스프링 부트 실행 방법은?");

        String finalPrompt = prompts.get(prompts.size() - 1);
        assertThat(finalPrompt).contains("근거 문서를 찾지 못했습니다").doesNotContain("[기술 문서]");
    }

    @Test
    void retrieve_dropsInactiveDocuments_andKeepsOnlyUrgentOnesForUrgentQuestion() {
        RagService rag = new RagService(ChatClient.builder(fakeChat), storeWith(incident, oldIncident, security));

        List<Document> docs = rag.retrieve("긴급 장애 보고는 어떻게 하나요?");

        assertThat(docs).extracting(Document::getText)
                .containsExactly("긴급 장애 발생 시 30분 안에 보고");    // 폐기 문서·'긴급' 없는 문서 제외
    }

    @Test
    void retrieve_dropsInactiveDocuments_evenWithoutKeywordFilter() {
        // '긴급'이 없는 질문 → 키워드 필터는 아무것도 안 거른다. 폐기 문서는 isActive 후처리만이 막는다
        RagService rag = new RagService(ChatClient.builder(fakeChat), storeWith(incident, oldIncident));

        List<Document> docs = rag.retrieve("장애 보고는 어떻게 하나요?");

        assertThat(docs).extracting(Document::getText)
                .contains("긴급 장애 발생 시 30분 안에 보고").doesNotContain("(폐기) 장애 보고는 하루 안에");
    }

    @Test
    void keywordPostProcessor_passesEverythingForNormalQuestion() {
        var p = new KeywordFilteringPostProcessor();
        assertThat(p.process(new Query("장애 보고 방법"), List.of(incident, security))).hasSize(2);
        assertThat(p.process(new Query("긴급 상황"), List.of(incident, security))).containsExactly(incident);
    }

    @Test
    void naiveRag_filterWithWrongConstant_isSilentlyIgnored() {
        // 실패 경로(책 p.242): 다른 모듈의 FILTER_EXPRESSION 상수를 쓰면 오류 없이 필터가 무시된다
        ChatClient client = ChatClient.builder(fakeChat)
                .defaultAdvisors(QuestionAnswerAdvisor.builder(storeWith(security, bike))
                        .searchRequest(SearchRequest.builder().topK(5).build()).build())
                .build();

        client.prompt().user("자전거 알려줘")
                .advisors(a -> a.param(QuestionAnswerAdvisor.FILTER_EXPRESSION, "category == 'tech_docs'"))
                .call().content();
        String right = prompts.get(prompts.size() - 1);

        client.prompt().user("자전거 알려줘")
                .advisors(a -> a.param(VectorStoreDocumentRetriever.FILTER_EXPRESSION, "category == 'tech_docs'"))
                .call().content();
        String wrong = prompts.get(prompts.size() - 1);

        assertThat(right).doesNotContain("출퇴근용 자전거");   // 올바른 상수: tech_docs만
        assertThat(wrong).contains("출퇴근용 자전거");         // 잘못된 상수: 필터가 무시되어 자전거도 들어감
    }
}
