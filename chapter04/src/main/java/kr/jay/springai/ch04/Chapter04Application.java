package kr.jay.springai.ch04;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 4장 실습 진입점 — 툴 호출 (spring.ai.cli.step으로 단계 선택).
 *
 * <p>툴 호출의 핵심은 한 문장이다: <b>모델은 도구를 실행하지 않는다. "이 도구를 이 인자로 불러 달라"고 요청할 뿐이고,
 * 실행은 언제나 우리 애플리케이션이 한다.</b> 그래서 무엇을 도구로 노출할지, 언제 실행할지는 개발자의 결정이다.
 */
@SpringBootApplication
public class Chapter04Application {

    public static void main(String[] args) {
        SpringApplication.run(Chapter04Application.class, args);
    }
}
