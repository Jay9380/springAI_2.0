package kr.jay.springai.ch06cli.capability.remote;

import java.net.URI;
import java.net.http.HttpRequest;

import io.modelcontextprotocol.client.transport.customizer.McpSyncHttpClientRequestCustomizer;
import io.modelcontextprotocol.common.McpTransportContext;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * [6.6.5 Step 4] 지식 서버(5장 RAG 서버, 5.4에서 API 키를 걸었다)로 가는 요청에만 X-API-Key를 붙인다.
 *
 * <p>함정: McpSyncHttpClientRequestCustomizer 빈은 자동 구성이 <b>모든 연결</b>의 요청에 적용한다(5장에서 확인).
 * 5장처럼 무조건 헤더를 붙이면 지식 서버의 키가 운영 서버(다른 팀, 다른 신뢰 경계)에도 그대로 전송된다.
 * 그래서 요청 대상 주소가 지식 서버일 때만 붙인다 — 비밀은 그 비밀이 필요한 경계로만.
 */
@Component
@Profile("with-knowledge")
public class KnowledgeApiKeyCustomizer implements McpSyncHttpClientRequestCustomizer {

    private final URI knowledge;
    private final String apiKey;

    public KnowledgeApiKeyCustomizer(@Value("${chapter6.knowledge.url}") String knowledgeUrl,
                                     @Value("${chapter6.knowledge.api-key}") String apiKey) {
        this.knowledge = URI.create(knowledgeUrl);
        this.apiKey = apiKey;
    }

    @Override
    public void customize(HttpRequest.Builder builder, String method, URI endpoint, @Nullable String body,
                          McpTransportContext context) {
        if (sameOrigin(endpoint, knowledge)) {
            builder.header("X-API-Key", apiKey);
        }
    }

    static boolean sameOrigin(URI a, URI b) {
        return a.getScheme().equalsIgnoreCase(b.getScheme())
                && a.getHost().equalsIgnoreCase(b.getHost())
                && port(a) == port(b);
    }

    private static int port(URI u) {
        return u.getPort() != -1 ? u.getPort() : ("https".equalsIgnoreCase(u.getScheme()) ? 443 : 80);
    }
}
