# 1장 · AI 에이전트, 새로운 패러다임의 시작

책 1장은 개념 소개라 코드가 없다. 그래서 이 모듈은 1장에서 말하는 두 가지를 직접 확인하는 최소 코드다.

1. **스프링 방식 그대로 LLM을 부른다** (책 1.2.2) — 스타터 + 설정만으로 `ChatClient.Builder`가 빈으로 등록된다.
2. **LLM 호출의 실체** — `ChatClient` 한 줄은 Ollama에 HTTP JSON을 한 번 주고받는 일이다. 텍스트 뒤에는 토큰 수와 종료 이유가 붙어 온다. 그리고 모델은 대화를 기억하지 않는다.

## 읽는 순서

| 순서 | 파일 | 볼 것 |
|---|---|---|
| 1 | `pom.xml` | 스타터 하나로 무엇이 자동 등록되는지 |
| 2 | `src/main/resources/application.yml` | 2.0의 평평한 설정 키, `think: false`, `temperature` |
| 3 | `HelloAiService.java` | `prompt().user().call().content()` 체인, `chatResponse()`로 메타데이터 읽기 |
| 4 | `Chapter01Application.java` | CommandLineRunner로 세 가지 실험 실행 |
| 5 | `HelloAiServiceTest.java` | 람다 하나로 가짜 ChatModel을 만들어 모델 없이 테스트하는 법 |

## 실행

```bash
ollama pull qwen3.5:4b                    # 한 번만
./mvnw -pl chapter01 spring-boot:run      # 실제 모델로 실행
./mvnw -pl chapter01 test                 # 모델 없이 단위 테스트
```

## 실행하면 보이는 것

실제 실행 예 (qwen3.5:4b, 2026-10-01):

```text
>>> 질문: 스프링 AI를 한 문장으로 소개해줘.
>>> 답변: 스프링 AI 는 ... 코드 작성 속도를 높이고, 테스트 품질을 개선하며 ... AI 기반 개발 도구입니다.
>>> 모델=qwen3.5:4b, 입력 토큰=24, 출력 토큰=50, 종료 이유=stop
>>> 답변: 네, 제이가 맞습니다! ...
>>> 답변: 죄송하지만 저는 사용자의 이름을 기억하지 못합니다. ...
```

- ①의 답은 **틀렸다**. 스프링 AI는 코딩 도우미가 아니라 AI 모델을 스프링 앱에 붙이는 프레임워크다. 작은 모델은 모르는 것을 그럴듯하게 지어낸다(환각). 사실은 RAG나 도구로 넣어 줘야 하는 이유다(3·4장).
- ②의 출력에서 입력·출력 토큰 수와 종료 이유가 찍힌다. 종료 이유가 `length`면 답이 최대 길이에 걸려 잘린 것이다.
- ③에서 두 번째 질문("내 이름이 뭐라고 했지?")에 모델이 이름을 모른다고 답한다. 두 호출이 서로 독립이기 때문이다. 이 문제를 푸는 것이 2장의 **대화 메모리**다.

## 참고

- macOS에서 `Unable to load io.netty.resolver.dns.macos...` ERROR 로그가 한 줄 찍힐 수 있다. 네이티브 DNS 라이브러리가 없어 시스템 기본 DNS로 대체한다는 뜻이라 로컬 Ollama 호출에는 영향이 없다.
