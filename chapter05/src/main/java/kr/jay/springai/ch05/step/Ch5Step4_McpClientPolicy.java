package kr.jay.springai.ch05.step;

import java.util.Map;

import io.modelcontextprotocol.client.McpSyncClient;
import kr.jay.springai.ch05.client.McpClientCatalogService;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.mcp.ToolContextToMcpMetaConverter;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * [5.5.3 Step4 예제 5.84] 클라이언트 정책이 실제로 무엇을 바꾸는지 비교해 본다.
 *
 * <p>실행: {@code --spring.ai.cli.step=ch5-client-step4}  (모델 호출 없음)
 *
 * <ol>
 *   <li>도구 필터: 서버가 공개한 도구(tools/list) vs 우리 모델에게 실제로 노출되는 도구(ToolCallback)</li>
 *   <li>_meta 변환: 같은 ToolContext가 기본 변환기와 허용 목록 변환기에서 각각 어떻게 서버로 나가는지</li>
 * </ol>
 */
@Component
@Profile("!server")
@ConditionalOnProperty(prefix = "spring.ai.cli", name = "step", havingValue = "ch5-client-step4")
public class Ch5Step4_McpClientPolicy implements CommandLineRunner {

    private final McpClientCatalogService catalog;
    private final ToolContextToMcpMetaConverter metaConverter;

    public Ch5Step4_McpClientPolicy(McpClientCatalogService catalog, ToolContextToMcpMetaConverter metaConverter) {
        this.catalog = catalog;
        this.metaConverter = metaConverter;
    }

    @Override
    public void run(String... args) {
        McpSyncClient client = catalog.client();
        System.out.println("── 서버가 공개한 도구 (tools/list)");
        client.listTools().tools().forEach(t -> System.out.println("  " + t.name()));
        System.out.println("── 필터를 통과해 모델에게 노출되는 도구 (ToolCallback)");
        for (ToolCallback cb : catalog.toolCallbacks()) {
            System.out.println("  " + cb.getToolDefinition().name());
        }

        ToolContext ctx = new ToolContext(Map.of(
                "userId", "jay", "conversationId", "c-1", "clientSession", "cli",
                "password", "p@ss", "internalToken", "eyJhbGciOi..."));
        System.out.println("\n── 같은 ToolContext → 서버로 나가는 _meta");
        System.out.println("  기본 변환기   : " + ToolContextToMcpMetaConverter.defaultConverter().convert(ctx));
        System.out.println("  허용 목록 정책: " + metaConverter.convert(ctx));
    }
}
