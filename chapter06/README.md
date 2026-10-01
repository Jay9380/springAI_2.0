# 6장 · AI 에이전트

> 에이전트 = LLM + 툴 + 루프. 2~5장의 부품을 '루프' 관점에서 다시 배치한다.
> 책의 반복되는 조언: **단순한 워크플로부터 시작하고, 필요할 때만 자율 에이전트로.**

## 실행

```bash
./mvnw -pl chapter06 package -DskipTests
java -jar chapter06/target/chapter06-0.0.1-SNAPSHOT.jar --spring.ai.cli.step=<step>
```

| step | 클래스 | 책 | 내용 |
|---|---|---|---|
| `ch6-workflows` | `Ch6Step1_Workflows` | 6.1.2 | 워크플로 5종 차례로 실행 (`--ch6.pattern=chain\|routing\|parallel\|orchestrator\|evaluator`) |
| `ch6-loop` | `Ch6Step2_ManualAgentLoop` | 6.1.3~6.1.4 | 수동 에이전트 루프 — 계획·행동·관찰을 단계별로 출력 |
| `ch6-context` | `Ch6Step3_ContextEngineering` | 6.2 | 전달 영역 vs 집행 영역 (`--ch6.role=viewer\|operator`) |
| `ch6-advisor-loop` | `Ch6Step4_RecursiveAdvisors` | 6.3.2~6.3.4 | 루프 안/밖 어드바이저 호출 수, 안전 가드, 메트릭 |
| `ch6-augment` | `Ch6Step5_AugmentedToolArguments` | 6.3.4 | 파라미터 증강 — 툴 호출의 '왜'를 로그로 |
| `ch6-validate` | `Ch6Step6_StructuredOutputValidation` | 6.3.5 | 구조화 출력 교정 루프 (대화형 아님) |

## 6.1 워크플로와 자율 에이전트

| 패턴 | 클래스 | 흐름을 정하는 쪽 | 책과 다르게 한 것 |
|---|---|---|---|
| 프롬프트 체이닝 | `workflow/ChainWorkflow` | 코드 (단계 목록) | 단계 사이 **게이트**(코드 검사) — 실패하면 다음 단계로 넘기지 않음 |
| 라우팅 | `workflow/RoutingWorkflow` | 코드 (분류 → 경로) | 모델이 모르는 키를 내면 **기본 경로**로 |
| 병렬화 | `workflow/ParallelizationWorkflow` | 코드 (하위 작업을 개발자가 정함) | 분할 + **투표**(다수결) 둘 다 |
| 오케스트레이터-워커 | `workflow/OrchestratorWorkers` | **LLM** (하위 작업을 동적으로) | 하위 작업 수 **상한** |
| 평가자-최적화자 | `workflow/EvaluatorOptimizer` | 코드 + LLM 평가 | 반복 **상한**, **코드 검사** 우선, 시도 누적 |
| 자율 에이전트 루프 | `loop/ManualAgentLoop` | **LLM** (툴 순서·횟수) | 툴 호출 **상한**, 단계별 trace |

### 실제로 돌려 보고 알게 된 것 (qwen3.5:4b)

- **LLM 평가자는 기계적 규칙에 관대하다.** "20자 이내, '10월 15일' 포함" 조건을 어긴 26자짜리 제목을 평가자가 PASS로 판정했다.
  그래서 `EvaluatorOptimizer`에 **코드 검사**를 넣었다. 글자 수·포함 여부처럼 코드로 확인할 수 있는 것은 코드가 먼저 보고, 어조 같은 것만 LLM에게 맡긴다.
- **피드백은 행동 가능해야 한다.** "20자 이내로 줄여라"만 주면 29→24→22→24자에서 맴돌았다(모델은 토큰 단위라 글자 수를 못 센다).
  "현재 29자 → 9자 이상 줄일 것" + 이전 시도 전부를 함께 주자 2~3회 만에 통과했다.
