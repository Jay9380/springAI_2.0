package kr.jay.springai.ch02;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.template.st.StTemplateRenderer;
import org.springframework.core.io.ClassPathResource;

/**
 * 2.4 PromptTemplate 동작을 모델 없이 확인한다.
 * 템플릿은 순수 문자열 처리라서 단위 테스트로 검증하기 가장 좋은 부분이다.
 */
class PromptTemplateTest {

    @Test
    void render_fillsVariables() {
        var t = new PromptTemplate("Tell me a {adjective} joke about {topic}.");
        assertThat(t.render(Map.of("adjective", "funny", "topic", "Java")))
                .isEqualTo("Tell me a funny joke about Java.");
    }

    @Test
    void render_failsWhenVariableIsMissing() {
        // 실패 경로: 기본 검증 모드는 THROW. 변수 하나를 빼먹으면 모델을 부르기 전에 예외가 난다.
        // → 빈칸 그대로 모델에 보내는 사고를 막아 준다.
        var t = new PromptTemplate("Tell me a {adjective} joke about {topic}.");
        assertThatThrownBy(() -> t.render(Map.of("adjective", "funny")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("topic");
    }

    @Test
    void defaultDelimiter_breaksOnJsonExample() {
        // 실패 경로: 중괄호가 들어간 JSON 예시는 기본 구분자에서 변수로 오인된다.
        var t = new PromptTemplate("Return JSON like { \"albums\": [\"A\"] } for {artist}.");
        assertThatThrownBy(() -> t.render(Map.of("artist", "Beatles")))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void customDelimiter_keepsJsonBraces() {
        var t = PromptTemplate.builder()
                .renderer(StTemplateRenderer.builder().startDelimiterToken('<').endDelimiterToken('>').build())
                .template("Return JSON like { \"albums\": [\"A\"] } for <artist>.")
                .build();
        assertThat(t.render(Map.of("artist", "Beatles")))
                .isEqualTo("Return JSON like { \"albums\": [\"A\"] } for Beatles.");
    }

    @Test
    void systemAndUserTemplates_produceMessagesWithRoles() {
        Message sys = new SystemPromptTemplate("당신은 {role} 전문가입니다.").createMessage(Map.of("role", "Java"));
        Message user = new PromptTemplate("{topic} 트렌드").createMessage(Map.of("topic", "Spring"));
        Prompt prompt = new Prompt(List.of(sys, user));

        assertThat(prompt.getInstructions()).extracting(Message::getMessageType)
                .containsExactly(MessageType.SYSTEM, MessageType.USER);
        assertThat(prompt.getInstructions().get(0).getText()).isEqualTo("당신은 Java 전문가입니다.");
    }

    @Test
    void stFileResource_isLoadedAndRendered() {
        var t = new PromptTemplate(new ClassPathResource("prompts/code-refactoring.st"));
        String rendered = t.render(Map.of("language", "Java", "code_snippet", "int a = 1;"));
        assertThat(rendered).contains("Java 코드를 리팩터링").contains("int a = 1;");
    }

    @Test
    void variableValue_withBraces_isNotParsedAsTemplate() {
        // 값(value)에 든 중괄호는 템플릿 문법으로 해석되지 않는다 — 코드 조각을 넣어도 안전
        var t = new PromptTemplate("[코드]\n{code}");
        assertThat(t.render(Map.of("code", "if (x) { return; }"))).endsWith("if (x) { return; }");
    }
}
