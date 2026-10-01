# 6.6 · 엔터프라이즈 스프링 AI 에이전트 CLI

> 지금까지의 부품을 **4-티어 아키텍처**로 합친다. 설계 원칙: **"툴 경계가 곧 시스템 경계다."**
> 로컬 메서드도, 같은 JVM의 하위 에이전트도, 원격 MCP 서버도 메인 에이전트에게는 모두 툴이다.

## 패키지 = 아키텍처

| 티어 | 패키지 | 클래스 |
|---|---|---|
| T1 채널 | `channel/` | `Ch6CoreAgentCli`(Step 1~4), `Ch6EnhancedSpringAIAgentCli`(Final), `AgentCliRunner`(`/tools` `/metrics`), `ChatConsole` |
| T2 오케스트레이션 | `orchestration/` | `SpringAIAgent`, `AgentConfig`, `AgentSafety`, `SafeToolCallingAdvisor`, `OrchestrationToolCallingAdvisor`, `MinResultsToolIndex`, `ToolCallTraceAdvisor`, `ToolLoopMetricsAdvisor` |
| T2 메타 툴 | `orchestration/meta/` | `ConsoleQuestionHandler`, `LenientTodoWriteCallback` (6.5에서 가져옴) |
| T3 로컬 능력 | `capability/local/` | `LocalTools` (시간, 매장 재고 조회·예약, 계산 4종 = 7개) |
| T3 원격 능력 | `capability/remote/` | `OperationsMcpTools`(운영 서버: `check_stock`, `place_purchase_order` + 승인 게이트), `ConsoleElicitationHandler`, `KnowledgeApiKeyCustomizer` |
| T4 설정·데이터 | `resources/` | `agents/report-writer.md`, `skills/restock-policy/SKILL.md`, 모델 설정 |
| 횡단: 관측 | 설정 | 액추에이터 + `spring-boot-starter-opentelemetry` (`otel` 프로파일에서 OTLP 내보내기) |

## 실행 (저장소 루트에서)

```bash
./mvnw -pl chapter06-agent-cli package -DskipTests
JAR=chapter06-agent-cli/target/chapter06-agent-cli-0.0.1-SNAPSHOT.jar

# Step 1: 로컬 툴만
java -jar $JAR --spring.ai.cli.step=ch6-core
# 동적 툴 발견을 일찍 켜기
java -jar $JAR --spring.ai.cli.step=ch6-core --spring.ai.cli.tool-search.min-tools=5

# Step 2·3: 운영 MCP 서버(:8086) 먼저, 그다음 연결
java -jar $JAR --spring.profiles.active=ops
java -jar $JAR --spring.profiles.active=with-ops --spring.ai.cli.step=ch6-core

# Step 4: 지식 서버 = 5장 RAG 서버(:8085, API 키) 먼저
java -jar chapter05/target/chapter05-0.0.1-SNAPSHOT.jar --spring.profiles.active=server
java -jar $JAR --spring.profiles.active=with-ops,with-knowledge --spring.ai.cli.step=ch6-core

# Step 5·6·Final: 강화 에이전트 (기본 step = ch6-final). OTLP로 내보내려면 otel 추가 (수집기 :4318)
java -jar $JAR --spring.profiles.active=with-ops,with-knowledge[,otel]
```

Step이 올라가도 `SpringAIAgent`와 CLI 코드는 한 줄도 바뀌지 않는다. 능력은 T3(연결한 서버)에서 늘어난다.

## 책과 다르게 한 것 (모두 실측이나 소스 확인에서 나옴)

