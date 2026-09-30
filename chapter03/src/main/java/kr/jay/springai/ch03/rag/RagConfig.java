package kr.jay.springai.ch03.rag;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** RagService를 빈으로 등록한다. (RagService 자체는 테스트에서 가짜 모델로 만들 수 있게 @Service를 붙이지 않았다) */
@Configuration
public class RagConfig {

    @Bean
    RagService ragService(ChatClient.Builder builder, VectorStore vectorStore) {
        return new RagService(builder, vectorStore);
    }
}
