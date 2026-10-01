package kr.jay.springai.ch05.step;

import java.util.Map;

import io.modelcontextprotocol.spec.McpSchema;
import kr.jay.springai.ch05.client.McpClientCatalogService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * [5.5.3 Step2 예제 5.82] 모델 없이 프리미티브를 직접 호출한다.
 *
 * <p>실행: {@code --spring.ai.cli.step=ch5-client-step2}  (클라이언트 쪽 모델 호출 없음)
 *
 * <p>왜? 모델과 연결하기 '전에' 서버가 공개한 각 기능이 정상인지 검증하기 위해서다.
 * 문제가 생겼을 때 "모델이 도구를 안 불렀나 / 서버 도구가 고장났나"를 분리해서 볼 수 있다.
 */
@Component
@Profile("!server")
@ConditionalOnProperty(prefix = "spring.ai.cli", name = "step", havingValue = "ch5-client-step2")
public class Ch5Step2_McpPrimitiveCalls implements CommandLineRunner {

    private final McpClientCatalogService catalog;

    public Ch5Step2_McpPrimitiveCalls(McpClientCatalogService catalog) {
        this.catalog = catalog;
    }

    @Override
    public void run(String... args) {
        System.out.println("── tools/call rag_index_summary");
        System.out.println("  " + McpClientCatalogService.text(catalog.callTool("rag_index_summary", Map.of())));

        System.out.println("\n── tools/call rag_search_documents (query, topK=2, category=tech_docs)");
        McpSchema.CallToolResult search = catalog.callTool("rag_search_documents",
                Map.of("query", "RAG 운영 정책은 무엇인가요?", "topK", 2, "category", "tech_docs"));
        System.out.println("  isError=" + search.isError());
        System.out.println("  " + McpClientCatalogService.text(search).replace("\\n", " ").substring(0, 300) + "…");

        System.out.println("\n── resources/read rag://sources");
        catalog.readResource("rag://sources").contents().forEach(c ->
                System.out.println("  " + ((McpSchema.TextResourceContents) c).text().replace("\n", "\n  ")));

        System.out.println("\n── prompts/get rag-grounded-answer");
        catalog.getPrompt("rag-grounded-answer", Map.of("question", "장애 보고 기한은?", "category", "tech_docs"))
                .messages().forEach(m -> System.out.println("  [" + m.role() + "] "
                        + ((McpSchema.TextContent) m.content()).text().replace("\n", "\n  ")));

        System.out.println("\n── completion/complete category \"t\" → " + catalog.completeCategory("t"));
        System.out.println("── completion/complete category \"p\" → " + catalog.completeCategory("p"));
    }
}
