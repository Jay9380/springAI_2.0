package kr.jay.springai.ch05;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 5장 실습 진입점 — 3장의 RAG를 MCP 서버와 MCP 클라이언트로 나눈다 (책 그림 5.12).
 *
 * <pre>
 *   [클라이언트 CLI] 사용자 질문 → ChatClient + ToolCallingAdvisor → 모델이 도구 선택
 *        → SyncMcpToolCallbackProvider(원격 도구를 ToolCallback으로)  ── HTTP POST /mcp (JSON-RPC) ──▶
 *   [MCP 서버 :8085] @McpTool 실행 → RAG 지식 베이스(bge-m3 + SimpleVectorStore) 검색 → 결과 반환
 * </pre>
 *
 * <p>4장 툴 호출과의 차이: 4장의 도구는 '같은 앱 안'에 있었다. MCP는 도구를 '다른 프로세스(서버)'로 빼서
 * 여러 AI 앱이 공유하게 한다. 클라이언트 입장에서는 원격 도구도 똑같이 ToolCallback으로 보인다.
 *
 * <p>실행 순서: 서버 먼저 → 클라이언트
 * <pre>
 *   java -jar chapter05/target/chapter05-0.0.1-SNAPSHOT.jar --spring.profiles.active=server
 *   java -jar chapter05/target/chapter05-0.0.1-SNAPSHOT.jar --spring.ai.cli.step=ch5-client-step1
 * </pre>
 */
@SpringBootApplication
public class Chapter05Application {

    public static void main(String[] args) {
        SpringApplication.run(Chapter05Application.class, args);
    }
}
