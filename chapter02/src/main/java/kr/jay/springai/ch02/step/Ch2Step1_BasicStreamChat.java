package kr.jay.springai.ch02.step;

import kr.jay.springai.ch02.support.ChatConsole;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * [Step 1 · 2.3 / 2.3.2] 기본 스트리밍 채팅.
 *
 * <p>실행: {@code --spring.ai.cli.step=ch2-step1}
 *
 * <p>basic-chat과 딱 한 군데가 다르다: {@code .call().content()} → {@code .stream().content()}.
 * <ul>
 *   <li>call()   : String 하나를 돌려준다 — 다 만들어질 때까지 기다림</li>
 *   <li>stream() : Flux&lt;String&gt;을 돌려준다 — 토큰이 만들어지는 대로 조각조각 흘러옴</li>
 * </ul>
 * 답이 완성되는 총 시간은 같지만, 첫 글자가 보이는 시간(체감 대기 시간)이 크게 줄어든다.
 */
@Component
@ConditionalOnProperty(prefix = "spring.ai.cli", name = "step", havingValue = "ch2-step1")
public class Ch2Step1_BasicStreamChat implements CommandLineRunner {

    private final ChatClient chatClient;

    public Ch2Step1_BasicStreamChat(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @Override
    public void run(String... args) {
        ChatConsole.run("[Ch2] Step1: 기본 스트리밍 채팅 (ChatClient, stream)", input ->
                chatClient.prompt()
                        .user(input)
                        .stream()                            // 스트리밍 응답 활성화
                        .content()                           // Flux<String>: 텍스트 조각의 흐름
                        .doOnNext(System.out::print)         // 조각이 도착할 때마다 즉시 출력
                        // blockLast(): 리액티브(비동기) 흐름을 CLI의 순차 실행에 맞추는 연결점.
                        // 마지막 조각이 올 때까지 기다린다. 이게 없으면 출력 전에 다음 입력으로 넘어간다.
                        .blockLast());
    }
}
