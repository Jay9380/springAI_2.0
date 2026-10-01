package kr.jay.springai.appendix;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 부록 실습 진입점 (spring.ai.cli.step으로 단계 선택).
 *
 * <ul>
 *   <li>A. 공급자 전환 — 코드는 그대로, 의존성과 설정만 바꿔 Ollama ↔ OpenAI</li>
 *   <li>B. AI 평가 — LLM as a Judge: 관련성·사실성 평가, 평가로 에이전트 루프 보강</li>
 *   <li>C·D. 외부 에이전트 연결·플레이그라운드 — 코드가 아니라 연결 방법 (README)</li>
 * </ul>
 */
@SpringBootApplication
public class AppendixApplication {

    public static void main(String[] args) {
        SpringApplication.run(AppendixApplication.class, args);
    }
}
