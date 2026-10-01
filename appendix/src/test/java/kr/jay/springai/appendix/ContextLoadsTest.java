package kr.jay.springai.appendix;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

/** 기본 설정(application.yml: 공급자 선택 + 쓰지 않는 OpenAI 모델 끔)으로는 OpenAI 키 없이 기동한다. */
@SpringBootTest(properties = "spring.ai.cli.step=none")
class ContextLoadsTest {

    @Autowired
    ApplicationContext context;

    @Test
    void startsWithoutOpenAiKey_andNoRunner() {
        assertThat(context.getBeansOfType(CommandLineRunner.class)).isEmpty();
    }
}
