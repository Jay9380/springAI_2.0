package kr.jay.springai.ch02.step;

import kr.jay.springai.ch02.support.ChatConsole;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * [2.2.3] 간단한 AI 애플리케이션 — call() 기반 가장 단순한 챗봇 (책의 basic-chat).
 *
 * <p>실행: {@code --spring.ai.cli.step=ch2-basic}
 *
 * <p>call()은 '동기(blocking)' 호출이다. 모델이 답을 끝까지 다 만들 때까지 기다렸다가 한 번에 받는다.
 * 4B 모델이 긴 답을 만들면 수 초~수십 초 동안 화면에 아무것도 안 나온다. 다음 Step1의 stream()과 비교해 보자.
 */
@Component
@ConditionalOnProperty(prefix = "spring.ai.cli", name = "step", havingValue = "ch2-basic")
public class Ch2Step0_BasicChat implements CommandLineRunner {

    private final ChatClient chatClient;

    public Ch2Step0_BasicChat(ChatClient.Builder builder) {
        // ChatClient.Builder: 자동설정이 만든 빈. build()로 불변(immutable) ChatClient를 만든다.
        this.chatClient = builder.build();
    }

    @Override
    public void run(String... args) {
        ChatConsole.run("[Ch2] basic-chat (ChatClient, call)", input -> {
            String answer = chatClient.prompt()
                    .user(input)
                    .call()          // ← 완성된 답이 올 때까지 여기서 멈춘다
                    .content();
            System.out.print(answer);
        });
    }
}
