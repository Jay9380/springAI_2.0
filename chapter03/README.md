# 3장 · 스프링 AI와 RAG

모델이 모르는 사내 문서를 '검색해서 프롬프트에 끼워 넣는' RAG를 오프라인(ETL)과 런타임(질문 처리) 두 파이프라인으로 나눠 만든다.

```bash
ollama pull qwen3.5:4b && ollama pull bge-m3                                      # 채팅 + 임베딩 모델
./mvnw -pl chapter03 spring-boot:run                                              # 기본: ch3-final
./mvnw -pl chapter03 spring-boot:run -Dspring-boot.run.arguments=--spring.ai.cli.step=ch3-step1
./mvnw -pl chapter03 test                                                         # 모델 없이 30개 테스트
```

## 단계 (책 3.8 표)

| step 값 | 클래스 | 파이프라인 | 책 | 배우는 것 | 모델 |
|---|---|---|---|---|---|
| `ch3-step1` | `Ch3Step1_DocumentReaders` | 오프라인 | 3.2 | 네 리더(Text·Json·Markdown·Jsoup)와 메타데이터 | 없음 |
| `ch3-step2` | `Ch3Step2_DocumentTransformers` | 오프라인 | 3.3 | 마스킹 → 포맷 → 청킹, 한 조각의 세 얼굴 | 없음 |
| `ch3-step3` | `Ch3Step3_DocumentWriters` | 오프라인 | 3.4 | 로그·파일로 적재해 눈으로 검증 | 없음 |
| `ch3-step4` | `Ch3Step4_VectorStore` | 오프라인·런타임 | 3.5~3.6 | 임베딩·검색·필터·임계값·저장 | bge-m3 |
| `ch3-step5` | `Ch3Step5_AdvancedRagModules` | 런타임 | 3.7.3~3.7.6 | 재작성 → 검색 → 후처리 → 답변 + 근거 메타데이터 | 둘 다 |
| `ch3-step6` | `Ch3Step6_AdvancedRag` | 런타임 | 3.7.7 | RAG 스트리밍 대화 | 둘 다 |
| `ch3-final` (기본) | `Ch3RagCliChatbotApplication` | 런타임 | 3.8 | 근거 문서를 먼저 보여 주는 RAG CLI | 둘 다 |

## 개념 데모

| step 값 | 클래스 | 책 | 내용 |
|---|---|---|---|
| `ch3-demo-enricher` | `EnricherDemo` | 3.3.3~3.3.4 | LLM으로 키워드·앞뒤 요약 메타데이터 추가 (한국어 템플릿) |
| `ch3-demo-embedding` | `EmbeddingDemo` | 3.5 | 1024차원 벡터, 코사인 유사도 직접 계산 |
| `ch3-demo-naive` | `NaiveRagDemo` | 3.7.2 | QuestionAnswerAdvisor, 런타임 필터, 잘못된 필터 상수 함정 |
| `ch3-demo-query` | `QueryTransformDemo` | 3.7.5 | Compression·Rewrite·Translation·MultiQuery |

## 패키지

```
ch03/
├─ etl/     SourceDocuments(리더 4종), PiiMaskingTransformer, SimpleLengthSplitter,
│           EtlPipeline(마스킹→포맷→청킹), LoggingDocumentWriter
├─ rag/     VectorStoreConfig(SimpleVectorStore), KnowledgeBase(적재), RagService(Advanced RAG),
│           KeywordFilteringPostProcessor, RagConfig
├─ step/    단계별 러너와 최종 CLI
├─ demo/    개념 데모
└─ support/ ChatConsole
src/main/resources/data/   직접 작성한 가상 문서 4개 (텍스트·JSON·마크다운·HTML)
```

## 테스트 (모델 없이, 가짜 임베딩·가짜 채팅 모델)

| 테스트 | 확인하는 것 |
|---|---|
| `SourceDocumentsTest` | 리더별 개수·본문·메타데이터, 단종 모델 isActive=false, nav/footer 제거, 마크다운 category 충돌 방지 |
| `TransformerTest` | 마스킹, 청크 메타데이터, 짧은 글 보존, EMBED/INFERENCE 차이, **자른 뒤 마스킹하면 전화번호가 샌다** |
| `WriterTest` | 파일 마커·메타데이터, 덮어쓰기/이어쓰기, 커스텀 Writer |
| `VectorStoreTest` | 유사도 정렬, isActive 필터, **없는 키가 비교 필터를 통과하는 함정**, **높은 임계값 → 0건**, 본문만 임베딩, save/load |
| `RagTest` | 증강 프롬프트, **근거 0건 → 거절 템플릿**, 폐기 문서 제외, 키워드 후처리, **잘못된 필터 상수는 무시됨** |
| `ContextLoadsTest` | 스프링 컨텍스트 배선 |

굵은 글씨는 실패 경로 테스트다.

## 실제 모델(qwen3.5:4b + bge-m3)로 돌려 보고 알게 된 것

책에는 없거나 책과 다르게 동작한 부분이다. 대부분 2.0.1 소스로 원인을 확인했고 코드 주석에도 적었다.

- **CLI 앱이 끝나지 않았다.** `RetrievalAugmentationAdvisor`가 기본으로 만드는 `ai-advisor-` 스레드 풀은 데몬이 아니고 아무도 닫지 않는다. `jstack`으로 확인했고, 데몬 실행기를 `.taskExecutor(...)`로 넘겨 해결했다.
- **Ollama 임베딩은 메타데이터를 무시한다.** 임베딩할 글은 `EmbeddingModel.getEmbeddingContent()`가 정하는데, 기본은 `getText()`이고 이를 재정의한 구현체는 `OpenAiEmbeddingModel`뿐이다. 그래서 `withExcludedEmbedMetadataKeys`/`EMBED` 설정은 이 실습에서 벡터에 영향이 없다.
- **없는 키가 비교 필터를 통과한다.** `SimpleVectorStore` 필터는 없는 키를 null로 보고 null을 가장 작은 값으로 친다. `bikePrice < 1000000`만 쓰면 가격이 없는 `./mvnw` 코드 조각까지 검색됐다. `category` 조건과 함께 건다.
- **필터가 없으면 폐기 문서가 1위로 나왔다.** "단종된 자전거도 추천해 줄 수 있나요?"에 단종 모델이 0.65로 1위였다. `isActive` 필터나 후처리가 필요하다.
- **기본 템플릿은 영어라 결과도 영어가 된다.** `SummaryMetadataEnricher`는 모든 요약을 "Based on the content provided, here is a summary…"로 시작했다. `{context_str}`을 넣은 한국어 템플릿으로 바꾸자 한국어 요약이 앞뒤로 맞물렸다.
- **리더끼리 메타데이터 키가 충돌한다.** 마크다운 리더는 `category`에 `header_1`·`code_block` 같은 요소 종류를 넣어 업무 분류 `category`와 겹친다. `mdElement`로 옮겼다.
- **임베딩 측정값:** bge-m3는 1024차원이다. "긴급 장애가 발생하면 30분 안에 보고한다"와의 코사인은 영어 번역 0.84, 뜻이 비슷한 다른 문장 0.72, 자전거 문장 0.36이었다.
- **질문 변환:** "그곳의 두 번째로 큰 도시는?"이 대화 기록을 보고 "덴마크에서 두 번째로 큰 도시는 어디인가요?"로 바뀌었다.
