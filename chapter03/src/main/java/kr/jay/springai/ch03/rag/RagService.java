package kr.jay.springai.ch03.rag;

import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.postretrieval.document.DocumentPostProcessor;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import reactor.core.publisher.Flux;

/**
 * [3.7.3 예제 3.49] Advanced RAG — RetrievalAugmentationAdvisor에 네 단계 모듈을 끼운다.
 *
 * <pre>
 *   ① 전처리   RewriteQueryTransformer     장황한 질문 → 검색용 문장 (LLM 호출 1회, temperature 0)
 *   ② 검색     VectorStoreDocumentRetriever 후보는 넉넉히 (topK 10, 임계값 0.50)
 *   ③ 후처리   isActive 필터 + 키워드 필터   규칙으로 좁힌다
 *   ④ 생성     ContextualQueryAugmenter    [문서 + 질문]을 한국어 템플릿으로 합쳐 user 메시지를 다시 쓴다
 * </pre>
 * 모든 단계가 어드바이저 '안'에서 일어나므로 이 서비스를 쓰는 쪽은 {@code chatClient.prompt().user(q)}만 안다.
 */
public class RagService {

    /** [예제 3.56] 문서가 있을 때 — {context}와 {query}는 반드시 있어야 한다 (없으면 생성 시 예외) */
    static final String RAG_TEMPLATE = """
            당신은 IT 전문 기술 지원 AI입니다.
            아래의 [기술 문서]를 기반으로 사용자의 질문에 친절하게 답변해 주세요.
            문서에 없는 내용은 지어내지 말고 솔직하게 모른다고 답해 주세요.

            [기술 문서]
            {context}

            [질문]
            {query}

            [답변]
            """;

    /** 검색 결과가 하나도 없을 때 — 기본값은 영어 거절문("...outside your knowledge base...")이라 한국어로 교체 */
    static final String EMPTY_TEMPLATE = """
            사용자의 질문에 대한 근거 문서를 찾지 못했습니다.
            문서에 없는 내용은 답할 수 없다고 정중히 말하고, 검색 가능한 범위(보안·RAG 운영·장애 대응 정책,
            자전거 카탈로그, 스프링 부트 실행 가이드) 안에서 다시 질문하도록 안내하세요.
            """;

    private final ChatClient chatClient;
    private final VectorStoreDocumentRetriever retriever;
    private final List<DocumentPostProcessor> postProcessors;

    public RagService(ChatClient.Builder builder, VectorStore vectorStore) {
        // ① 전처리: 질문 재작성은 '일관성'이 중요 → temperature 0. 빌더는 clone()해서 원본을 오염시키지 않는다
        RewriteQueryTransformer rewrite = RewriteQueryTransformer.builder()
                .chatClientBuilder(builder.clone().defaultOptions(ChatOptions.builder().temperature(0.0)))
                .targetSearchSystem("Spring AI RAG vector store")
                .build();

        // ② 검색: 후보는 넉넉히. 정밀한 거르기는 ③에서
        this.retriever = VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .topK(10)
                .similarityThreshold(0.50)
                .build();

        // ③ 후처리: 폐기 문서 제외(규칙) → 키워드 필터
        DocumentPostProcessor activeOnly = (query, docs) -> docs.stream()
                .filter(d -> !Boolean.FALSE.equals(d.getMetadata().get("isActive")))
                .toList();
        this.postProcessors = List.of(activeOnly, new KeywordFilteringPostProcessor());

        // ④ 생성: 한국어 템플릿 두 개. allowEmptyContext(false) → 문서가 없으면 EMPTY_TEMPLATE로 거절 안내
        ContextualQueryAugmenter augmenter = ContextualQueryAugmenter.builder()
                .promptTemplate(new PromptTemplate(RAG_TEMPLATE))
                .emptyContextPromptTemplate(new PromptTemplate(EMPTY_TEMPLATE))
                .allowEmptyContext(false)
                .build();

        Advisor advisor = RetrievalAugmentationAdvisor.builder()
                .queryTransformers(rewrite)
                .documentRetriever(retriever)
                .documentPostProcessors(postProcessors)
                .queryAugmenter(augmenter)
                .taskExecutor(daemonExecutor())
                .build();

        this.chatClient = builder.clone().defaultAdvisors(advisor).build();
    }

    /**
     * 함정 (2.0.1 소스 확인, 책에 없음): RetrievalAugmentationAdvisor는 실행기를 주지 않으면
     * 'ai-advisor-' 이름의 ThreadPoolTaskExecutor를 스스로 만든다. 이 스레드들은 데몬이 아니고
     * 스프링 빈도 아니어서 아무도 종료하지 않는다 → 웹 서버가 없는 CLI 앱이 할 일을 다 마쳐도 JVM이 끝나지 않는다.
     * (jstack으로 확인: main은 끝났고 "ai-advisor-1" 스레드만 살아 있었다)
     * 해결: 데몬 스레드 실행기를 직접 넘긴다. 웹 애플리케이션이라면 스프링 빈으로 등록해 종료 시 함께 닫히게 한다.
     */
    private static TaskExecutor daemonExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("rag-");
        executor.setCorePoolSize(2);
        executor.setDaemon(true);
        executor.initialize();
        return executor;
    }

    /** 스트리밍 답변 (Step6, final) */
    public Flux<String> stream(String question) {
        return chatClient.prompt().user(question).stream().content();
    }

    /** 동기 답변 */
    public String call(String question) {
        return chatClient.prompt().user(question).call().content();
    }

    /**
     * 화면에 '근거 문서'를 먼저 보여 주기 위한 검색 (책 3.8.4 printRetrievedDocuments).
     * 어드바이저와 같은 검색기·후처리를 쓰되, 질문 재작성(LLM 호출)은 건너뛴다.
     */
    public List<Document> retrieve(String question) {
        Query query = new Query(question);
        List<Document> docs = retriever.retrieve(query);
        for (DocumentPostProcessor p : postProcessors) {
            docs = p.process(query, docs);
        }
        return docs;
    }

    public ChatClient chatClient() {
        return chatClient;
    }
}
