package kr.jay.springai.ch06cli;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 6.6 엔터프라이즈 스프링 AI 에이전트 CLI.
 *
 * <p>패키지 구조 자체가 4-티어 아키텍처다 (책 예제 6.28).
 * <pre>
 *   channel/          T1 채널        사용자 접점: 입력, 스트리밍 출력, 승인·질문 응답
 *   orchestration/    T2 오케스트레이션 '지휘하는 지능': ChatClient + 어드바이저 체인 + 안전 가드
 *   capability/local  T3 능력(로컬)    같은 프로세스의 @Tool
 *   capability/remote T3 능력(원격)    MCP 서버 툴, 승인 핸들러, 연결별 인증
 *   resources/        T4 설정·데이터   agents/*.md, skills/*.md, 모델 설정
 * </pre>
 * 설계 원칙: "툴 경계가 곧 시스템 경계다." 로컬 메서드도, 같은 JVM의 하위 에이전트도, 원격 MCP 서버도 메인 에이전트에게는 모두 툴이다.
 */
@SpringBootApplication
public class Chapter06AgentCliApplication {

    public static void main(String[] args) {
        SpringApplication.run(Chapter06AgentCliApplication.class, args);
    }
}
