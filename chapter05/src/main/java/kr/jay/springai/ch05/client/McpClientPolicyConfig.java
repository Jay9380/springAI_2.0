package kr.jay.springai.ch05.client;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.ai.mcp.McpToolFilter;
import org.springframework.ai.mcp.McpToolNamePrefixGenerator;
import org.springframework.ai.mcp.ToolContextToMcpMetaConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * [5.5.3 Step4 예제 5.84] 클라이언트 쪽 정책 — 어떤 원격 도구를 받아들이고, 무엇을 서버로 보낼까.
 *
 * <p>스프링 AI 자동 구성(McpToolCallbackAutoConfiguration)은 아래 세 빈이 있으면 그대로 가져다 쓴다.
 * <ul>
 *   <li>McpToolFilter : 서버가 공개한 도구 중 무엇을 '우리 모델'에게 보여 줄지. 서버를 믿더라도
 *       실험용·위험한 도구까지 모델 손에 쥐여 줄 이유는 없다</li>
 *   <li>McpToolNamePrefixGenerator : 서버가 여러 개면 이름 충돌을 막으려고 접두사를 붙인다. 서버가 하나라 원래 이름 유지</li>
 *   <li>ToolContextToMcpMetaConverter : 요청의 ToolContext 중 무엇을 MCP 요청의 _meta로 서버에 보낼지</li>
 * </ul>
 */
@Configuration
@Profile("!server")
public class McpClientPolicyConfig {

    /** 서버로 보내도 되는 키 — 이 목록 밖의 값(비밀번호, 인증 토큰 등)은 절대 나가지 않는다 */
    static final Set<String> ALLOWED_META_KEYS = Set.of("userId", "conversationId", "clientSession", "progressToken");

    @Bean
    public McpToolFilter ragOnlyToolFilter() {
        // rag_ 로 시작하는 도구만, 설명에 experimental이 있으면 제외
        return (connection, tool) -> tool.name().startsWith("rag_")
                && (tool.description() == null || !tool.description().contains("experimental"));
    }

    @Bean
    public McpToolNamePrefixGenerator noPrefix() {
        return McpToolNamePrefixGenerator.noPrefix();
    }

    /**
     * 주의(2.0.1 소스 확인): 이 빈이 없으면 defaultConverter()가 쓰이는데, 그것은 ToolContext의 값을
     * '거의 전부'(MCP exchange 키만 빼고) 서버로 보낸다. 앱 내부 값이 원격 서버로 새는 길이 된다 → 허용 목록 방식으로 바꾼다.
     */
    @Bean
    public ToolContextToMcpMetaConverter allowListMetaConverter() {
        return toolContext -> {
            Map<String, Object> meta = new HashMap<>();
            if (toolContext != null) {
                toolContext.getContext().forEach((k, v) -> {
                    if (v != null && ALLOWED_META_KEYS.contains(k)) {
                        meta.put(k, v);
                    }
                });
            }
            meta.put("source", "chapter5-cli");
            return meta;
        };
    }
}
