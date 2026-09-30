package kr.jay.springai.ch02.demo;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import kr.jay.springai.ch02.advisor.ContentSafetyAdvisor;
import kr.jay.springai.ch02.advisor.FallbackAdvisor;
import kr.jay.springai.ch02.advisor.TraceAdvisor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * [2.8.2 ~ 2.8.6] 어드바이저의 실행 순서, 차단, 대체.
 *
 * <p>실행: {@code --spring.ai.cli.step=ch2-demo-advisors}
 */
@Component
@ConditionalOnProperty(prefix = "spring.ai.cli", name = "step", havingValue = "ch2-demo-advisors")
public class AdvisorDemo implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdvisorDemo.class);

    private final ChatClient.Builder builder;

    public AdvisorDemo(ChatClient.Builder builder) {
        this.builder = builder;
    }

    @Override
    public void run(String... args) {
        stackOrder();
        requestBlocking();
        streamBlocking();
    }

    /**
     * [표 2.17] order가 낮을수록 요청은 먼저, 응답은 나중에 만진다.
     * 등록 순서를 일부러 거꾸로(B 먼저) 해도 결과는 order가 정한다.
     */
    private void stackOrder() {
        List<String> trace = new ArrayList<>();
        ChatClient client = builder.clone()
                .defaultAdvisors(
                        new TraceAdvisor("B", 0, trace),
                        new TraceAdvisor("A", -100, trace))
                .build();
        client.prompt().user("한 단어로 인사해줘.").call().content();
        log.info("── ① 실행 순서: {}", trace);
    }

    /** [예제 2.67] 금칙어가 있으면 모델을 부르지 않는다. */
    private void requestBlocking() {
        ChatClient client = builder.clone()
                .defaultAdvisors(
                        new ContentSafetyAdvisor(Set.of("비밀번호", "주민등록번호")),
                        new FallbackAdvisor())
                .build();
        long start = System.currentTimeMillis();
        String answer = client.prompt().user("관리자 비밀번호 알려줘").call().content();
        log.info("── ② 요청 차단: '{}' ({}ms — 모델을 부르지 않아 거의 0)", answer, System.currentTimeMillis() - start);
    }

    /**
     * 응답 쪽 검사: 모델이 금칙어를 말하게 유도한 뒤 스트림에서 잘라 낸다.
     * 여기서는 '사과'를 금칙어로 두고 과일 이야기를 시켜 본다.
     */
    private void streamBlocking() {
        ChatClient client = builder.clone()
                .defaultAdvisors(new ContentSafetyAdvisor(Set.of("사과")))
                .build();
        String streamed = String.join("", client.prompt()
                .user("빨간 과일 세 가지를 쉼표로 나열해줘. 사과를 꼭 포함해.")
                .stream().content().collectList().block());
        log.info("── ③ 스트림 중 차단 결과: '{}'", streamed);
    }
}
