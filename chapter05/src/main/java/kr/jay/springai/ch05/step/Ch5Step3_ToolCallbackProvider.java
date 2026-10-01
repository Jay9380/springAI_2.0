package kr.jay.springai.ch05.step;

import java.util.Map;

import kr.jay.springai.ch05.client.McpClientCatalogService;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * [5.5.3 Step3 예제 5.83] 원격 도구를 ToolCallback으로 — 4장의 로컬 도구와 같은 모양이 된다.
 *
 * <p>실행: {@code --spring.ai.cli.step=ch5-client-step3}  (서버의 rag_answer_question은 서버에서 LLM을 부른다)
 *
 * <p>call(JSON, ToolContext)은 로컬 메서드를 부르는 것처럼 보이지만, 안에서는
 * ① ToolContext → _meta 변환(허용 목록 정책) ② tools/call JSON-RPC 요청 ③ 서버 실행 결과 수신이 일어난다.
 * 서버 로그에서 userId·conversationId가 _meta로 도착했는지, password는 오지 않았는지 확인해 보자.
 */
@Component
@Profile("!server")
@ConditionalOnProperty(prefix = "spring.ai.cli", name = "step", havingValue = "ch5-client-step3")
public class Ch5Step3_ToolCallbackProvider implements CommandLineRunner {

    private final McpClientCatalogService catalog;

    public Ch5Step3_ToolCallbackProvider(McpClientCatalogService catalog) {
        this.catalog = catalog;
    }

    @Override
    public void run(String... args) {
        System.out.println("── 원격 도구가 바뀐 ToolCallback 목록");
        for (ToolCallback cb : catalog.toolCallbacks()) {
            System.out.printf("  %-22s %s%n", cb.getToolDefinition().name(), cb.getClass().getSimpleName());
        }

        ToolCallback answer = catalog.toolCallback("rag_answer_question");
        ToolContext context = new ToolContext(Map.of(
                "userId", "step3-user",
                "conversationId", "step3-conversation",
                "password", "절대-서버로-가면-안-되는-값"));      // 허용 목록 밖 → _meta에서 빠져야 한다
        String result = answer.call("{\"question\":\"긴급 장애는 몇 분 안에 보고해야 하나요?\"}", context);
        System.out.println("\n── rag_answer_question 결과 (서버가 검색 + 답변까지)");
        System.out.println("  " + result);
    }
}
