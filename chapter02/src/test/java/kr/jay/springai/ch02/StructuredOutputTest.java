package kr.jay.springai.ch02;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import kr.jay.springai.ch02.demo.PipeDelimitedListConverter;
import kr.jay.springai.ch02.demo.StructuredOutputDemo.MovieAnalysis;
import kr.jay.springai.ch02.step.Ch2Step3_StructuredOutput.Recipe;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.converter.ListOutputConverter;
import org.springframework.core.convert.support.DefaultConversionService;

/**
 * 2.6 구조화 출력의 두 단계 — (1) 요청 전처리: 형식 지시문 부착, (2) 응답 후처리: 텍스트 → 객체.
 * 둘 다 모델 없이 검증할 수 있다.
 */
class StructuredOutputTest {

    @Test
    void beanConverter_formatContainsJsonSchemaOfRecord() {
        String format = new BeanOutputConverter<>(Recipe.class).getFormat();
        assertThat(format)
                .contains("JSON Schema")
                .contains("\"dishName\"").contains("\"cookingMinutes\"").contains("\"mainIngredient\"");
    }

    @Test
    void jsonPropertyOrder_putsReasoningFirstInSchema() {
        String format = new BeanOutputConverter<>(MovieAnalysis.class).getFormat();
        assertThat(format.indexOf("\"reasoning\"")).isLessThan(format.indexOf("\"genre\""));
    }

    @Test
    void beanConverter_convertsJson_evenWrappedInMarkdownFence() {
        // 2.0.1 BeanOutputConverter는 변환 전에 ```json 코드블록 표시와 <think> 태그를 걷어낸다
        var converter = new BeanOutputConverter<>(Recipe.class);
        Recipe r = converter.convert("""
                ```json
                {"dishName":"김치찌개","cookingMinutes":35,"mainIngredient":"김치"}
                ```
                """);
        assertThat(r).isEqualTo(new Recipe("김치찌개", 35, "김치"));
    }

    @Test
    void beanConverter_failsOnNonJsonAnswer() {
        // 실패 경로: 모델이 형식 지시를 무시하고 문장으로 답하면 변환 예외가 난다
        var converter = new BeanOutputConverter<>(Recipe.class);
        assertThatThrownBy(() -> converter.convert("김치찌개는 35분 정도 걸리고 김치가 주재료입니다."))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void entity_appendsFormatInstructionToUserMessage() {
        // ChatClient.entity()가 실제로 무엇을 보내는지 가짜 모델로 엿본다
        List<Prompt> sent = new ArrayList<>();
        ChatModel fake = prompt -> {
            sent.add(prompt);
            return new ChatResponse(List.of(new Generation(new AssistantMessage(
                    "{\"dishName\":\"비빔밥\",\"cookingMinutes\":20,\"mainIngredient\":\"밥\"}"))));
        };

        Recipe r = ChatClient.builder(fake).build().prompt().user("비빔밥 알려줘").call().entity(Recipe.class);

        assertThat(r.dishName()).isEqualTo("비빔밥");
        String userText = sent.get(0).getUserMessage().getText();
        assertThat(userText).startsWith("비빔밥 알려줘").contains("JSON Schema");
    }

    @Test
    void listConverter_splitsCommaSeparated() {
        var c = new ListOutputConverter(new DefaultConversionService());
        assertThat(c.convert("바닐라, 초코, 딸기")).containsExactly("바닐라", "초코", "딸기");
    }

    @Test
    void pipeConverter_keepsCommasInsideItems() {
        var c = new PipeDelimitedListConverter();
        assertThat(c.convert("부산, 항구 도시 | 대구, 분지 |  | 광주, 예향"))
                .containsExactly("부산, 항구 도시", "대구, 분지", "광주, 예향");
    }
}
