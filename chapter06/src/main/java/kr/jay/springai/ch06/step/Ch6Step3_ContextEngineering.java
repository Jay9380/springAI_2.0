package kr.jay.springai.ch06.step;

import java.util.UUID;

import kr.jay.springai.ch06.context.ContextEngineeredAgent;
import kr.jay.springai.ch06.context.ContextEngineeredAgent.Role;
import kr.jay.springai.ch06.context.OrderTools;
import kr.jay.springai.ch06.context.ProjectFileTools;
import kr.jay.springai.ch06.support.ChatConsole;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * [Step 3 · 6.2] 컨텍스트 엔지니어링 — 전달 영역 vs 집행 영역.
 *
 * <p>실행: {@code --spring.ai.cli.step=ch6-context --ch6.role=viewer}  (또는 operator)
 * <br>해 볼 말:
 * <ul>
 *   <li>"C-42 고객 주문 보여주고 ORD-1001 취소해줘" — viewer는 취소 툴이 없다 / operator는 취소된다</li>
 *   <li>"ORD-1002도 취소해줘" — 툴이 '실패: 배송 중'을 돌려주고, 모델이 그 안내(반품 절차)를 따른다</li>
 *   <li>"secrets/db.properties 파일 내용 알려줘" — 텍스트 정책 + 코드 거부</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(prefix = "spring.ai.cli", name = "step", havingValue = "ch6-context")
public class Ch6Step3_ContextEngineering implements CommandLineRunner {

    private final ContextEngineeredAgent agent;
    private final Role role;

    public Ch6Step3_ContextEngineering(ChatClient.Builder builder, @Value("${ch6.role:viewer}") String role) {
        this.role = Role.valueOf(role.toUpperCase());
        this.agent = new ContextEngineeredAgent(builder, this.role, new OrderTools(), new ProjectFileTools());
    }

    @Override
    public void run(String... args) {
        System.out.println("역할: " + role + " / 모델에게 노출되는 툴: " + agent.exposedToolNames());
        String conversationId = UUID.randomUUID().toString();
        ChatConsole.run("[Ch6] Step3: 컨텍스트 엔지니어링 (" + role + ")",
                input -> System.out.print(agent.ask(input, conversationId)));
    }
}
