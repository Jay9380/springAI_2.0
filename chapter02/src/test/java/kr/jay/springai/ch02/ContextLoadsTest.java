package kr.jay.springai.ch02;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

/**
 * 스프링 컨텍스트가 뜨는지(빈 배선) 확인한다.
 * step을 존재하지 않는 값으로 주면 어떤 러너도 등록되지 않아 모델을 부르지 않는다.
 * Ollama 자동설정은 기동 시 서버에 접속하지 않으므로 Ollama 없이도 통과한다.
 */
@SpringBootTest(properties = "spring.ai.cli.step=none")
class ContextLoadsTest {

    @Autowired
    ApplicationContext context;

    @Test
    void contextLoads_andNoRunnerIsActive_whenStepDoesNotMatch() {
        assertThat(context.getBeansOfType(CommandLineRunner.class)).isEmpty();
    }
}
