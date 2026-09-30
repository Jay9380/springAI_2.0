package kr.jay.springai.ch01;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

/**
 * 1장 실습 진입점.
 *
 * <p>실행: {@code ./mvnw -pl chapter01 spring-boot:run}  (Ollama와 qwen3.5:4b가 필요)
 *
 * <p>세 가지를 순서대로 확인한다.
 * <ol>
 *   <li>첫 대화 - 스프링 방식(DI + 자동설정)으로 LLM을 부른다.</li>
 *   <li>응답의 실체 - 텍스트 뒤에 붙어 오는 토큰 수와 종료 이유를 본다.</li>
 *   <li>LLM은 기억하지 않는다 - 두 번 따로 물으면 첫 번째 대화를 모른다.</li>
 * </ol>
 */
@SpringBootApplication
public class Chapter01Application {

    private static final Logger log = LoggerFactory.getLogger(Chapter01Application.class);

    public static void main(String[] args) {
        SpringApplication.run(Chapter01Application.class, args);
    }

    /**
     * CommandLineRunner: 스프링 컨테이너가 다 뜬 뒤 딱 한 번 실행되는 코드.
     * 웹 서버 없이(web-application-type: none) 실험 코드를 돌리기에 가장 간단한 방법이다.
     *
     * <p>@Profile("!test") - 단위 테스트에서는 이 러너가 실제 모델을 부르지 않도록 끈다.
     */
    @Bean
    @Profile("!test")
    CommandLineRunner firstConversation(HelloAiService hello) {
        return args -> {
            // ① 첫 대화
            String q1 = "스프링 AI를 한 문장으로 소개해줘.";
            log.info(">>> 질문: {}", q1);
            log.info(">>> 답변: {}", hello.ask(q1));

            // ② 같은 호출을 메타데이터까지 받아 보기
            HelloAiService.Answer answer = hello.askWithMetadata("KPI가 무엇인지 두 문장으로 설명해줘.");
            log.info(">>> 답변: {}", answer.text());
            log.info(">>> 모델={}, 입력 토큰={}, 출력 토큰={}, 종료 이유={}",
                    answer.model(), answer.promptTokens(), answer.completionTokens(), answer.finishReason());

            // ③ LLM은 상태가 없다(stateless).
            //    두 호출은 서로 완전히 독립이다. 앱이 지난 대화를 다시 보내 주지 않으면 모델은 모른다.
            //    이것을 해결하는 것이 2장의 '대화 메모리'다.
            log.info(">>> 답변: {}", hello.ask("내 이름은 제이야. 기억해 둬."));
            log.info(">>> 답변: {}  <- 이름을 모르는 것이 정상", hello.ask("내 이름이 뭐라고 했지?"));
        };
    }
}
