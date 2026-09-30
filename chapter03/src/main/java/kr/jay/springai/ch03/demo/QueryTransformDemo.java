package kr.jay.springai.ch03.demo;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.expansion.MultiQueryExpander;
import org.springframework.ai.rag.preretrieval.query.transformation.CompressionQueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.TranslationQueryTransformer;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * [3.7.5] 전처리 모듈 — 검색 '전에' 질문을 고친다. 각 모듈은 내부에서 LLM을 한 번씩 부른다.
 *
 * <p>실행: {@code --spring.ai.cli.step=ch3-demo-query}
 *
 * <p>왜? 벡터 검색은 질문 문장 자체를 임베딩한다. "그곳", "아까 말한 거" 같은 지시어, 인사말, 다른 언어는
 * 검색 품질을 떨어뜨린다. 그래서 검색에 알맞은 문장으로 먼저 바꾼다.
 */
@Component
@ConditionalOnProperty(prefix = "spring.ai.cli", name = "step", havingValue = "ch3-demo-query")
public class QueryTransformDemo implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(QueryTransformDemo.class);

    private final ChatClient.Builder builder;

    public QueryTransformDemo(ChatClient.Builder builder) {
        this.builder = builder;
    }

    @Override
    public void run(String... args) {
        // 변환은 창의성보다 일관성 → temperature 0
        ChatClient.Builder exact = builder.clone().defaultOptions(ChatOptions.builder().temperature(0.0));

        // Compression: 대화 기록 + 후속 질문 → 혼자서도 뜻이 통하는 질문
        Query followUp = new Query("그곳의 두 번째로 큰 도시는?",
                List.of(new UserMessage("덴마크의 수도는?"), new AssistantMessage("코펜하겐입니다.")),
                java.util.Map.of());
        log.info("── Compression : {}  →  {}", followUp.text(),
                CompressionQueryTransformer.builder().chatClientBuilder(exact).build().transform(followUp).text());

        // Rewrite: 장황·모호한 질문 → 검색용 문장
        String chatty = "안녕하세요, 질문이 좀 있는데요… 머신러닝 공부 중인데 LLM이 뭐예요?";
        log.info("── Rewrite     : {}  →  {}", chatty,
                RewriteQueryTransformer.builder().chatClientBuilder(exact).targetSearchSystem("vector store")
                        .build().transform(new Query(chatty)).text());

        // Translation: 문서가 영어라면 질문도 영어로
        log.info("── Translation : 스프링 부트 실행 방법  →  {}",
                TranslationQueryTransformer.builder().chatClientBuilder(exact).targetLanguage("English")
                        .build().transform(new Query("스프링 부트 실행 방법")).text());

        // MultiQuery: 한 질문을 표현만 다른 여러 질문으로 → 각각 검색해 합친다(재현율↑). 다양해야 하므로 temperature 0.5
        List<Query> expanded = MultiQueryExpander.builder()
                .chatClientBuilder(builder.clone().defaultOptions(ChatOptions.builder().temperature(0.5)))
                .numberOfQueries(3)
                .includeOriginal(true)
                .build()
                .expand(new Query("스프링 부트 실행 방법"));
        log.info("── MultiQuery  : {}개 (원본 포함)", expanded.size());
        expanded.forEach(q -> log.info("     - {}", q.text()));
    }
}
