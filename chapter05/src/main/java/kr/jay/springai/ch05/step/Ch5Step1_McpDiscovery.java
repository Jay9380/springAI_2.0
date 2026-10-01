package kr.jay.springai.ch05.step;

import io.modelcontextprotocol.client.McpSyncClient;
import kr.jay.springai.ch05.client.McpClientCatalogService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * [5.5.3 Step1 예제 5.81] 서버 발견 — 서버 정보, 역량(capabilities), 도구·리소스·프롬프트 목록.
 *
 * <p>실행: {@code --spring.ai.cli.step=ch5-client-step1}  (서버가 먼저 떠 있어야 한다, 모델 호출 없음)
 *
 * <p>여기 찍히는 capabilities는 서버가 initialize 응답에서 선언한 '나는 이런 기능이 있다'는 목록이다.
 * 클라이언트는 이것을 보고 어떤 요청을 보낼 수 있는지 안다 (역량 협상).
 */
@Component
@Profile("!server")
@ConditionalOnProperty(prefix = "spring.ai.cli", name = "step", havingValue = "ch5-client-step1")
public class Ch5Step1_McpDiscovery implements CommandLineRunner {

    private final McpClientCatalogService catalog;

    public Ch5Step1_McpDiscovery(McpClientCatalogService catalog) {
        this.catalog = catalog;
    }

    @Override
    public void run(String... args) {
        McpSyncClient client = catalog.client();
        System.out.println("서버 정보   : " + client.getServerInfo());
        System.out.println("서버 역량   : " + client.getServerCapabilities());
        System.out.println("서버 안내문 : " + client.getServerInstructions());

        System.out.println("\n── tools/list");
        client.listTools().tools().forEach(t -> System.out.printf("  %-22s %s%n    annotations=%s%n",
                t.name(), t.description(), t.annotations()));
        System.out.println("\n── resources/list");
        client.listResources().resources().forEach(r -> System.out.printf("  %-16s %s%n", r.uri(), r.description()));
        System.out.println("\n── prompts/list");
        client.listPrompts().prompts().forEach(p -> System.out.printf("  %-20s %s  인자=%s%n",
                p.name(), p.description(), p.arguments().stream().map(a -> a.name() + (a.required() ? "*" : "")).toList()));
    }
}
