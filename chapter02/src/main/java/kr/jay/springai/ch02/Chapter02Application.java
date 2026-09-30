package kr.jay.springai.ch02;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 2장 실습 진입점.
 *
 * <p>이 앱에는 CommandLineRunner가 여러 개 있다. 그런데 실행되는 것은 하나뿐이다.
 * 각 러너에 {@code @ConditionalOnProperty(prefix = "spring.ai.cli", name = "step", havingValue = "...")}를
 * 붙여서, 설정값 {@code spring.ai.cli.step}과 이름이 같은 러너만 빈으로 등록되게 했기 때문이다 (책 예제 2.74).
 *
 * <pre>
 *   ./mvnw -pl chapter02 spring-boot:run -Dspring-boot.run.arguments=--spring.ai.cli.step=ch2-step1
 * </pre>
 */
@SpringBootApplication
public class Chapter02Application {

    public static void main(String[] args) {
        SpringApplication.run(Chapter02Application.class, args);
    }
}
