package kr.jay.springai.appendix.step;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * [부록 A] 공급자 전환 — 같은 코드가 어떤 모델에 붙었는지 보여 준다.
 *
 * <p>실행: {@code --spring.ai.cli.step=appx-provider} (기본 Ollama)
 * <br>OpenAI: {@code --spring.profiles.active=openai --spring.ai.cli.step=appx-provider} (OPENAI_API_KEY 필요, 과금)
 *
 * <p>이 클래스는 ChatModel·EmbeddingModel·ChatClient 인터페이스만 안다. 공급자는 의존성과 spring.ai.model.* 설정이 정한다.
 * 책이 본문 내내 공통 API를 강조한 이유 — 처음부터 공급자 SDK에 묶었다면 이 전환이 코드 수정이 된다.
 */
@Component
@ConditionalOnProperty(prefix = "spring.ai.cli", name = "step", havingValue = "appx-provider")
public class AppxStepA_Provider implements CommandLineRunner {

    private final ChatModel chatModel;
    private final EmbeddingModel embeddingModel;
    private final ChatClient chatClient;

    public AppxStepA_Provider(ChatModel chatModel, EmbeddingModel embeddingModel, ChatClient.Builder builder) {
        this.chatModel = chatModel;
        this.embeddingModel = embeddingModel;
        this.chatClient = builder.build();
    }

    @Override
    public void run(String... args) {
        System.out.println("ChatModel      : " + chatModel.getClass().getSimpleName() + " (model=" + chatModel.getOptions().getModel() + ")");
        System.out.println("EmbeddingModel : " + embeddingModel.getClass().getSimpleName());
        System.out.println("응답           : " + chatClient.prompt().user("한 문장으로 자기소개해 줘").call().content());
    }
}
