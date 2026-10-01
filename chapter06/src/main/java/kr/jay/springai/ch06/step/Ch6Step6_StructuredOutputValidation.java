package kr.jay.springai.ch06.step;

import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.StructuredOutputValidationAdvisor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * [Step 6 · 6.3.5 예제 6.15~6.16] 구조화 출력 교정 루프.
 *
 * <p>실행: {@code --spring.ai.cli.step=ch6-validate}  (대화형 아님)
 *
 * <ul>
 *   <li>{@code .entity(TravelPlan.class, spec -> spec.validateSchema())} — StructuredOutputValidationAdvisor가 자동 등록된다.
 *       응답을 JSON 스키마로 검증하고, 틀리면 "Output JSON validation failed because of: …"를 사용자 메시지 뒤에 붙여 다시 부른다.
 *       기본 재시도 3회.</li>
 *   <li>재시도 횟수를 바꾸려면 어드바이저를 직접 등록한다 (예제 6.16).</li>
 * </ul>
 * 제약: 응답이 완전히 조립돼야 검증할 수 있으므로 {@code .stream()}에서는 UnsupportedOperationException.
 *
 * <p>소스로 확인한 점(2.0.1): 재시도를 다 써도 실패하면 어드바이저는 예외를 던지지 않고 마지막 응답을 그대로 돌려준다.
 * 그 뒤 entity 변환에서 파싱 예외가 난다. 즉 '교정 루프'는 성공을 보장하지 않는다 — 실패 처리는 여전히 호출자 몫이다.
 */
@Component
@ConditionalOnProperty(prefix = "spring.ai.cli", name = "step", havingValue = "ch6-validate")
public class Ch6Step6_StructuredOutputValidation implements CommandLineRunner {

    public record TravelPlan(String destination, int days, List<String> dailyActivities) {
    }

    private final ChatClient.Builder builder;

    public Ch6Step6_StructuredOutputValidation(ChatClient.Builder builder) {
        this.builder = builder;
    }

    @Override
    public void run(String... args) {
        // 예제 6.15 그대로: 프롬프트로 스키마를 알려 주고 + 검증·재시도
        try {
            TravelPlan plan = builder.build().prompt()
                    .user("제주도로 3일짜리 여행 일정을 짜줘")
                    .call()
                    .entity(TravelPlan.class, spec -> spec.validateSchema());
            System.out.println("── 예제 6.15 validateSchema()\n  " + plan);
        }
        catch (RuntimeException e) {
            // 실측(qwen3.5:4b): 모델이 일정 대신 '스키마 자체'($schema, type, properties …)를 4번 연속 돌려줬다.
            // 검증 오류를 붙여 다시 물어도 같은 실수를 반복 → 재시도 소진 → 파싱 예외.
            System.out.println("── 예제 6.15 validateSchema() → 재시도 소진 후 실패: " + e.getClass().getSimpleName());
        }

        // 보완: 제공자 네이티브 구조화 출력(Ollama format=스키마)으로 '생성 단계'에서 형식을 강제하고, 검증은 안전망으로 둔다
        TravelPlan plan = builder.build().prompt()
                .user("제주도로 3일짜리 여행 일정을 짜줘")
                .call()
                .entity(TravelPlan.class, spec -> spec.useProviderStructuredOutput().validateSchema());
        System.out.println("── 네이티브 구조화 출력 + validateSchema()\n  " + plan);

        // 예제 6.16: 재시도 횟수를 직접 지정 (여기도 네이티브와 함께)
        var validationAdvisor = StructuredOutputValidationAdvisor.builder()
                .outputType(TravelPlan.class)
                .maxRepeatAttempts(5)
                .build();
        TravelPlan plan2 = builder.clone().defaultAdvisors(validationAdvisor).build().prompt()
                .user("부산 2일 여행 일정을 짜줘")
                .call()
                .entity(TravelPlan.class, spec -> spec.useProviderStructuredOutput());
        System.out.println("── 직접 등록 (maxRepeatAttempts=5)\n  " + plan2);
    }
}
