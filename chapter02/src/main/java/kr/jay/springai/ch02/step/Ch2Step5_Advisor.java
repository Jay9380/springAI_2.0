package kr.jay.springai.ch02.step;

import kr.jay.springai.ch02.advisor.ElapsedTimeAdvisor;
import kr.jay.springai.ch02.support.ChatConsole;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * [Step 5 · 2.8] 어드바이저 체인 — 내장(SimpleLoggerAdvisor) + 커스텀(ElapsedTimeAdvisor).
 *
 * <p>실행: {@code --spring.ai.cli.step=ch2-step5}
 *
 * <p>대화 코드는 Step1과 한 글자도 다르지 않다. 로그와 응답 시간이라는 '운영 관심사'가
 * 어드바이저로 분리되어, 비즈니스 코드를 건드리지 않고 기능이 늘었다. 이것이 어드바이저의 존재 이유다.
 *
 * <p>SimpleLoggerAdvisor는 요청·응답 전체를 DEBUG 레벨로 남긴다 (application.yml에서 advisor 패키지를 debug로 켜 둠).
 * 로그에서 확인할 것: 요청에 담긴 메시지, 응답의 usage(토큰 수), 그리고 [응답 시간].
 */
@Component
@ConditionalOnProperty(prefix = "spring.ai.cli", name = "step", havingValue = "ch2-step5")
public class Ch2Step5_Advisor implements CommandLineRunner {

    private final ChatClient chatClient;

    public Ch2Step5_Advisor(ChatClient.Builder builder) {
        this.chatClient = builder
                .defaultAdvisors(
                        new SimpleLoggerAdvisor(),    // 내장: 요청과 응답 DEBUG 로그 (order 0)
                        new ElapsedTimeAdvisor())     // 커스텀: 응답 시간 측정 (order 100)
                .build();
    }

    @Override
    public void run(String... args) {
        ChatConsole.run("[Ch2] Step5: 어드바이저 체인 (Logger + ElapsedTime, stream)", input ->
                chatClient.prompt()
                        .user(input)
                        .stream()
                        .content()
                        .doOnNext(System.out::print)
                        .blockLast());
    }
}
