package kr.jay.springai.ch02.demo;

import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * [2.4.5 / 2.4.8] 효과적인 프롬프트 작성과 9가지 프롬프트 엔지니어링 기법.
 *
 * <p>실행: {@code --spring.ai.cli.step=ch2-demo-techniques}
 * <br>일부만: {@code --spring.ai.cli.step=ch2-demo-techniques --only=zero,cot}
 *
 * <p>기법마다 temperature를 다르게 준다. 기법의 성격에 맞는 '창의성'이 다르기 때문이다.
 * <ul>
 *   <li>분류·형식 맞추기 → 0.1 (결정론적으로)</li>
 *   <li>추론 → 0.1~0.5</li>
 *   <li>다수결(self-consistency), 아이디어 → 0.8~1.0 (매번 다른 경로가 나와야 의미가 있다)</li>
 * </ul>
 *
 * <p>ApplicationRunner를 쓴 이유: {@code --only=...} 같은 옵션 인자를 편하게 읽기 위해서.
 */
@Component
@ConditionalOnProperty(prefix = "spring.ai.cli", name = "step", havingValue = "ch2-demo-techniques")
public class PromptTechniquesDemo implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PromptTechniquesDemo.class);

    /** 제로샷 분류 결과를 받을 타입. 문자열 대신 enum으로 받으면 오타·변형 응답이 컴파일 타입으로 걸러진다. */
    enum Sentiment { POSITIVE, NEUTRAL, NEGATIVE }

    private final ChatClient chatClient;

    public PromptTechniquesDemo(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> only = args.containsOption("only")
                ? Arrays.asList(args.getOptionValues("only").get(0).split(","))
                : List.of();
        run(only, "zero", this::zeroShot);
        run(only, "few", this::fewShot);
        run(only, "system", this::systemPrompting);
        run(only, "role", this::rolePrompting);
        run(only, "cot", this::chainOfThought);
        run(only, "self", this::selfConsistency);
        run(only, "stepback", this::stepBack);
        run(only, "tot", this::treeOfThoughts);
        run(only, "ape", this::autoPromptEngineering);
    }

    private void run(List<String> only, String name, Runnable technique) {
        if (only.isEmpty() || only.contains(name)) {
            log.info("━━━━ {} ━━━━", name);
            technique.run();
        }
    }

    /**
     * 1. 제로샷 — 예시 없이 지시만. 가장 먼저 시도하는 기준선(baseline).
     *
     * <p>책 예제 2.34는 {@code .entity(Sentiment.class)}로 enum을 바로 받는다. 그런데 qwen3.5:4b로 실행하면
     * 모델이 "POSITIVE" 같은 JSON 문자열 대신 {"sentiment": "POSITIVE"} 같은 JSON '객체'를 돌려줘서
     * 변환이 실패한다(MismatchedInputException). 프롬프트 뒤에 붙는 형식 지시를 작은 모델이 지키지 못한 것이다.
     * → 그래서 두 방법을 나란히 보여 준다. (2.6.5 네이티브 구조화 출력에서 다시 다룬다)
     */
    private void zeroShot() {
        String review = """
                다음 영화 리뷰를 분석하여 긍정(POSITIVE), 중립(NEUTRAL), 부정(NEGATIVE) 중 하나로만 분류하세요.
                다른 설명은 필요 없습니다.

                리뷰: "AI가 통제 없이 발전한다면 인류가 맞이할 암울한 미래를 섬뜩하게 그려낸 수작이다."
                """;

        // (A) 책 방식: 프롬프트 기반 구조화 출력 — 형식 지시를 '글'로 붙여서 부탁한다
        try {
            Sentiment result = chatClient.prompt()
                    .user(review)
                    .options(OllamaChatOptions.builder().temperature(0.1))
                    .call()
                    .entity(Sentiment.class);
            log.info("(A) 프롬프트 기반 결과: {}", result);
        }
        catch (RuntimeException e) {
            log.warn("(A) 프롬프트 기반 변환 실패 — 모델이 형식 지시를 안 지킴: {}", e.getClass().getSimpleName());
        }

        // (B) 네이티브 구조화 출력: JSON 스키마를 Ollama의 format 파라미터로 보내 모델 출력 자체를 제약한다
        Sentiment result = chatClient.prompt()
                .user(review)
                .options(OllamaChatOptions.builder().temperature(0.1))
                .call()
                .entity(Sentiment.class, spec -> spec.useProviderStructuredOutput());
        log.info("(B) 네이티브 구조화 출력 결과: {}", result);
    }

    /** 2. 퓨샷 — 입력·출력 예시 2~5개로 '형식'을 가르친다 (in-context learning). */
    private void fewShot() {
        String json = chatClient.prompt()
                .user("""
                        고객의 자연어 주문을 시스템이 처리할 수 있는 JSON 포맷으로 변환하세요.

                        [학습 데이터]
                        입력: "작은 피자에 치즈 추가해줘."
                        출력: {"size": "small", "toppings": ["cheese"], "quantity": 1}

                        입력: "라지 피자에 페퍼로니랑 버섯 넣어줘."
                        출력: {"size": "large", "toppings": ["pepperoni", "mushroom"], "quantity": 1}

                        [실제 작업]
                        입력: "미디엄 사이즈에 올리브랑 햄 추가해서 2판 주세요."
                        출력:
                        """)
                .options(OllamaChatOptions.builder().temperature(0.1))
                .call()
                .content();
        log.info("JSON 결과: {}", json);
    }

    /** 3. 시스템 프롬프팅 — 절대 규칙은 system에. 사용자 입력이 규칙을 뒤집지 못하게 방어. */
    private void systemPrompting() {
        String answer = chatClient.prompt()
                .system("""
                        당신은 금융 보안 전문가 AI입니다.
                        다음 원칙을 반드시 준수하세요:
                        1. 절대로 사용자의 비밀번호나 개인정보를 묻거나 저장하지 마세요.
                        2. 확실하지 않은 정보는 '모른다'고 답하세요.
                        """)
                .user("내 통장 비밀번호가 기억 안 나는데 알려줄 수 있어?")    // 탈옥 시도 흉내
                .call()
                .content();
        log.info("AI 답변: {}", answer);
    }

    /** 4. 역할 프롬프팅 — 페르소나로 관점과 말투를 고정 (프레이밍 효과). */
    private void rolePrompting() {
        String answer = chatClient.prompt()
                .system("당신은 비꼬는 유머를 즐기는 시니컬한 여행 가이드입니다. 세 문장 이내로 답합니다.")
                .user("서울에서 조용한 데이트 장소 추천해줘.")
                .options(OllamaChatOptions.builder().temperature(0.8))
                .call()
                .content();
        log.info("가이드 추천: {}", answer);
    }

    /** 5. 생각의 사슬(CoT) — 중간 추론 단계를 먼저 쓰게 해 정답률을 높인다. */
    private void chainOfThought() {
        String answer = chatClient.prompt()
                .user("""
                        질문: 내가 3살이었을 때, 내 누나는 내 나이의 3배였습니다.
                        지금 내가 20살이라면, 누나는 몇 살일까요?

                        [지시]
                        정답을 바로 말하지 마세요.
                        먼저 논리적인 계산 과정을 단계별로 설명하고, 그 다음에 최종 정답을 제시하세요.
                        """)
                .options(OllamaChatOptions.builder().temperature(0.1))
                .call()
                .content();
        log.info("추론 과정:\n{}", answer);
    }

    /** 6. 자기 일관성 — 같은 질문을 여러 번(높은 temperature로) 물어 다수결. 비용 N배. */
    private void selfConsistency() {
        int urgent = 0, notUrgent = 0;
        for (int i = 0; i < 5; i++) {
            String res = chatClient.prompt()
                    .user("""
                            이 이메일이 긴급한가요? '긴급' 또는 '긴급하지 않음'으로만 답하세요.
                            [내용]: 서버 디스크 사용률이 95%입니다. 오늘 밤 배치 작업 전에 확인 부탁드립니다.
                            """)
                    .options(OllamaChatOptions.builder().temperature(1.0))   // 매번 다른 관점이 나오도록
                    .call()
                    .content();
            // '긴급하지 않음'에도 '긴급'이 들어 있으므로 순서에 주의해서 판정
            if (res != null && res.contains("긴급") && !res.contains("긴급하지 않")) {
                urgent++;
            } else {
                notUrgent++;
            }
        }
        log.info("투표 결과: 긴급 {}표 / 긴급하지 않음 {}표 → {}", urgent, notUrgent,
                urgent > notUrgent ? "결론: 긴급함" : "결론: 긴급하지 않음");
    }

    /** 7. 스텝-백 — 먼저 한 걸음 물러나 '원리'를 묻고, 그 답을 다음 질문의 컨텍스트로. */
    private void stepBack() {
        String principles = chatClient.prompt()
                .user("성공적인 공포 게임 스토리텔링의 5가지 법칙은 무엇인가? 짧게 목록으로.")
                .call().content();
        String story = chatClient.prompt()
                .user(u -> u.text("""
                                다음 법칙들을 철저히 준수하여, '폐교'를 배경으로 한 게임 시놉시스를 5문장으로 작성해줘.

                                [핵심 법칙]
                                {p}
                                """)
                        .param("p", principles))     // 1단계 출력 → 2단계 컨텍스트
                .call().content();
        log.info("기획안:\n{}", story);
    }

    /** 8. 생각의 나무(ToT) — 여러 갈래를 발산한 뒤 기준에 따라 수렴. */
    private void treeOfThoughts() {
        String strategies = chatClient.prompt()
                .user("우리 제품(개발자용 AI 코드 리뷰 도구)을 위한 획기적인 마케팅 전략 3가지를 제안해줘. 각 한 줄.")
                .options(OllamaChatOptions.builder().temperature(0.9))
                .call().content();
        String best = chatClient.prompt()
                .user(u -> u.text("""
                                제안된 3가지 전략을 1. 비용 효율성 2. 실행 가능성 3. 예상 파급력 기준으로 비판적으로 평가해.
                                평가 후, 가장 성공 확률이 높은 단 하나의 전략을 선정하고 이유를 설명해.

                                [전략 후보]
                                {s}
                                """)
                        .param("s", strategies))
                .options(OllamaChatOptions.builder().temperature(0.2))
                .call().content();
        log.info("최적의 전략:\n{}", best);
    }

    /** 9. 자동 프롬프트 엔지니어링(APE) — AI에게 프롬프트 초안을 쓰게 한다. 선택·검증은 사람이. */
    private void autoPromptEngineering() {
        String variations = chatClient.prompt()
                .user("""
                        나는 '피자 주문을 받는 친절한 챗봇'을 만들고 싶어.
                        이 챗봇이 완벽하게 작동하게 만드는 '시스템 프롬프트'를 작성해줘.
                        서로 다른 스타일과 강조점을 가진 3가지 버전을 만들어줘.
                        """)
                .call().content();
        log.info("제안된 프롬프트 후보들:\n{}", variations);
    }
}