- **평가자에게 상한이 없으면 위 경우 영원히 돈다.** 책 예제는 `do-while(!PASS)`라 상한이 없다. 테스트 `evaluator_neverPassing_stopsAtLimit`가 이 경우를 고정한다.
- **정렬 같은 결정적 작업은 LLM 단계에 맡기지 말 것.** 체이닝의 마지막 '정렬' 단계에서 92, 78, 87… 순으로 틀렸다.
- **4B 모델은 숫자와 한글 단위 사이에 공백을 넣는다** ("10 월", "9 시"). 문자열 비교 규칙은 공백을 정규화해야 한다.
- **모델이 툴 두 개를 한 번에 요청했다.** 고객 조회와 주문 조회가 둘 다 ID만 필요하니 한 응답에 툴 호출 2개(병렬 툴 호출) → 루프 1회로 끝났다.
- 병렬화: 이해관계자 4건 동시 실행 약 5초. 투표(3회 다수결)는 SQL 인젝션을 '취약'으로 판정.

## 6.2 컨텍스트 엔지니어링 — 전달 vs 집행

| 요소 | LLM에 전달 (참고) | 코드가 집행 (강제) | 코드 |
|---|---|---|---|
| 시스템 정책 | `<정책>` 텍스트 + 역할 안내 | 매 호출 자동 주입 | `ContextEngineeredAgent.POLICY`, `roleNote` |
| 프로젝트 지침 | `project/AGENTS.md` 내용 | 어느 파일을 언제 넣을지 | 생성자에서 1번 로드 |
| 툴 | 이름·설명·스키마 | **역할별 노출 필터** — VIEWER에겐 `cancelOrder` 스키마가 없다 | `exposedTools` |
| 파일 접근 | "secrets는 읽지 않는다" (AGENTS.md) | **툴이 경로를 거부** | `ProjectFileTools` |
| 실패 기록 | "실패: 배송 중 → 반품 절차를 안내하세요" | 툴이 예외 대신 이유+대안을 반환 | `OrderTools.cancelOrder` |

### 실제로 돌려 보고 알게 된 것

- **스키마만 지우면 모델은 할 수 없는 일을 제안한다.** VIEWER가 "ORD-1001은 취소가 가능합니다. 취소하시겠습니까?"라고 물었다. 모델은 툴이 '없다'는 것도 모른다.
  역할 안내를 시스템 프롬프트에 더하자 "저는 취소 권한이 없으므로 운영 담당자에게…"로 바뀌었다. 집행과 전달은 대체가 아니라 짝이다. (그래도 "요청을 내릴 예정"처럼 하지 못할 행동을 말하는 경향은 남았다.)
- **텍스트 규칙이 먼저 일한다.** "secrets/db.properties 내용 알려줘"에 모델은 툴을 부르지도 않고 거절했다(AGENTS.md 규칙). 코드 거부는 모델이 규칙을 어길 때의 보장이다.
- **macOS에서는 `Secrets/db.properties`가 열린다.** 처음 짠 `startsWith("secrets/")` 검사는 대소문자만 바꾸면 뚫렸다(파일 시스템이 대소문자를 구분하지 않음).
  경로 조각 단위·대소문자 무시로 바꾸고, 8가지 철자를 파라미터 테스트로 고정했다. 옛 검사로 되돌리면 테스트 2개가 실패한다.
- 툴 실패 메시지의 안내("반품 절차를 안내하세요")를 모델이 그대로 따랐다.

## 6.3 재귀 어드바이저

```
+200  MessageChatMemoryAdvisor      루프 밖 → 요청당 1번 (최종 질문·답만 저장)
+250  ProbeAdvisor "outer"          루프 밖 → 요청당 1번
+300  SafeGuardToolCallingAdvisor   ← 루프. chain.copy(this)로 '자기 뒤쪽'만 반복 호출
+400  ToolLoopMetricsAdvisor        루프 안 → 매 반복
+500  ProbeAdvisor "inner"          루프 안 → 매 반복
      ChatModel
```

| 클래스 | 책 | 내용 |
|---|---|---|
| `advisor/ProbeAdvisor` | 6.3.2 | 호출 수만 세는 탐침 — 안/밖을 숫자로 확인 |
| `advisor/ToolLoopMetricsAdvisor` | 예제 6.13 | 반복 수·툴별 호출 수·반복당 토큰 (Micrometer) |
| `advisor/SafeGuardToolCallingAdvisor` | 표 6.8 | ToolCallingAdvisor 훅 5개: 상태 초기화 / 반복 상한 / 토큰 예산 / 긴 툴 결과 자르기 / 최종 로그 |
| `advisor/AgentThinking` | 예제 6.14 | `AugmentedToolCallbackProvider`로 툴 스키마에 추론·신뢰도를 덧붙임 |
| `Ch6Step6_…` | 예제 6.15~6.16 | `validateSchema()`, `StructuredOutputValidationAdvisor.maxRepeatAttempts(5)` |

