package kr.jay.springai.ch03;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

/** 빈 배선 확인. 일치하는 step이 없으면 러너가 하나도 등록되지 않아 모델을 부르지 않는다. */
@SpringBootTest(properties = "spring.ai.cli.step=none")
class ContextLoadsTest {

    @Autowired
    ApplicationContext context;

    @Test
    void contextLoads_andNoRunnerIsActive_whenStepDoesNotMatch() {
        assertThat(context.getBeansOfType(CommandLineRunner.class)).isEmpty();
    }
}
