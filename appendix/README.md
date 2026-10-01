# 부록 · OpenAI 전환 · AI 평가 · 외부 에이전트 연결 · 플레이그라운드

## 실행

```bash
./mvnw -pl appendix package -DskipTests
JAR=appendix/target/appendix-0.0.1-SNAPSHOT.jar
java -jar $JAR --spring.ai.cli.step=appx-provider    # A. 지금 붙은 공급자 (기본 Ollama)
java -jar $JAR --spring.ai.cli.step=appx-eval        # B. 관련성·사실성·커스텀 평가 비교
java -jar $JAR --spring.ai.cli.step=appx-eval-loop   # B. 평가로 루프 보강 (B.5)
# OpenAI로: OPENAI_API_KEY=... java -jar $JAR --spring.profiles.active=openai --spring.ai.cli.step=appx-provider  (과금됨)
```

## A. OpenAI로 전환 — 코드는 그대로, 설정만

`AppxStepA_Provider`는 `ChatModel`·`EmbeddingModel`·`ChatClient` 인터페이스만 안다. `--spring.profiles.active=openai`만 주면
`OllamaChatModel (qwen3.5:4b)` → `OpenAiChatModel (gpt-4.1-nano)`로 바뀐다(실측). 두 스타터를 함께 둔 이 모듈에서 확인한 함정:

| 상황 | 결과 (테스트 `ProviderSelectionTest`) |
|---|---|
| 아무것도 고르지 않음, 키 없음 | **기동 실패.** 처음 실패한 빈이 채팅이 아니라 `openAiSdkAudioSpeechModel`(TTS) — "At least one credential source must be specified". OpenAI 자동 구성이 전부 `matchIfMissing=true`라 쓰지 않는 오디오·이미지·모더레이션까지 켜진다 |
| 아무것도 고르지 않음, 키 있음 | 기동은 **성공**. 하지만 `ChatModel` 빈이 2개(Ollama·OpenAI) → **처음 주입받는 순간** `NoUniqueBeanDefinitionException` (ChatClient.Builder는 프로토타입이라 기동 때 안 만든다) |
| `spring.ai.model.chat/embedding` 선택 + 나머지 `none` | OpenAI 키 없이 기동, ChatModel 1개 (`application.yml`이 이 상태) |
| openai 프로파일, 환경 변수 없음 (`${OPENAI_API_KEY}`) | **기동 성공 → 첫 호출에서 401.** 자리표시자 글자 `${OPENAI_API_KEY}`가 그대로 키로 전송됐다 |
| 같은 상황, 빈 기본값 `${OPENAI_API_KEY:}` | 여전히 기동 성공 → 첫 호출에서 401 ("You didn't provide an API key") |
| + `OpenAiKeyGuard` | **기동 시점에 실패**, 네트워크 호출 없음 |

2.0은 옵션이 평평해졌다: 1.x의 `spring.ai.openai.chat.options.model` → `spring.ai.openai.chat.model` (책 예제 A.2와 같음).

## B. AI 평가 — LLM as a Judge

| 클래스 | 내용 |
|---|---|
| `eval/RagAnswerer` | 평가 대상 RAG. 답과 함께 **답을 만들 때 실제로 본 문서**(`qa_retrieved_documents`)를 돌려준다 |
| `eval/GroundedJudge` | 커스텀 평가기: 네이티브 구조화 출력으로 점수(0~1)·근거 없는 주장·피드백. 최소 통과 점수. **근거가 비면 심판에게 묻지 않음** |
| `eval/EvaluatedAgentLoop` | 예제 B.5: 생성 → 평가 → 피드백 실어 재생성. 최대 시도, **NO_CONTEXT 분리** |
| `support/RecordingChatModel` | 내장 심판이 실제로 쓴 원문 기록 ("YES"? "Yes."?) |

### 실제로 돌려 보고 알게 된 것 (`appx-eval`, qwen3.5:4b가 생성·심판 모두)

| 답 | Relevancy | FactChecking | GroundedJudge |
|---|---|---|---|
| ① RAG의 정상 답 ("3개월마다, 12자 이상…") | 통과 1.0 (심판 "YES") | 통과 **0.0** (심판 "yes") | 통과 1.0 |
| ② 책의 예: "+ 2025년에 개정되었습니다" | **통과** 1.0 | **실패** (심판 "no") | 실패 0.5, 피드백 "근거에 없는 주장: 2025년 개정…" |
| ③ 무관한 답 ("연차는 15일") | 실패 (심판 "NO") | **통과** (심판 "yes") | 실패 0.5, "질문과 무관" |
| ④ 근거 문서 없이 ① 평가 | 실패 | **거짓 통과** ("yes") | (수정 전) **거짓 통과 1.0** → 수정 후 NO_CONTEXT |

- **②가 책의 예를 그대로 재현했다.** 주제가 맞으니 관련성은 통과, 근거에 없는 연도 주장 때문에 사실성은 실패.
- **③은 사실성만으로는 못 잡는다.** FactChecking은 질문을 보지 않는다(소스 확인) — 연차 15일은 검색된 휴가 문서로 '뒷받침'되므로 통과. 두 평가는 함께 써야 한다.
- **④ 근거가 비면 4B 심판은 거짓 통과시켰다.** 노트에는 "빈 근거면 거의 항상 NO"라고 적었는데 실측은 반대(FactChecking "yes", 내 GroundedJudge 1.0). 외부 지식으로 판정한 것이다. 그래서 빈 근거는 **코드가 막는다**(`NO_CONTEXT`).
- **FactChecking은 통과해도 점수 0.0** — 평균 점수로 집계하면 안 되고 `isPass()`로 센다. 두 내장 평가기 모두 **피드백은 빈 문자열**.
- 이번 실행의 심판은 "YES"/"yes"/"NO"/"no"로 깔끔하게 답했다. "Yes."나 "예"였다면 내용과 상관없이 실패한다 — 테스트로 고정(`relevancy_passesOnlyOnExactYes…`).

