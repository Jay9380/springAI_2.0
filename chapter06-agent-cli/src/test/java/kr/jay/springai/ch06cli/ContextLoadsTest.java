package kr.jay.springai.ch06cli;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

/** 빈 배선 확인. 에이전트 빈은 @Lazy라 step이 없으면 만들어지지 않는다(모델·폴더 없이도 기동). */
@SpringBootTest(properties = "spring.ai.cli.step=none")
class ContextLoadsTest {

    @Autowired
    ApplicationContext context;

    @Test
    void contextLoads_noRunner_andAgentsNotCreatedYet() {
        assertThat(context.getBeansOfType(CommandLineRunner.class)).isEmpty();
        assertThat(context.containsBean("coreAgent")).isTrue();
    }
}
