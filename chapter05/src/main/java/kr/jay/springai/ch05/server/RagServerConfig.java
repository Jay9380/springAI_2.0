package kr.jay.springai.ch05.server;

import kr.jay.springai.ch05.support.RagAnswerService;
import kr.jay.springai.ch05.support.RagDocumentLoader;
import kr.jay.springai.ch05.support.RagKnowledgeBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * 서버 프로파일 전용 빈 — 임베딩 모델(bge-m3), 벡터 저장소, 지식 베이스, 답변 서비스.
 * 클라이언트 모드에는 임베딩 모델 자체가 없으므로(spring.ai.model.embedding=none) 이 설정도 서버에서만 켠다.
 */
@Configuration
@Profile("server")
public class RagServerConfig {

    private static final Logger log = LoggerFactory.getLogger(RagServerConfig.class);

    @Bean
    RagKnowledgeBase ragKnowledgeBase(EmbeddingModel embeddingModel) {
        RagKnowledgeBase kb = new RagKnowledgeBase(SimpleVectorStore.builder(embeddingModel).build(), new RagDocumentLoader());
        int chunks = kb.index();                   // 기동 시점에 적재 — 실패하면 서버가 뜨지 않는다
        log.info("RAG 지식 베이스 적재 완료: 청크 {}개 {}", chunks, kb.chunksPerSource());
        return kb;
    }

    @Bean
    RagAnswerService ragAnswerService(ChatClient.Builder builder, RagKnowledgeBase kb) {
        return new RagAnswerService(builder, kb);
    }
}
