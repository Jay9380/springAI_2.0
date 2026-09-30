package kr.jay.springai.ch03.demo;

import kr.jay.springai.ch03.rag.KnowledgeBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.template.st.StTemplateRenderer;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * [3.7.2] Naive RAG — QuestionAnswerAdvisor 하나로 끝나는 가장 단순한 RAG.
 *
 * <p>실행: {@code --spring.ai.cli.step=ch3-demo-naive}
 *
 * <p>질문 → (그대로) 벡터 검색 → 문서를 프롬프트에 넣음 → 답. 전·후처리가 없다.
 * PoC·FAQ 봇에는 충분하지만, 질문이 모호하거나 문서를 더 골라야 하면 Advanced RAG(RagService)로 간다.
 */
@Component
@ConditionalOnProperty(prefix = "spring.ai.cli", name = "step", havingValue = "ch3-demo-naive")
public class NaiveRagDemo implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(NaiveRagDemo.class);

    private final KnowledgeBase knowledgeBase;
    private final ChatClient.Builder builder;

    public NaiveRagDemo(KnowledgeBase knowledgeBase, ChatClient.Builder builder) {
        this.knowledgeBase = knowledgeBase;
        this.builder = builder;
    }

    @Override
    public void run(String... args) {
        knowledgeBase.ensureIndexed();

        // [예제 3.47] 한국어 템플릿 + 구분자 < >.
        // 검색된 문서가 JSON이나 코드라서 { 가 들어 있으면 기본 구분자 { }에서 렌더링이 깨진다.
        // QuestionAnswerAdvisor의 변수 이름은 query와 question_answer_context다.
        PromptTemplate korean = PromptTemplate.builder()
                .renderer(StTemplateRenderer.builder().startDelimiterToken('<').endDelimiterToken('>').build())
                .template("""
                        <query>

                        아래 [정보]를 참고하여 사용자의 질문에 답변하십시오.
                        ---------------------
                        [정보]
                        <question_answer_context>
                        ---------------------
                        [지침]
                        1. 정보가 부족하면 "제공된 정보 내에서는 알 수 없습니다"라고 답하시오.
                        2. 답변은 해요체로 두세 문장 이내로 쓰시오.
                        """)
                .build();

        ChatClient client = builder.clone()
                .defaultAdvisors(QuestionAnswerAdvisor.builder(knowledgeBase.vectorStore())
                        .searchRequest(SearchRequest.builder().topK(4).similarityThreshold(0.5).build())
                        .promptTemplate(korean)
                        .build())
                .build();

        String q = "출퇴근용 자전거 추천해 주세요.";
        log.info("── ① 필터 없음: {}", client.prompt().user(q).call().content());

        // 런타임 동적 필터: 요청마다 검색 범위를 바꾼다 (사용자별 권한 문서 격리에 쓰는 방법)
        log.info("── ② category == 'tech_docs'로 제한: {}", client.prompt().user(q)
                .advisors(a -> a.param(QuestionAnswerAdvisor.FILTER_EXPRESSION, "category == 'tech_docs'"))
                .call().content());

        // 함정: 다른 모듈의 상수를 쓰면 오류 없이 필터가 '조용히 무시'된다 (책 p.242)
        //   QuestionAnswerAdvisor.FILTER_EXPRESSION        = "qa_filter_expression"
        //   VectorStoreDocumentRetriever.FILTER_EXPRESSION = "vector_store_filter_expression"
        log.info("── ③ 잘못된 상수로 같은 필터 (무시되어 ①과 같은 결과): {}", client.prompt().user(q)
                .advisors(a -> a.param(VectorStoreDocumentRetriever.FILTER_EXPRESSION, "category == 'tech_docs'"))
                .call().content());
    }
}