### 평가로 루프 보강 (`appx-eval-loop`) — 루프가 고칠 일이 없었다

질문에 근거에 없는 것(도입 연도)을 섞고, 생성자에게 일부러 "모르면 추정해서라도 채우라"는 과신형 시스템 프롬프트까지 줬지만
두 번 다 "문서에 도입 연도 정보가 없다"고 답해 **1회차에 통과**했다. 원인은 QuestionAnswerAdvisor의 기본 템플릿이 사용자 메시지 끝에
"not prior knowledge … If the answer is not in the context, inform the user that you can't answer"를 붙이기 때문(2.0.1 소스) —
**메시지 끝의 지시가 시스템 프롬프트를 이겼다.** RAG 템플릿 자체가 1차 방어선이다.
근거가 검색되지 않는 질문("구내식당 메뉴")은 평가 없이 `NO_CONTEXT`로 끝났다.
피드백으로 답을 고치는 경로와 "피드백 없는 재시도"(내장 평가기를 꽂았을 때)는 가짜 모델 테스트로 확인한다.

## C. 외부 AI 에이전트에 연결하기 (부록 C)

이 저장소의 MCP 서버는 모두 Streamable HTTP라 MCP를 지원하는 다른 에이전트에 그대로 붙는다.

| 서버 | 띄우기 | 주소 | 인증 |
|---|---|---|---|
| 5장 RAG 서버 | `java -jar chapter05/target/chapter05-*.jar --spring.profiles.active=server` | `http://localhost:8085/mcp` | `X-API-Key` 헤더 (5.4) |
| 6장 운영 서버 | `java -jar chapter06-agent-cli/target/*.jar --spring.profiles.active=ops` | `http://localhost:8086/mcp` | 없음 (학습용, localhost) |

```bash
# Claude Code (예제 C.2) — 운영 서버
claude mcp add --transport http book-ops http://localhost:8086/mcp
# 5장 서버는 API 키 헤더가 필요하다 (-H/--header, `claude mcp add --help`로 확인)
claude mcp add --transport http book-rag http://localhost:8085/mcp --header "X-API-Key: local-study-key"
```

Claude Desktop은 설정 파일이 "실행할 명령"만 받으므로 `npx -y mcp-remote http://localhost:8086/mcp`로 stdio↔HTTP 다리를 놓는다(예제 C.3).
Codex는 `~/.codex/config.toml`의 `[mcp_servers.<이름>]`에 `url`을 등록한다(예제 C.4).

주의 (책 p.665 + 이 저장소에서 확인한 것):
- `place_purchase_order`는 실행 직전 승인 요청(Elicitation)을 보낸다. **승인 창구가 없는 클라이언트는 이 저장소의 서버가 '거부'로 돌려준다**(6.6 `OperationsServerTest`). 조회 툴(`check_stock`)부터 확인한다.
- 키를 클라이언트 설정에 넣을 때는 그 파일이 저장소에 커밋되지 않는지 확인한다.
- 이 README의 등록 명령은 **실행해 보지 않았다** — 사용자의 에이전트 설정을 바꾸는 일이라 각자 실행한다. 서버 쪽 프로토콜 동작은 각 장의 통합 테스트(순수 MCP 자바 클라이언트)로 확인했다.

## D. 스프링 AI 플레이그라운드 (부록 D)

커뮤니티 오픈소스 데스크톱 앱(spring-ai-community.github.io/spring-ai-playground). MCP 인스펙터에서 위 서버를
STREAMABLE HTTP · `http://localhost:8086` · 엔드포인트 `/mcp`로 등록하면 툴 카드(설명·입력 스키마)를 보고 값을 넣어 직접 실행할 수 있다.
**에이전트 채팅에 붙이기 전에 툴이 계약대로 동작하는지 먼저 확인**하는 단계로 쓴다. 코드가 없는 부분이라 이 모듈에는 실습 코드가 없다.

## 테스트

| 테스트 | 확인하는 것 |
|---|---|
| `EvaluationTest` | Relevancy는 **정확히 yes만 통과**("Yes." "예" 실패)·점수 0/1·피드백 빈 문자열, FactChecking은 **통과해도 0점·질문을 보지 않음**·**근거를 userText에 넣으면 빈 문서로 판정**, GroundedJudge 점수·피드백·최소 점수·**근거 없는 주장이 있으면 점수와 무관하게 실패**·**빈 근거면 심판 호출 없음**, 루프: **피드백이 다음 입력으로 → 통과**, **빈 피드백이면 같은 답 반복 후 상한에서 중단**, **검색 0건은 NO_CONTEXT** |
| `ProviderSelectionTest` | 위 A 표의 네 상황 + `OpenAiKeyGuard` |
| `ContextLoadsTest` | 기본 설정은 OpenAI 키 없이 기동 |
