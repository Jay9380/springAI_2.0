package kr.jay.springai.ch05.client;

import io.modelcontextprotocol.client.transport.customizer.McpSyncHttpClientRequestCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * [5.4] 클라이언트 쪽 인증 — 모든 MCP 요청에 API 키 헤더를 붙인다.
 *
 * <p>McpSyncHttpClientRequestCustomizer 빈을 두면 스프링 AI 자동 구성(StreamableHttpHttpClientTransportAutoConfiguration)이
 * 나가는 '모든' HTTP 요청(initialize, tools/list, tools/call …)에 적용한다. 비즈니스 코드는 인증을 몰라도 된다.
 * OAuth2라면 같은 자리에서 Authorization: Bearer 토큰을 붙인다.
 */
@Configuration
@Profile("!server")
public class McpClientAuthConfig {

    @Bean
    McpSyncHttpClientRequestCustomizer apiKeyHeader(@Value("${chapter5.mcp.api-key}") String apiKey) {
        return (builder, method, endpoint, body, context) -> builder.header("X-API-Key", apiKey);
    }
}