| 책 | 여기 | 이유 |
|---|---|---|
| 루프 어드바이저를 **요청마다 새로** 만들어 안전 가드 카운터를 분리 (예제 6.31·6.33) | 어드바이저는 **하나**, 카운터는 **요청 컨텍스트**에 (`AgentSafety`) | 새 `ToolSearchToolCallingAdvisor`는 '색인함' 표시가 비어 **매 요청 모든 툴을 다시 임베딩**한다(테스트로 확인: 요청마다 +7) |
| 체커가 false → 툴 요청 응답이 그대로 최종 답 | 상한을 넘은 응답을 **사람이 읽을 안내문**으로 바꿔 종료 | 툴을 부르려던 응답엔 읽을 텍스트가 없다 |
| 라운드 상한만 | **같은 툴·같은 인자 3연속이면 즉시 중단** 추가 | 실측: 4B가 같은 TodoWrite를 11번 반복, 입력 토큰 **88,190** 소모 후 상한에 걸림 |
| 툴 검색 결과 수 = 모델이 준 maxResults | `MinResultsToolIndex`로 **최소 3개** | 실측: 모델이 `maxResults:1`로 검색해 예약 툴을 못 찾고 "예약 툴이 없다"고 답함 |
| 메타 툴을 `List<ToolCallback>` 빈으로 (예제 6.46) | 전용 타입 `MetaTools`로 감쌈 | ToolCallingAutoConfiguration이 그 타입 빈을 전역 리졸버로 모아 `@Lazy`를 무시하고 즉시 생성 → ChatClient.Builder와 **순환 참조로 기동 실패** |
| (5장 키를 모든 요청에) | `KnowledgeApiKeyCustomizer`는 **지식 서버 주소로 가는 요청에만** 키를 붙임 | 요청 커스터마이저 빈은 모든 MCP 연결에 적용된다 → 키가 운영 서버로 샌다 |
| 스킬: 모델이 50 − 7을 직접 계산 | 스킬에 `calculator_subtract`를 쓰라고 명시 | 책 p.641이 스스로 지적한 한계 (숫자 계산은 툴로) |
| 하위 에이전트 정의에 `tools:` 없음 (예제 6.45) | `tools: TodoWrite` | 없으면 Bash·Write·Edit까지 받는다 (6.5 `SubagentToolPolicy`) |
| 관측 의존성 3개 개별 추가 (예제 6.49) | `spring-boot-starter-opentelemetry` | Boot 4는 자동 구성이 모듈로 쪼개져 있어 그 셋만으로는 자동 구성 모듈이 빠진다 |
| `stream()` | `call()` | 루프 안 [툴 호출]·[툴 결과] 출력과 최종 답이 섞이지 않게 |

## 실제로 돌려 보고 알게 된 것 (qwen3.5:4b, bge-m3)

- **Step 1** — "지금 한국 시간을 알려주고, SKU-200 재고를 확인한 뒤 2개 예약해줘" → `current_datetime`·`product_lookup`(병렬) → `product_reserve` → "남은 재고 5개".
  이어서 "방금 예약한 상품 이름이 뭐였지?" → **툴 없이 메모리로** "자전거 전조등". `/metrics`: 4회 반복, 입력 토큰 4,350.
- **동적 툴 발견(min-tools=5)** — `toolSearchTool("한국 시간 확인")` → `current_datetime` …, 임베딩 토큰(bge-m3)이 지표에 새로 등장.
- **Step 2·3** — 툴 9개(로컬 7 + 원격 2). `check_stock` 7개 → `place_purchase_order(43)` → `[승인 요청] … (y/n)` y → 발주 완료. SKU-100은 n → "승인하지 않아 취소", 재고 그대로.
- **Step 4** — 툴 12개 → 임계값(10)을 넘어 **동적 툴 발견이 자동으로 켜짐**. "사내 보안 담당자가 누구인지" → 검색으로 `rag_search_documents`를 찾아 "김보안"(연락처는 5장 마스킹 그대로 `[EMAIL]`).
  "비밀번호 변경 주기"는 5장 데이터에 없어서 RAG 에이전트가 "모릅니다"라고 답했다 — 근거 없이 지어내지 않았다.
