package kr.jay.springai.ch05.server;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * [5.4] MCP 서버 보안 — 가장 단순한 형태인 API 키 검사.
 *
 * <p>MCP 서버는 "도구를 실행해 주는 HTTP 엔드포인트"다. 아무나 tools/call을 보낼 수 있으면 곧 아무나 우리 시스템을
 * 실행할 수 있다는 뜻이다. 로컬 STDIO가 아니라 원격(HTTP)으로 공개하는 순간 인증이 필요하다.
 *
 * <p>책 5.4는 커뮤니티 라이브러리 mcp-security로 두 가지를 다룬다.
 * <ul>
 *   <li>API 키 — 서버·도구 단위의 간단한 공유 비밀. 서버 간 통신, 사내 도구에 알맞다 (이 클래스가 그 개념)</li>
 *   <li>OAuth2 — 인가 서버가 발급한 JWT를 서버가 검증. 사용자별 권한·만료·위임이 필요할 때.
 *       401 응답의 WWW-Authenticate로 클라이언트가 인가 서버를 '발견'하는 흐름까지 MCP 명세에 정의돼 있다</li>
 * </ul>
 * 여기서는 라이브러리 없이 서블릿 필터 하나로 '키가 없으면 401'이라는 핵심만 보인다.
 * 운영에서는 키를 환경 변수·비밀 저장소로 주입하고, 사용자 단위 권한이 필요하면 OAuth2로 간다.
 */
@Component
@Profile("server")
public class McpApiKeyFilter extends OncePerRequestFilter {

    static final String HEADER = "X-API-Key";

    private final byte[] expected;

    public McpApiKeyFilter(@Value("${chapter5.mcp.api-key}") String apiKey) {
        this.expected = apiKey.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/mcp");       // MCP 엔드포인트만 보호
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String given = request.getHeader(HEADER);
        // 상수 시간 비교: 문자열 equals는 앞에서부터 다른 글자를 만나면 바로 끝나 응답 시간으로 키를 추측당할 수 있다
        if (given == null || !MessageDigest.isEqual(expected, given.getBytes(StandardCharsets.UTF_8))) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"unauthorized\",\"message\":\"X-API-Key 헤더가 없거나 올바르지 않습니다\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
