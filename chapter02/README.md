# 2장 · 스프링 AI 프레임워크

ChatModel·ChatClient → 프롬프트 → 구조화 출력 → 대화 메모리 → 어드바이저를 차례로 익히고, 마지막에 하나의 CLI 챗봇으로 조립한다.
책과 같은 방식으로 **실행할 단계를 `spring.ai.cli.step` 값으로 고른다** (각 러너에 `@ConditionalOnProperty`).

```bash
./mvnw -pl chapter02 spring-boot:run                                                  # 기본: ch2-final
./mvnw -pl chapter02 spring-boot:run -Dspring-boot.run.arguments=--spring.ai.cli.step=ch2-step1
./mvnw -pl chapter02 test                                                             # 모델 없이 28개 테스트
```

## 단계 (책 표 2.21 순서)

| step 값 | 클래스 | 책 | 배우는 것 | 응답 |
|---|---|---|---|---|
| `ch2-basic` | `Ch2Step0_BasicChat` | 2.2.3 | 가장 단순한 call() 챗봇 | call |
| `ch2-step1` | `Ch2Step1_BasicStreamChat` | 2.3 | stream()으로 토큰 단위 출력, blockLast() | stream |
| `ch2-step2` | `Ch2Step2_PromptTemplate` | 2.4 | defaultSystem으로 페르소나 고정 | stream |
| `ch2-step3` | `Ch2Step3_StructuredOutput` | 2.6 | entity()로 Recipe 레코드 받기 (네이티브 모드) | call |
| `ch2-step4` | `Ch2Step4_Memory` | 2.7 | MessageChatMemoryAdvisor + 대화 ID | stream |
| `ch2-step5` | `Ch2Step5_Advisor` | 2.8 | SimpleLoggerAdvisor + 커스텀 ElapsedTimeAdvisor | stream |
| `ch2-final` (기본) | `Ch2CliChatbotApplication` | 2.9.4 | 위 전부를 한 ChatClient에 조립 | stream |

## 개념 데모 (CLI가 아니라 한 번 실행하고 끝남)

| step 값 | 클래스 | 책 | 내용 |
|---|---|---|---|
| `ch2-demo-chatclient` | `ChatModelDemo` | 2.3 | ChatModel 동기/스트림, ChatResponse 해부, 옵션 오버라이딩, 성격이 다른 ChatClient 빈 2개 |
| `ch2-demo-template` | `PromptTemplateDemo` | 2.4.1~2.4.4 | render, system/user 템플릿, JSON 중괄호 충돌과 `<>` 구분자, `.st` 파일 |
| `ch2-demo-techniques` | `PromptTechniquesDemo` | 2.4.8 | 9가지 기법. `--only=zero,cot`로 골라 실행 |
| `ch2-demo-tokens` | `TokenDemo` | 2.5 | 한글/영어 토큰 수 측정, numPredict로 답 잘림(finishReason=length) |
| `ch2-demo-structured` | `StructuredOutputDemo` | 2.6 | 형식 지시문 보기, Bean/List/Map/직접 만든 변환기, 네이티브 모드 |
| `ch2-demo-memory` | `MemoryDemo` | 2.7 | 수동 메모리, 슬라이딩 윈도우, 대화 ID별 분리 |
| `ch2-demo-advisors` | `AdvisorDemo` | 2.8 | 실행 순서(스택), 요청 차단, 스트림 중 차단 |

## 패키지

```
ch02/
├─ Chapter02Application.java
├─ step/      단계별 CLI 러너 (책의 Ch2StepN)
├─ demo/      개념 데모와 데모용 설정·변환기
├─ advisor/   ElapsedTime, ContentSafety, Fallback, Trace 어드바이저
└─ support/   ChatConsole — 입력/출력 반복 도우미
```

## 테스트 (모델 없이)

| 테스트 | 확인하는 것 |
|---|---|
| `ChatConsoleTest` | 입력 전달, `/exit` 이후 무시, 입력이 끝나면 정상 종료 |
| `PromptTemplateTest` | 변수 치환, **변수 누락 시 예외**, **JSON 중괄호 충돌**과 `<>` 해결, 역할 메시지, `.st` 로딩 |
| `StructuredOutputTest` | 스키마 생성, 필드 순서, 코드블록 제거 후 변환, **문장 응답은 변환 실패**, entity()가 형식 지시를 붙이는 것 |
| `ChatMemoryTest` | 다음 요청에 지난 턴이 실림, 대화 ID별 분리, **대화 ID 누락 시 거부**, 윈도우 초과 시 시스템 메시지 보존 |
| `AdvisorTest` | 스택 순서, **금칙어 요청은 모델을 부르지 않음**, 위험한 답 교체, 스트림 조각에 걸친 금칙어 차단, 빈 답 대체 |
| `ContextLoadsTest` | 스프링 컨텍스트 배선 |

굵은 글씨는 실패 경로(막아야 할 것을 제대로 막는지) 테스트다.

## 실제 모델(qwen3.5:4b)로 돌려 보고 알게 된 것

책 코드와 다르게 동작하거나 주의가 필요했던 부분이다. 코드 주석에도 적어 두었다.

- **책 예제 2.34의 제로샷 `entity(Sentiment.class)`가 실패했다.** 모델이 `"POSITIVE"` 대신 `{"sentiment": "POSITIVE"}` 같은 객체를 돌려줘서 변환 예외가 났다. 스키마를 Ollama `format`으로 직접 보내는 네이티브 모드(`spec.useProviderStructuredOutput()`)로 바꾸니 정확히 `POSITIVE`가 나왔다. → `PromptTechniquesDemo.zeroShot()`에 두 방식을 나란히 두었다.
- **최상위가 배열인 스키마를 네이티브로 보내면 빈 배열 `[]`이 돌아왔다.** 배열은 프롬프트 기반으로 받거나 레코드로 한 번 감싼다. (스프링 AI 소스 주석도 OpenAI는 최상위 배열 스키마를 받지 않는다고 경고한다)
- **형식 지시는 부탁일 뿐이다.** 파이프(`|`)로 구분하라는 지시를 `/`로 어기거나, JSON 대신 문장으로 답하는 일이 가끔 있었다. 데모는 항목별로 실패해도 계속 진행하게 했고, 운영 코드라면 `validateSchema()` 재시도나 네이티브 모드를 쓴다.
- **환각이 많다.** 스프링의 역사를 물을 때마다 연도와 창시자가 다르게 나왔고, 책 저자를 지어냈고, "감사합니다"의 일본어를 틀렸다. 사실은 RAG(3장)나 도구(4장)로 넣어 줘야 한다.
- **토큰:** "안녕하세요. 만나서 반갑습니다."(17자)는 9토큰, "Hello, Nice to meet you."는 6토큰이었다. 책 표 2.10(9 / 7)과 거의 같다.
- **메모리 회상도 확률적이다.** 저장소에는 대화가 정확히 들어 있었는데 모델이 한 번은 회상에 실패했다. 이상할 때는 `ChatMemory.get(id)`로 저장소부터 확인해 '기억 장치' 문제와 '모델' 문제를 구분한다.