- **Final (책 pp.639–640 시나리오, 책은 qwen3.5:9b로 실행)** — 처음엔 **실패**: TodoWrite만 11번 반복 → 10라운드 상한으로 중단, 입력 토큰 88,190.
  반복 감지 · TodoWrite 입력 모양 보정(새 모양 `"[{\"todos\":[…]}]"`) · 툴 사용 규칙(절차는 Skill 먼저, 모호하면 AskUserQuestionTool, TodoWrite 반복 금지)을 넣은 뒤 **3번 모두 SKU-200 43개 발주까지 완료**:
  `Skill(restock-policy)` → `AskUserQuestionTool`(1/3회; 나머지 2회는 텍스트로 되물음) → `toolSearchTool` → `check_stock`(7) → (`calculator_subtract` 1/3회) → `place_purchase_order(43)` → 승인 y.
  한 번은 `Skill("calculator_subtract")`처럼 **툴을 스킬로 착각**했다. 4B로 되긴 하지만 경로가 매번 다르다 — 책의 "복잡한 시나리오는 모델 성능에 크게 좌우된다"를 그대로 확인.
- **관측** — 관측 코드를 한 줄도 쓰지 않았는데 `gen_ai.client.token.usage{operation=chat|embedding, model=qwen3.5:4b|bge-m3, type=input|output|total}`가 쌓였다.
  `otel` 프로파일 + 로컬 OTLP 수신기로 확인: 트레이스 요청 3~4건·메트릭 2~3건이 도착했고, 트레이스에 `gen_ai.request.model`·`gen_ai.usage.input_tokens`·`spring.ai.advisor.name`·`spring.ai.chat.client.tool.names`가,
  메트릭에 커스텀 `agent.tool.loop.iterations`·`agent.tool.calls`가 함께 실렸다. 로그 줄에도 `[traceId-spanId]`가 붙는다.
- **콘텐츠는 기본 비기록** — OTLP 페이로드에 툴 이름(`check_stock`)은 있지만 **사용자 질문("운영 시스템에서 SKU-200…")과 툴 결과("현재 재고는…")는 없었다**(바이트 검색으로 확인).
- **가짜 임베딩 모델의 함정(테스트)** — `EmbeddingModel.dimensions()` 기본 구현은 호출마다 "Test String"을 임베딩한다. SimpleVectorStore는 add·delete·search마다 관측 컨텍스트를 만들며 이를 부른다.
  실제 OllamaEmbeddingModel은 AbstractEmbeddingModel이 캐시해서 문제없지만, 직접 만든 EmbeddingModel은 저장소 작업마다 임베딩이 1번씩 더 나간다.

## 테스트

| 테스트 | 확인하는 것 |
|---|---|
| `OrchestrationTest` | **멈추지 않는 모델 → 예외 대신 안내문**, **같은 호출 반복 즉시 중단**(다른 인자는 반복 아님), **공유 어드바이저에서도 카운터는 요청별**, 메모리는 루프 밖(질문·답만)·두 번째 턴이 첫 턴을 봄, **어드바이저 공유 시 재색인 0 / 요청마다 새로 만들면 +7**, 메타 툴 레이어 첫 라운드 = toolSearchTool + 메타 툴만, 예약 성공·**재고 부족·0개·없는 SKU 실패(재고 불변)**, **최소 결과 수 보장**, **API 키는 지식 서버로만** |
| `OperationsServerTest` | 운영 서버를 실제로 띄워: 툴 2개와 destructive 힌트, **승인 시에만 재고 변경**, **거절 시 불변**, **승인 창구 없는 클라이언트 거부·잘못된 입력은 묻지도 않음** |
| `TodoShapeTest` | 실측에서 모델이 보낸 TodoWrite 인자 세 가지 모양을 모두 같은 계획으로 해석 |
| `ContextLoadsTest` | step이 없으면 러너도 에이전트도 만들지 않음 |
