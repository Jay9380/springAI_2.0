package kr.jay.springai.appendix;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * [부록 A, 노트 2.8] 스타터 두 개(Ollama + OpenAI)가 함께 있을 때 '설정 한 줄'이 무엇을 바꾸는지.
 * 모델을 호출하지 않는다 — 기동(빈 생성)만 본다. application.yml을 읽지 않게 해서 '아무것도 고르지 않은' 상태를 만든다.
 */
class ProviderSelectionTest {

    static ConfigurableApplicationContext start(String... props) {
        return new SpringApplicationBuilder(AppendixApplication.class)
                .web(WebApplicationType.NONE)
                .properties("spring.config.name=__none__", "spring.ai.cli.step=none", "spring.main.banner-mode=off")
                .properties(props)
                .run();
    }

    @Test
    void nothingSelected_noKey_failsAtStartup() {
        // OpenAI 자동 구성은 matchIfMissing=true → 쓰지 않는 OpenAI 모델 빈까지 만들려다 API 키가 없어 기동 실패 (책 p.655의 함정)
        // 실측: 처음 실패한 빈은 채팅이 아니라 '오디오 음성(TTS)' 모델이었다 — 이 앱은 오디오를 전혀 쓰지 않는다
        assertThatThrownBy(() -> start().close())
                .hasStackTraceContaining("openAiSdkAudioSpeechModel")
                .hasStackTraceContaining("At least one credential source must be specified");
    }

    @Test
    void nothingSelected_withKey_twoChatModels_failLaterWhenFirstInjected() {
        // 키가 있으면 기동은 된다. 하지만 Ollama·OpenAI 둘 다 ChatModel 빈을 만든다.
        // ChatClient.Builder는 프로토타입이라 기동 때 만들지 않으므로, 충돌은 처음 주입받는 순간(어떤 단계를 실행할 때) 터진다.
        try (var ctx = start("spring.ai.openai.api-key=sk-test-not-real")) {
            assertThat(ctx.getBeansOfType(ChatModel.class)).hasSize(2);
            assertThatThrownBy(() -> ctx.getBean(ChatModel.class))
                    .isInstanceOf(org.springframework.beans.factory.NoUniqueBeanDefinitionException.class);
        }
    }

    @Test
    void selectingProviders_startsWithoutAnyOpenAiKey() {
        try (var ctx = start("spring.ai.model.chat=ollama", "spring.ai.model.embedding=ollama",
                "spring.ai.model.image=none", "spring.ai.model.audio.speech=none",
                "spring.ai.model.audio.transcription=none", "spring.ai.model.moderation=none")) {
            assertThat(ctx.getBeansOfType(ChatModel.class).values()).singleElement()
                    .satisfies(m -> assertThat(m.getClass().getSimpleName()).isEqualTo("OllamaChatModel"));
            assertThat(ctx.getBeansOfType(EmbeddingModel.class)).hasSize(1);
        }
    }

    @Test
    void switchingToOpenAi_isOnlyConfiguration() {
        // 키 값이 가짜여도 빈 생성은 된다 (실제 호출 때 인증). 같은 코드가 OpenAiChatModel에 붙는다.
        try (var ctx = start("spring.ai.model.chat=openai", "spring.ai.model.embedding=openai",
                "spring.ai.model.image=none", "spring.ai.model.audio.speech=none",
                "spring.ai.model.audio.transcription=none", "spring.ai.model.moderation=none",
                "spring.ai.openai.api-key=sk-test-not-real")) {
            assertThat(ctx.getBean(ChatModel.class).getClass().getSimpleName()).isEqualTo("OpenAiChatModel");
            assertThat(ctx.getBean(EmbeddingModel.class).getClass().getSimpleName()).isEqualTo("OpenAiEmbeddingModel");
        }
    }

    @Test
    void openAiKeyGuard_rejectsBlankOrUnresolvedKey_acceptsRealLookingKey() {
        assertThatThrownBy(() -> new kr.jay.springai.appendix.support.OpenAiKeyGuard("")).hasMessageContaining("OPENAI_API_KEY");
        assertThatThrownBy(() -> new kr.jay.springai.appendix.support.OpenAiKeyGuard("${OPENAI_API_KEY}"))
                .hasMessageContaining("OPENAI_API_KEY");                           // 자리표시자 글자가 그대로 들어온 경우
        new kr.jay.springai.appendix.support.OpenAiKeyGuard("sk-test-not-real");   // 형식만 보고 통과 (진위는 첫 호출에서)
    }
}
