package kr.jay.springai.ch02.step;

import kr.jay.springai.ch02.support.ChatConsole;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * [Step 2 · 2.4] 시스템 프롬프트로 페르소나 부여.
 *
 * <p>실행: {@code --spring.ai.cli.step=ch2-step2}
 *
 * <p>Step1의 챗봇은 아무 역할도 없어서 같은 질문에 교과서 같은 답을 낸다.
 * 여기서는 빌더의 {@code defaultSystem(...)}으로 system 메시지를 '한 번' 고정한다.
 * 그러면 이후 모든 {@code prompt().user(...)} 호출 앞에 이 지시가 자동으로 붙는다.
 *
 * <p>system 메시지는 모델이 가장 강하게 따르는 지시다. 사용자가 무엇을 입력하든
 * 대화 전체를 관통하는 규칙(말투, 전문성, 모를 때의 태도)을 여기에 둔다.
 */
@Component
@ConditionalOnProperty(prefix = "spring.ai.cli", name = "step", havingValue = "ch2-step2")
public class Ch2Step2_PromptTemplate implements CommandLineRunner {

    // 텍스트 블록(""")은 여러 줄 프롬프트를 읽기 좋게 쓰는 자바 15+ 문법이다.
    static final String SYSTEM_PROMPT = """
            당신은 시니어 자바 개발자이자 기술 멘토입니다.
            - 코드 예제는 Java 21 이상의 최신 문법을 활용합니다.
            - 답변은 단계적이고 근거를 함께 설명합니다.
            - 확실하지 않은 내용은 "모른다"고 솔직하게 답합니다.
            """;

    private final ChatClient chatClient;

    public Ch2Step2_PromptTemplate(ChatClient.Builder builder) {
        this.chatClient = builder
                .defaultSystem(SYSTEM_PROMPT)      // 이 클라이언트의 모든 호출에 자동 부착
                .build();
    }

    @Override
    public void run(String... args) {
        ChatConsole.run("[Ch2] Step2: 시스템 프롬프트 (시니어 자바 멘토, stream)", input ->
                chatClient.prompt()
                        .user(input)                // 여기에는 사용자 질문만. system은 이미 붙어 있다
                        .stream()
                        .content()
                        .doOnNext(System.out::print)
                        .blockLast());
    }
}
