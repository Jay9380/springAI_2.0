package kr.jay.springai.ch03;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 3장 실습 진입점 — 2장과 같은 '단계 선택' 방식 (spring.ai.cli.step).
 *
 * <p>RAG는 두 파이프라인으로 나뉜다 (책 3.1.3).
 * <pre>
 *   오프라인(ETL) : 읽기(Step1) → 변환·청킹(Step2) → 적재(Step3) → 임베딩·벡터 저장(Step4)
 *   런타임(질문)  : 질문 변환 → 검색 → 후처리 → 프롬프트 증강 → 생성 (Step5, Step6, final)
 * </pre>
 */
@SpringBootApplication
public class Chapter03Application {

    public static void main(String[] args) {
        SpringApplication.run(Chapter03Application.class, args);
    }
}
