package kr.jay.springai.ch02.demo;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * [2.3.4] ChatClient를 스프링 빈으로 구성하는 두 가지 방법.
 *
 * <p>(1) 자동 구성된 ChatClient.Builder를 받아 쓴다 (예제 2.11)
 * <br>(2) ChatModel을 직접 받아 ChatClient.builder(chatModel)로 만든다 (예제 2.12)
 *     — 모델이 여러 개일 때(예: 로컬 Ollama + 클라우드 OpenAI) 어떤 모델을 쓸지 명시하는 방법.
 *
 * <p>이 실습에는 Ollama 하나뿐이라, 같은 모델에 '성격(system 프롬프트)'만 다른 클라이언트 두 개를 만든다.
 * 사용하는 쪽에서는 {@code @Qualifier("analystClient")}처럼 이름으로 골라 주입받는다.
 */
@Configuration
public class ChatClientConfig {

    /** (1) 빌더 주입 방식. defaultSystem: 이 클라이언트로 보내는 모든 요청 앞에 붙는 system 메시지. */
    @Bean
    ChatClient friendlyClient(ChatClient.Builder builder) {
        return builder
                .defaultSystem("당신은 친절한 AI 어시스턴트입니다. 쉬운 말로 짧게 답합니다.")
                .build();
    }

    /** (2) ChatModel 직접 주입 방식. 실무에서는 여기에 다른 제공자의 ChatModel을 넣는다. */
    @Bean
    ChatClient analystClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultSystem("당신은 논리적인 분석가입니다. 결론을 먼저 말하고 근거를 번호로 나열합니다.")
                .build();
    }
}
