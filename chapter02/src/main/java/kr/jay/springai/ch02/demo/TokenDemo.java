package kr.jay.springai.ch02.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * [2.5] 토큰 — 직접 세어 보기.
 *
 * <p>실행: {@code --spring.ai.cli.step=ch2-demo-tokens}
 *
 * <p>책 표 2.10은 "안녕하세요. 만나서 반갑습니다."(한글 17자)가 최신 토크나이저에서 9토큰,
 * 영어 문장은 7토큰이라고 비교한다. 토큰 수는 모델(토크나이저)마다 다르므로,
 * 우리가 쓰는 qwen3.5:4b에서 실제로 몇 토큰인지 응답 메타데이터(promptTokens)로 확인한다.
 *
 * <p>promptTokens에는 우리가 쓴 글 말고도 채팅 템플릿의 특수 토큰(역할 표시 등)이 더해진다.
 * 그래서 '빈 질문'의 토큰 수를 먼저 재고 차이로 비교한다.
 */
@Component
@ConditionalOnProperty(prefix = "spring.ai.cli", name = "step", havingValue = "ch2-demo-tokens")
public class TokenDemo implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(TokenDemo.class);

    private final ChatClient chatClient;

    public TokenDemo(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @Override
    public void run(String... args) {
        int base = promptTokens(".");
        log.info("기준(빈 질문에 가까운 '.') promptTokens = {}", base);

        measure("안녕하세요. 만나서 반갑습니다.", base);
        measure("Hello, Nice to meet you.", base);
        measure("스프링 AI 2.0은 스프링 부트 4.x를 필수로 요구합니다.", base);
        measure("Spring AI 2.0 requires Spring Boot 4.x.", base);

        maxTokensCutsTheAnswer();
    }

    private void measure(String text, int base) {
        int tokens = promptTokens(text);
        log.info("글자 {}자 → 입력 토큰 약 {}개  | {}", text.length(), tokens - base, text);
    }

    /** 답은 짧게(1토큰) 받아 비용을 아끼고, 입력 토큰 수만 본다. */
    private int promptTokens(String text) {
        ChatResponse r = chatClient.prompt()
                .user(text)
                .options(OllamaChatOptions.builder().numPredict(1))
                .call()
                .chatResponse();
        Integer t = (r == null) ? null : r.getMetadata().getUsage().getPromptTokens();
        return t == null ? -1 : t;
    }

    /**
     * [2.4.6 MaxTokens] 출력 토큰 상한(Ollama: num_predict)을 낮게 주면 답이 중간에 잘린다.
     * 이때 finishReason이 'length'가 된다. 운영에서 "답이 뚝 끊긴다"면 가장 먼저 볼 곳.
     */
    private void maxTokensCutsTheAnswer() {
        ChatResponse r = chatClient.prompt()
                .user("스프링 프레임워크의 역사를 자세히 설명해줘.")
                .options(OllamaChatOptions.builder().numPredict(20))
                .call()
                .chatResponse();
        log.info("numPredict=20 → finishReason={}, 출력 토큰={}, 답변='{}'",
                r.getResult().getMetadata().getFinishReason(),
                r.getMetadata().getUsage().getCompletionTokens(),
                r.getResult().getOutput().getText());
    }
}
