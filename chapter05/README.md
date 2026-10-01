# 5장 · 스프링 AI MCP

3장의 RAG를 **MCP 서버**로 떼어 내고, CLI는 **MCP 클라이언트**로서 원격 도구를 불러 쓴다.
4장 툴 호출과의 차이는 하나다 — 도구가 같은 앱 안이 아니라 **다른 프로세스(서버)**에 있다. 그런데 클라이언트에서는 원격 도구도 똑같이 `ToolCallback`으로 보인다.

```
[클라이언트 CLI] 질문 → ChatClient + ToolCallingAdvisor → 모델이 도구 선택
     → SyncMcpToolCallbackProvider ── HTTP POST /mcp (JSON-RPC 2.0) ──▶ [MCP 서버 :8085]
                                                                        @McpTool → RAG 검색(bge-m3) → 결과
```

## 실행 (서버 먼저)

```bash
./mvnw -pl chapter05 package -DskipTests
cp chapter05/target/chapter05-0.0.1-SNAPSHOT.jar /tmp/ch5-server.jar          # 실행 중에 jar를 다시 빌드해도 서버가 안 깨지게
java -jar /tmp/ch5-server.jar --spring.profiles.active=server                    # 서버 (8085)
java -jar chapter05/target/chapter05-0.0.1-SNAPSHOT.jar                          # 클라이언트: 기본 ch5-final
java -jar chapter05/target/chapter05-0.0.1-SNAPSHOT.jar --spring.ai.cli.step=ch5-client-step1
./mvnw -pl chapter05 test                                                        # 서버를 테스트 안에서 직접 띄워 검증
```

## 구성 (책 5.5 / 표 5.41)

| 역할 | 클래스 | 내용 |
|---|---|---|
| 서버 | `RagServerConfig` | bge-m3 + SimpleVectorStore, 기동 시점에 적재 |
| 서버 | `RagMcpTools` | `@McpTool` 3개: `rag_index_summary`, `rag_search_documents`(클라이언트 측 답변), `rag_answer_question`(서버 측 답변, `McpMeta`) |
| 서버 | `RagMcpPrimitives` | `@McpResource` rag://sources·rag://pipeline, `@McpPrompt` rag-grounded-answer, `@McpComplete` category |
| 클라이언트 | `McpClientPolicyConfig` | 도구 필터, 이름 접두사, **허용 목록 `_meta` 변환기** |
| 클라이언트 | `McpClientCatalogService` | `McpSyncClient` 저수준 호출 + 원격 도구 `ToolCallback` |
| 클라이언트 | `McpEnabledChatService` | 메모리 + 원격 도구 + 스트리밍 |

| step 값 | 클래스 | 배우는 것 | 모델 |
|---|---|---|---|
| `ch5-client-step1` | `Ch5Step1_McpDiscovery` | 서버 정보·역량·도구·리소스·프롬프트 발견 | 없음 |
| `ch5-client-step2` | `Ch5Step2_McpPrimitiveCalls` | 모델 없이 tools/call·resources/read·prompts/get·completion 직접 호출 | 없음 |
| `ch5-client-step3` | `Ch5Step3_ToolCallbackProvider` | 원격 도구를 `ToolCallback`으로, ToolContext → `_meta` | 서버만 |
| `ch5-client-step4` | `Ch5Step4_McpClientPolicy` | 필터 전·후 도구, 기본/허용 목록 `_meta` 비교 | 없음 |
| `ch5-final` (기본) | `Ch5McpCliChatbotApplication` | MCP 도구 챗봇 + 슬래시 명령(`/tools` `/resource` `/prompt` `/complete` `/answer-direct`) | 둘 다 |

## 5.4 보안 — API 키 (최소 구현)

원격(HTTP)으로 공개한 MCP 서버는 "아무나 도구를 실행시킬 수 있는 엔드포인트"다. 책은 커뮤니티 라이브러리
`mcp-security`(`mcp-server-security` / `mcp-client-security`)로 **API 키**와 **OAuth2(JWT)** 두 방식을 보여 준다.
여기서는 라이브러리 없이 같은 개념을 가장 작게 구현했다.

| 쪽 | 클래스 | 하는 일 |
|---|---|---|
| 서버 | `server/McpApiKeyFilter` | `/mcp` 요청의 `X-API-Key` 헤더를 상수 시간 비교, 다르면 401 (initialize부터 막힘) |
| 클라이언트 | `client/McpClientAuthConfig` | `McpSyncHttpClientRequestCustomizer` 빈 → 자동 구성이 **모든** MCP 요청에 헤더를 붙임 |

키는 양쪽 모두 `chapter5.mcp.api-key: ${MCP_API_KEY:local-study-key}`. 운영이라면 환경 변수·비밀 저장소로 주입하고,
사용자별 권한·만료가 필요하면 OAuth2로 간다(401의 `WWW-Authenticate`로 인가 서버를 발견하는 흐름이 MCP 명세에 있다).

```bash
curl -i -X POST localhost:8085/mcp -H 'Content-Type: application/json' -d '{}'   # → 401
MCP_API_KEY=wrong java -jar chapter05/target/chapter05-*.jar --spring.ai.cli.step=ch5-client-step4   # → initialize 실패
```

## 테스트

| 테스트 | 확인하는 것 |
|---|---|
| `RagServerLogicTest` | 청킹 전 마스킹, 적재 멱등성, category 필터·topK 상한, **기본 `_meta` 변환기는 password까지 보냄**, 도구 필터 |
| `McpServerIntegrationTest` | 서버를 임의 포트로 실제 기동 + 순수 MCP 자바 클라이언트로 접속: 도구 3개와 힌트, `McpMeta`가 스키마에 없음, tools/call, **없는 도구 거절**, 리소스·자동 완성, **키 없음·틀린 키는 initialize 실패** |

## 실제로 돌려 보고 알게 된 것

- **기본 `_meta` 변환기는 거의 전부 보낸다.** `ToolContext`에 `password`, `internalToken`을 넣으면 `defaultConverter()`는 그대로 서버로 보냈다. 허용 목록 변환기를 빈으로 두자 서버 로그의 `_meta` 키가 `[userId, conversationId, source]`로 줄었다.
- **공통 설정은 모든 프로파일에 적용된다.** 클라이언트의 경고를 줄이려고 `application.yml`에서 끈 `annotation-scanner`가 서버 프로파일에도 적용되어 `@McpTool`이 하나도 등록되지 않았다(`Unknown tool`). 서버 설정에서 다시 켰다.
- **실행 중인 서버의 jar를 다시 빌드하면 서버가 깨진다.** `NoClassDefFoundError`가 났고 클라이언트는 60초 타임아웃만 받았다. 서버는 복사본 jar로 띄운다.
- **`curl`로 프로토콜을 직접 보면 구조가 보인다.** `initialize` 응답의 `Mcp-Session-Id` 헤더와 capabilities, `notifications/initialized`의 202(본문 없는 알림), `tools/list`의 힌트를 직접 확인했다.
- **결과:** "장애가 나면 무엇을 기록해야 해?" → 모델이 `rag_search_documents`를 골라 4가지 항목과 보고 기한으로 답했다. `/answer-direct`는 서버가 검색과 답변을 모두 해서 "김보안입니다. ([policy-docs.txt])"를 돌려줬다.
