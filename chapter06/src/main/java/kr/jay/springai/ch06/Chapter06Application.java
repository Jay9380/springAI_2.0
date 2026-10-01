package kr.jay.springai.ch06;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 6장 실습 진입점 — AI 에이전트 (spring.ai.cli.step으로 단계 선택).
 *
 * <p>6장의 한 문장: <b>에이전트 = LLM + 툴 + 루프.</b> 새 프레임워크가 아니라 2~5장의 부품(ChatClient, 어드바이저,
 * 툴, 메모리, MCP)을 '루프' 관점에서 다시 배치한 것이다. 그리고 책은 "단순한 워크플로부터 시작해
 * 필요할 때만 자율 에이전트로 가라"고 거듭 말한다.
 */
@SpringBootApplication
public class Chapter06Application {

    public static void main(String[] args) {
        SpringApplication.run(Chapter06Application.class, args);
    }
}
