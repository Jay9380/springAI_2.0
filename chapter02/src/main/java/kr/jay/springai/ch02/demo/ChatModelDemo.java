package kr.jay.springai.ch02.demo;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * [2.3] ChatModel과 ChatClient — 저수준과 고수준을 나란히 실행해 본다.
 *
 * <p>실행: {@code --spring.ai.cli.step=ch2-demo-chatclient}
 *
 * <table>
 *   <tr><th></th><th>ChatModel (드라이버)</th><th>ChatClient (클라이언트)</th></tr>
 *   <tr><td>수준</td><td>Low-Level</td><td>High-Level</td></tr>
 *   <tr><td>관심</td><td>'어떻게' 통신하나 (HTTP JSON 변환)</td><td>'무엇을' 대화하나 (프롬프트·어드바이저·출력)</td></tr>
 *   <tr><td>비유</td><td>JDBC 드라이버 / RestTemplate</td><td>JdbcTemplate</td></tr>
 * </table>
 */
@Component
@ConditionalOnProperty(prefix = "spring.ai.cli", name = "step", havingValue = "ch2-demo-chatclient")
public class ChatModelDemo implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ChatModelDemo.class);

    private final ChatModel chatModel;
    private final ChatClient friendlyClient;
    private final ChatClient analystClient;

    public ChatModelDemo(ChatModel chatModel,
                         @Qualifier("friendlyClient") ChatClient friendlyClient,
                         @Qualifier("analystClient") ChatClient analystClient) {
        this.chatModel = chatModel;
        this.friendlyClient = friendlyClient;
        this.analystClient = analystClient;
    }

    @Override
    public void run(String... args) {
        chatModelSync();
        chatModelStream();
        chatClientRequestSpec();
        runtimeOptionOverride();
        twoClientsSameModel();
    }

    /** [2.3.2 예제 2.6 / 2.3.3] ChatModel 동기 호출과 ChatResponse 해부. */
    private void chatModelSync() {
        log.info("── ① ChatModel.call(Prompt) — 저수준 동기 호출");
        // Prompt = 메시지 목록(List<Message>) + 실행 옵션(ChatOptions)
        Prompt prompt = new Prompt(List.of(
                new SystemMessage("한 문장으로만 답하세요."),
                new UserMessage("자바의 역사를 요약해줘.")));
        ChatResponse response = chatModel.call(prompt);

        // ChatResponse = generations(답변 후보 목록) + metadata(호출 전체의 부가 정보)
        //  - getResult()      : 첫 번째 Generation. 대부분의 모델은 후보를 1개만 만든다
        //  - getOutput()      : AssistantMessage (role=assistant)
        //  - Generation 메타   : 종료 이유(finishReason) 등 '이 답변' 전용 정보
        //  - Response 메타     : 모델명, 토큰 사용량(usage), 요청 제한(rateLimit) 등 '호출 전체' 정보
        log.info("답변: {}", response.getResult().getOutput().getText());
        log.info("finishReason: {}", response.getResult().getMetadata().getFinishReason());
        Usage usage = response.getMetadata().getUsage();
        // 책 2.3.3 주의: 제공자에 따라 사용량이 null이나 0일 수 있다 → 운영 코드에서는 null 체크 필수
        log.info("model={}, promptTokens={}, completionTokens={}, totalTokens={}",
                response.getMetadata().getModel(),
                usage.getPromptTokens(), usage.getCompletionTokens(), usage.getTotalTokens());
    }

    /** [2.3.2 예제 2.7] ChatModel 스트리밍. 반환 타입이 Flux<ChatResponse>다. */
    private void chatModelStream() {
        log.info("── ② ChatModel.stream(Prompt) — 토큰 조각이 ChatResponse로 여러 번 온다");
        StringBuilder sb = new StringBuilder();
        chatModel.stream(new Prompt("봄에 대한 짧은 시를 세 줄로 써줘."))
                .doOnNext(chunk -> {
                    // 각 조각(chunk)도 ChatResponse다. 텍스트가 한두 토큰씩 들어 있다.
                    String piece = chunk.getResult() == null ? null : chunk.getResult().getOutput().getText();
                    if (piece != null) {
                        sb.append(piece);
                    }
                })
                .blockLast();
        log.info("스트림으로 모은 답변:\n{}", sb);
    }

    /** [2.3.4 예제 2.13] 요청 명세는 공통, 실행만 call()/stream()으로 갈라진다. */
    private void chatClientRequestSpec() {
        log.info("── ③ 같은 요청 명세로 call()과 stream()");
        var request = friendlyClient.prompt().user("스프링의 역사를 두 문장으로 설명해줘.");

        String full = request.call().content();                               // 동기
        log.info("call(): {}", full);

        String streamed = String.join("", request.stream().content().collectList().block()); // 비동기 → 모아서 확인
        log.info("stream(): {}", streamed);
    }

    /** [2.3.4 예제 2.14 / 2.4.7] 전역 설정(temperature 0.7)을 이 요청에서만 덮어쓰기. */
    private void runtimeOptionOverride() {
        log.info("── ④ 런타임 옵션 오버라이딩");
        // 2.0에서 .options(...)는 '빌더'를 받는다. build()를 부르지 않고 그대로 넘긴다.
        String idea = friendlyClient.prompt()
                .user("SF 소설 아이디어를 3개 제안해줘. 각각 한 줄로.")
                .options(ChatOptions.builder().temperature(1.0))   // 이 요청만 창의성 ↑
                .call()
                .content();
        log.info("temperature=1.0 결과:\n{}", idea);
    }

    /** [2.3.4 예제 2.12] 같은 모델, 다른 defaultSystem — 성격이 다른 두 클라이언트. */
    private void twoClientsSameModel() {
        log.info("── ⑤ 같은 질문, 다른 클라이언트");
        String q = "재택근무의 장단점은?";
        log.info("friendlyClient: {}", friendlyClient.prompt().user(q).call().content());
        log.info("analystClient : {}", analystClient.prompt().user(q).call().content());
    }
}