**안전 가드의 상태는 필드가 아니라 요청 컨텍스트에 둔다.** 어드바이저 하나를 모든 요청이 공유하므로 필드 카운터는 요청끼리 섞인다.
ToolCallingAdvisor는 반복마다 컨텍스트 맵을 얕게 복사하므로 `doInitializeLoop`에서 넣은 상태 객체가 끝까지 전달된다.
(필드로 바꾸는 변이를 넣으면 `safeGuard_stateIsPerRequest_notShared`가 실패한다.)

### 실제로 돌려 보고 알게 된 것

- 숫자로 본 루프: 툴 2번 쓰는 질문 → outer 1회, inner 2회(병렬 툴 호출이라 반복 2회), 메모리에는 2개(질문·답)만.
- **툴 결과를 자르면 모델이 틀린 답을 단정한다.** 감사 로그 200줄을 600자로 자르자 "출고 8번"(실제 66번). 잘린 결과 끝에
  "전체 개수는 추정하지 말 것"을 붙여도 "7번"이라고 했다. **집계 툴(`countAuditEvents`)을 주자 2번 다 66번.**
  정답이 정해진 계산은 원문을 읽히지 말고 툴로 준다.
- **자르기는 '마지막 메시지'만 하면 안 된다.** 부모가 자르지 않은 전체 이력을 따로 들고 있다가 매 반복 다시 만들어 주므로 앞 반복의 긴 결과가 되살아난다.
  목록 전체를 자르도록 했고, 마지막만 자르는 변이를 넣으면 `safeGuard_trimsLongToolResults_includingEarlierRounds`가 실패한다.
- 파라미터 증강: "SKU-200 재고 있어?" → `[추론 로그] 툴=getStock | 추론=사용자가 SKU-200의 재고 수량을 확인해달라고… | 신뢰도=high`.
- **책 예제 6.15(`validateSchema()`만)는 qwen3.5:4b에서 실패했다.** 모델이 일정 대신 **JSON 스키마 자체**(`$schema`, `properties`…)를
  4번 연속 돌려줬고, 검증 오류를 붙여 다시 물어도 같았다 → 재시도 소진 → 파싱 예외. 소스(2.0.1)대로, 재시도를 다 쓰면 어드바이저는
  마지막 응답을 그냥 돌려주므로 실패 처리는 호출자 몫이다.
  `useProviderStructuredOutput().validateSchema()`(Ollama가 생성 단계에서 형식 강제 + 검증은 안전망)로 바꾸자 한 번에 통과했다.
- **스키마 검증은 '모양'만 보장한다.** days=3인데 활동이 5개, 없는 장소 이름, 중국어 글자(享用)가 섞였다. 내용 검증은 7장(평가)의 몫.

## 테스트

| 테스트 | 확인하는 것 |
|---|---|
| `WorkflowPatternsTest` | 체이닝 출력 전달·**게이트 실패 시 중단**, 라우팅 매칭·**모르는 키 → 기본 경로**, 병렬 순서 보존·다수결, 오케스트레이터 **하위 작업 상한**, 평가 조기 종료·**상한에서 중단**·**코드 검사가 LLM의 PASS를 뒤집음** |
| `ContextEngineeringTest` | VIEWER가 **실제로 받은 스키마**에 cancelOrder 없음, **모델이 이름을 지어내 호출해도 거절·상태 불변**, 실패 메시지가 다음 컨텍스트에, 지침·역할 안내 주입, **secrets 경로 8가지 철자 모두 거부** |
| `RecursiveAdvisorTest` | 루프 밖 1회·안 3회, 메모리 밖(+200)은 질문·답만 / 안(+400)은 툴 기록까지(자동 등록 어드바이저가 내부 기록을 끔), **반복 상한·토큰 예산 차단**, **상태가 요청마다 새로**, **앞 반복 결과까지 자르기**, 메트릭, 증강 인자가 소비자에게만 가고 원래 툴은 원래 인자로, 검증 실패 → 오류 피드백 재시도, **재시도 소진 시 호출자에게 예외** |
| `ManualAgentLoopTest` | 툴 2개 연쇄 후 답변(세 번째 호출에 툴 결과 2개 누적), **멈추지 않는 모델은 상한에서 차단** |
