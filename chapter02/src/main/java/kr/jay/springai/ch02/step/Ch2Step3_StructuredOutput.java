package kr.jay.springai.ch02.step;

import kr.jay.springai.ch02.support.ChatConsole;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * [Step 3 · 2.6] call()을 이용한 구조화 출력 — 요리 이름을 넣으면 Recipe 객체로 받는다.
 *
 * <p>실행: {@code --spring.ai.cli.step=ch2-step3}
 *
 * <p>이 단계만 stream()이 아니라 call()을 쓴다. JSON은 '완성된 전체'가 있어야 객체로 바꿀 수 있기 때문이다.
 *
 * <p>{@code .entity(Recipe.class, spec -> spec.useProviderStructuredOutput().validateSchema())} 한 줄이 하는 일:
 * <ol>
 *   <li>BeanOutputConverter가 Recipe 레코드를 분석해 JSON 스키마를 만든다</li>
 *   <li>useProviderStructuredOutput: 그 스키마를 Ollama의 format 파라미터로 직접 보낸다 (네이티브 구조화 출력)
 *       → 모델의 출력 자체가 스키마 모양으로 제약된다</li>
 *   <li>응답 JSON을 Jackson으로 Recipe 객체로 역직렬화한다</li>
 *   <li>validateSchema: 결과가 스키마를 어기면 자동으로 다시 요청한다</li>
 * </ol>
 */
@Component
@ConditionalOnProperty(prefix = "spring.ai.cli", name = "step", havingValue = "ch2-step3")
public class Ch2Step3_StructuredOutput implements CommandLineRunner {

    /** 필드 이름이 곧 JSON 키가 된다. 주석은 모델에 전달되지 않으므로, 의미가 드러나는 이름을 쓴다. */
    public record Recipe(
            String dishName,        // 요리 이름
            int cookingMinutes,     // 조리 시간(분)
            String mainIngredient   // 대표 재료
    ) {
    }

    private final ChatClient chatClient;

    public Ch2Step3_StructuredOutput(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @Override
    public void run(String... args) {
        ChatConsole.run("[Ch2] Step3: 구조화한 출력 (Recipe, call + entity) — 요리 이름을 입력하세요", input -> {
            Recipe recipe = chatClient.prompt()
                    .user("'" + input + "' 요리의 정보를 알려줘. 조리 시간(분)과 대표 재료를 알려줘.")
                    .call()
                    .entity(Recipe.class, spec -> spec.useProviderStructuredOutput().validateSchema());
            printRecipe(recipe);
        });
    }

    static void printRecipe(Recipe recipe) {
        if (recipe == null) {
            System.out.print("(응답 없음)");
            return;
        }
        // 화면의 표는 '문자열 포매팅' 결과일 뿐, 프로그램 안에서는 이미 완성된 Recipe 객체다
        System.out.println();
        System.out.println("┌──────────────────────────────");
        System.out.println("│ " + recipe.dishName());
        System.out.println("├ 조리 시간 : " + recipe.cookingMinutes() + "분");
        System.out.println("├ 대표 재료 : " + recipe.mainIngredient());
        System.out.print("└──────────────────────────────");
    }
}
