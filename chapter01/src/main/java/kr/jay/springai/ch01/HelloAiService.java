package kr.jay.springai.ch01;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;

/**
 * 1장 실습 - "ChatClient 한 줄 = HTTP JSON 왕복 한 번"을 눈으로 확인한다.
 *
 * <p>책 1.2.2는 "의존성을 추가하고 설정만 넣으면 ChatClient.Builder가 빈으로 등록되고,
 * 이를 주입받아 build()로 ChatClient를 만든다"고 설명한다. 이 클래스가 바로 그 모양이다.
 *
 * <p>호출이 내려가는 길 (위 → 아래):
 * <pre>
 *   ChatClient (편의 API)
 *     → Advisor 체인 (이 장에서는 없음)
 *       → ChatModel 인터페이스 (OllamaChatModel 구현체)
 *         → POST http://localhost:11434/api/chat  (JSON)
 * </pre>
 */
@Service
public class HelloAiService {

    private final ChatClient chatClient;

    /**
     * ChatClient.Builder는 스프링 AI 자동설정이 만들어 준 빈이다.
     * 우리가 직접 new 하지 않는다 — 스프링의 DI를 그대로 쓰는 것이 스프링 AI의 핵심 철학(책 1.2.2).
     */
    public HelloAiService(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    /**
     * 가장 짧은 형태의 호출. 응답 텍스트만 받는다.
     *
     * <pre>
     *   prompt()   : 요청을 만들기 시작
     *   user(q)    : role=user 메시지로 질문을 넣음
     *   call()     : 동기 호출 (스트리밍은 stream())
     *   content()  : 응답에서 텍스트만 꺼냄
     * </pre>
     */
    public String ask(String question) {
        return chatClient.prompt()
                .user(question)
                .call()
                .content();
    }

    /**
     * 같은 호출이지만 content() 대신 chatResponse()로 '응답 전체'를 받는다.
     * 여기에는 모델이 돌려준 JSON의 메타데이터가 들어 있다.
     *
     * <ul>
     *   <li>promptTokens     : 입력 토큰 수 (Ollama JSON의 prompt_eval_count)</li>
     *   <li>completionTokens : 출력 토큰 수 (Ollama JSON의 eval_count)</li>
     *   <li>finishReason     : 왜 멈췄나. stop = 스스로 끝냄, length = 최대 길이에 걸려 잘림
     *                          (값은 제공자가 준 문자열 그대로다. Ollama는 소문자 "stop"/"length")</li>
     * </ul>
     * 토큰 수는 비용과 지연의 기준이고, finishReason이 length면 답이 잘린 것이다.
     */
    public Answer askWithMetadata(String question) {
        ChatResponse response = chatClient.prompt()
                .user(question)
                .call()
                .chatResponse();

        if (response == null || response.getResult() == null) {
            return new Answer("", null, null, null, null);
        }
        Usage usage = response.getMetadata().getUsage();
        return new Answer(
                response.getResult().getOutput().getText(),
                response.getMetadata().getModel(),
                usage.getPromptTokens(),
                usage.getCompletionTokens(),
                response.getResult().getMetadata().getFinishReason());
    }

    /** 응답 텍스트와 메타데이터를 함께 담는 값 객체. */
    public record Answer(String text, String model, Integer promptTokens,
                         Integer completionTokens, String finishReason) {
    }
}
