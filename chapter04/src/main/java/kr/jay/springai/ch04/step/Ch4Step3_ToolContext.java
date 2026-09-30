package kr.jay.springai.ch04.step;

import java.util.Map;
import java.util.UUID;

import kr.jay.springai.ch04.examples.Chapter4ToolCallbacks;
import kr.jay.springai.ch04.support.ChatConsole;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * [Step 3 · 4.2.4 예제 4.35] ToolContext와 returnDirect.
 *
 * <p>실행: {@code --spring.ai.cli.step=ch4-step3}
 * <br>해 볼 말: "이번 대화 학습 주제는 Tool Calling이야. 세션 요약해줘"
 *
 * <ul>
 *   <li>ToolContext : 사용자 이름·대화 ID처럼 '앱이 아는 값'을 도구 실행 시점에 넣는다.
 *       모델에게는 전송되지 않는다 → 모델이 다른 사용자의 ID를 지어내 넣는 사고가 원천 차단된다</li>
 *   <li>returnDirect : 도구 결과를 모델이 다시 다듬지 않고 그대로 사용자에게 돌려준다 → LLM 호출 1회 절약</li>
 * </ul>
 * 출력이 모델 문장이 아니라 도구가 만든 '세션 요약' 틀 그대로 나오면 직접 반환된 것이다.
 */
@Component
@ConditionalOnProperty(prefix = "spring.ai.cli", name = "step", havingValue = "ch4-step3")
public class Ch4Step3_ToolContext implements CommandLineRunner {

    private final ChatClient chatClient;
    private final String conversationId = UUID.randomUUID().toString();

    public Ch4Step3_ToolContext(ChatClient.Builder builder) {
        this.chatClient = builder.clone()
                .defaultSystem("""
                        당신은 CLI 세션 정보를 요약하는 AI 어시스턴트입니다.
                        사용자가 세션 요약을 요청하면 session_summary 툴을 호출합니다.
                        """)
                .defaultTools(Chapter4ToolCallbacks.sessionSummary(true))   // returnDirect = true
                .build();
    }

    @Override
    public void run(String... args) {
        ChatConsole.run("[Ch4] Step3: ToolContext + returnDirect (세션 요약)", input ->
                System.out.print(chatClient.prompt()
                        .user(input)
                        // 요청 단위로 ToolContext 전달. 이 Map은 도구의 BiFunction 두 번째 인자로만 들어간다
                        .toolContext(Map.of("userName", "chapter4-reader", "conversationId", conversationId))
                        .call()
                        .content()));
    }
}
