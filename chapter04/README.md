# 4장 · 툴 호출

모델이 "이 도구를 이 인자로 불러 달라"고 요청하면 앱이 실행하고 결과를 돌려주는 구조를 만든다.
**모델은 도구를 실행하지 않는다. 실행 권한은 언제나 앱에 있다** — 그래서 무엇을 노출할지, 언제 실행할지, 사람 승인을 받을지를 개발자가 정한다.

```bash
./mvnw -pl chapter04 spring-boot:run                                                  # 기본: ch4-final
./mvnw -pl chapter04 spring-boot:run -Dspring-boot.run.arguments=--spring.ai.cli.step=ch4-step4
./mvnw -pl chapter04 test                                                             # 모델 없이 16개 테스트
```

## 단계 (책 표 4.11)

| step 값 | 클래스 | 책 | 배우는 것 |
|---|---|---|---|
| `ch4-step1` | `Ch4Step1_MethodTools` | 4.3.1 | `@Tool` 메서드, 선택 파라미터, `defaultTools` 등록 |
| `ch4-step2` | `Ch4Step2_FunctionTools` | 4.2.5, 4.3.2 | `FunctionToolCallback`, 결과 변환기로 이메일 마스킹 |
| `ch4-step3` | `Ch4Step3_ToolContext` | 4.2.4 | `ToolContext`(모델에 안 가는 앱 값), `returnDirect` |
| `ch4-step4` | `Ch4Step4_ToolCallingManager` | 4.4.5 | 수동 루프, 반복 상한, **예약 전 y/n 사람 승인**, 예외 변환 |
| `ch4-step5` | `Ch4Step5_ToolCallingAdvisor` | 4.4.4 | `ToolCallingAdvisor` 직접 구성, 도구 여러 개 연쇄 |
| `ch4-final` (기본) | `Ch4ToolCliChatbotApplication` | 4.5.3 | 메모리 + 도구 + 스트리밍 통합 |
| `ch4-demo-definitions` | `ToolDefinitionDemo` | 4.2.3, 4.2.5 | 모델에게 가는 name·description·inputSchema, 결과 변환 규칙 |

## 패키지

```
ch04/
├─ examples/  ToolNames, DateTimeTools, CalculatorTools, Chapter4ToolCallbacks(함수형 도구 3종),
│             EmailMaskingToolCallResultConverter, FriendlyToolExceptionProcessor, ManualToolCallingService
├─ support/   InventoryTools, TodoTools(상태를 바꾸는 도구), ToolEnabledChatService, ChatConsole
├─ step/      단계별 러너와 최종 CLI
└─ demo/      ToolDefinitionDemo
```

## 실행 제어 세 방식 (4.4)

| 방식 | 루프를 도는 주체 | 이 저장소 |
|---|---|---|
| 프레임워크 제어 | 도구를 등록하면 자동으로 붙는 `ToolCallingAdvisor` | Step1~3 |
| 어드바이저 제어 | 직접 구성한 `ToolCallingAdvisor` (order·루프 조건 지정) | Step5, final |
| 사용자 제어 | `ChatModel` + `ToolCallingManager`로 직접 `for` 루프 | Step4 |

셋 모두 실제 실행은 `ToolCallingManager`가 한다. 달라지는 것은 누가 루프를 도느냐다.

## 테스트 (모델 없이, 호출 순서대로 응답을 정해 둔 `ScriptedToolModel`)

| 테스트 | 확인하는 것 |
|---|---|
| `ToolSpecTest` | 명세·스키마(선택 파라미터), JSON 인자로 실행, 함수형 도구, 이메일 마스킹, String 결과의 따옴표, '도구 요청 → 실행 → [질문·요청·결과]로 재호출' 왕복 |
| `ToolExecutionTest` | 수동 루프, **승인 거절 시 재고 불변**, **폭주 모델을 상한에서 차단**, 도구 예외 → 모델용 메시지, returnDirect 시 재호출 없음·ToolContext 비노출, 아래 버그 두 개의 회귀 테스트 |
| `FinalServiceTest` | 메모리를 루프 바깥에 두면 질문과 최종 답만 저장되고 도구 왕복은 저장되지 않음 |
| `ContextLoadsTest` | 스프링 컨텍스트 배선 |

## 실제 모델(qwen3.5:4b)로 돌려 보고 알게 된 것

- **returnDirect로 String을 바로 보내면 JSON 따옴표가 보인다.** 세션 요약이 `"세션 요약\n- 사용자: …"`처럼 따옴표와 `\n`이 붙은 채 나왔다. 기본 결과 변환기가 String도 JSON으로 직렬화하기 때문이다. 문자열은 그대로 통과시키는 변환기로 고쳤다.
- **`ChatModel`에 준 요청 옵션은 기본 옵션을 대체한다(책 4.3.4).** 수동 루프에서 `ToolCallingChatOptions.builder()`로 새 옵션을 만들었더니 모델 이름까지 사라져 Ollama가 `model cannot be null or empty`로 거부했다. `chatModel.getOptions().mutate()`로 기본값을 유지한 채 도구만 더해 해결했다.
- **도구 결과도 모델이 읽는 프롬프트다.** 할 일 목록을 `#1 [ ] 제목`으로 돌려줬더니 모델이 미완료 항목을 "완료됨"이라고 답했다. 상태를 글자("미완료")로 바꿨다.
- **도구 루프 조건 두 가지(테스트를 만들며 확인).** `ToolCallingAdvisor`는 요청 옵션이 `ToolCallingChatOptions`일 때만 루프를 돈다. 그리고 2.0에서 `ChatModel.getDefaultOptions()`는 deprecated이고 `ChatClient`는 `getOptions()`만 읽는다.
- **도구가 정확한 일을 한다.** "37 곱하기 89"는 3,293, "35000원 18% 할인"은 28,700원, 예약 승인 `n`은 실행하지 않고 `y`는 재고를 12 → 10으로 바꿨다. "오늘 날짜와 SKU-200 재고 확인 후 할 일 기록" 한 문장에 도구 세 개가 연쇄 실행됐다.
