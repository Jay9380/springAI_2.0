package kr.jay.springai.ch02.demo;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.AdvisorParams;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.converter.ListOutputConverter;
import org.springframework.ai.converter.MapOutputConverter;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.stereotype.Component;

/**
 * [2.6] 구조화한 출력 — 비정형 텍스트를 자바 객체로.
 *
 * <p>실행: {@code --spring.ai.cli.step=ch2-demo-structured}
 *
 * <p>두 가지 방식이 있다.
 * <ul>
 *   <li><b>프롬프트 기반</b> (기본): 변환기의 getFormat() 지시문 + JSON 스키마를 사용자 메시지 뒤에 '글'로 붙인다.
 *       모든 모델에서 동작하지만, 작은 모델은 지시를 어길 수 있다.</li>
 *   <li><b>네이티브</b> (2.6.5): 스키마를 모델 API의 파라미터(Ollama는 format)로 보낸다.
 *       모델 출력 자체가 제약되므로 훨씬 안정적이다. 지원 모델에서만 동작.</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(prefix = "spring.ai.cli", name = "step", havingValue = "ch2-demo-structured")
public class StructuredOutputDemo implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(StructuredOutputDemo.class);

    /**
     * [예제 2.47] @JsonPropertyOrder로 필드 순서를 정한다.
     * 모델은 JSON을 앞에서부터 한 토큰씩 쓴다. 그래서 reasoning(이유)을 먼저 쓰게 하면
     * 결론(genre, score)을 내리기 전에 생각을 정리하는 효과가 난다 — JSON 안의 '생각의 사슬'.
     */
    @JsonPropertyOrder({"reasoning", "genre", "recommendationScore"})
    public record MovieAnalysis(String reasoning, String genre, int recommendationScore) {
    }

    /** [예제 2.54] 네이티브 구조화 출력 예제용 */
    public record BookInfo(String title, String author, String summary, String isbn) {
    }

    private final ChatClient chatClient;

    public StructuredOutputDemo(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @Override
    public void run(String... args) {
        showWhatIsSentToTheModel();
        // 작은 모델은 형식 지시를 가끔 어긴다. 한 항목이 실패해도 나머지 실습은 계속 보이도록 감싼다.
        safely("① 단일 객체", this::singleObject);
        safely("② 제네릭 List", this::genericList);
        safely("③ Map", this::mapConverter);
        safely("④ List(CSV)", this::listConverter);
        safely("⑤ 파이프 구분 변환기", this::customConverter);
        safely("⑥ 네이티브 구조화 출력", this::nativeStructuredOutput);
    }

    private void safely(String name, Runnable step) {
        try {
            step.run();
        }
        catch (RuntimeException e) {
            // 운영 코드라면 여기서 재시도하거나(validateSchema), 네이티브 모드로 바꾸거나, 사용자에게 안내한다
            log.warn("── {} 실패: {} — 모델이 형식 지시를 어겼을 가능성이 크다", name, e.getClass().getSimpleName());
        }
    }

    /** [예제 2.45] 프레임워크가 몰래 붙이는 지시문을 직접 본다. */
    private void showWhatIsSentToTheModel() {
        var converter = new BeanOutputConverter<>(MovieAnalysis.class);
        log.info("── ⓪ 사용자 질문 뒤에 자동으로 붙는 형식 지시문:\n{}", converter.getFormat());
    }

    /** [예제 2.48 Case 1] 단일 객체 — 클래스만 넘기면 된다. */
    private void singleObject() {
        MovieAnalysis m = chatClient.prompt()
                .user(u -> u.text("영화 '{title}'을 분석해줘.").param("title", "인터스텔라"))
                .call()
                .entity(MovieAnalysis.class, spec -> spec.useProviderStructuredOutput());
        log.info("── ① 단일 객체: {}", m);
    }

    /**
     * [예제 2.48 Case 2] 제네릭 List — ParameterizedTypeReference가 필요하다.
     * 자바는 컴파일 후 제네릭 타입 정보를 지운다(type erasure). List.class만으로는 '무엇의 List'인지 알 수 없어서,
     * 익명 클래스 {} 트릭으로 List&lt;MovieAnalysis&gt;라는 정보를 런타임까지 전달한다.
     *
     * <p>여기서는 네이티브 모드를 쓰지 않는다. qwen3.5:4b에 '최상위가 배열'인 스키마를 네이티브로 보내면
     * 빈 배열 []이 돌아왔다(실측). 스프링 AI 문서도 OpenAI는 최상위 배열 스키마를 받지 않는다고 경고한다.
     * 배열이 필요하면 프롬프트 기반을 쓰거나, record Movies(List&lt;MovieAnalysis&gt; items)처럼 한 번 감싼다.
     */
    private void genericList() {
        List<MovieAnalysis> list = chatClient.prompt()
                .user(u -> u.text("{year}년도 최고의 영화 3편을 분석해줘.").param("year", "2014"))
                .call()
                .entity(new ParameterizedTypeReference<List<MovieAnalysis>>() {
                });
        log.info("── ② List<MovieAnalysis> ({}개)", list == null ? 0 : list.size());
        if (list != null) {
            list.forEach(m -> log.info("   {}", m));
        }
    }

    /** [예제 2.49] 키가 정해지지 않은 JSON → Map. */
    private void mapConverter() {
        Map<String, Object> rates = chatClient.prompt()
                .user("미국 달러, 일본 엔화, 유로의 원화 대비 대략적인 환율을 JSON으로 줘. 키는 통화 코드.")
                .call()
                .entity(new MapOutputConverter());
        log.info("── ③ Map: {}", rates);
    }

    /** [예제 2.50] 가벼운 목록 → 쉼표 구분(CSV) 텍스트를 List<String>으로. JSON보다 토큰을 덜 쓴다. */
    private void listConverter() {
        List<String> flavors = chatClient.prompt()
                .user("인기 있는 아이스크림 맛 5가지를 나열해줘.")
                .call()
                .entity(new ListOutputConverter(new DefaultConversionService()));
        log.info("── ④ List(CSV): {}", flavors);
    }

    /** [예제 2.52] 직접 만든 파이프 구분 변환기. */
    private void customConverter() {
        List<String> cities = chatClient.prompt()
                .user("한국의 광역시 5곳을 '도시, 특징' 형태로 알려줘.")
                .call()
                .entity(new PipeDelimitedListConverter());
        log.info("── ⑤ 파이프 구분 변환기: {}", cities);
    }

    /**
     * [예제 2.55] AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT —
     * 기존 코드를 거의 안 바꾸고 네이티브 모드로 전환하는 방법. (예제 2.56처럼 defaultAdvisors에 넣으면 전역 적용)
     */
    private void nativeStructuredOutput() {
        BookInfo book = chatClient.prompt()
                .advisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
                .user("'객체지향의 사실과 오해' 책 정보를 요약해줘.")
                .call()
                .entity(BookInfo.class);
        log.info("── ⑥ 네이티브 구조화 출력: {}", book);
    }
}
