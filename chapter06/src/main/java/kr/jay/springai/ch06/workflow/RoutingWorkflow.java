package kr.jay.springai.ch06.workflow;

import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;

/**
 * [6.1.2 ② 예제 6.2] 라우팅 — 먼저 분류하고, 전문 프롬프트로 보낸다.
 *
 * <p>두 번 부른다: (1) 분류 호출 — routes의 키 중 하나를 고르게 함, (2) 고른 키의 전문 프롬프트로 답변 호출.
 * 실무 가치는 두 가지다. 전문 프롬프트를 짧게 유지할 수 있고, 경로마다 다른 모델(싼 모델/비싼 모델)을 쓸 수 있다.
 *
 * <p>책은 분류 결과를 구조화 출력(reasoning + selection)으로 받는다. 여기서는 4B 로컬 모델에서도 안정적이도록
 * "키 하나만 출력" 방식으로 받고, <b>모델이 엉뚱한 키를 내면 코드가 기본 경로로 보낸다.</b>
 * 분류 결과를 믿지 않고 검증하는 것 — 이것이 6.2의 '집행 영역'이다.
 */
public class RoutingWorkflow {

    public record RouteResult(String route, String answer) {
    }

    private final ChatClient chatClient;

    public RoutingWorkflow(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public RouteResult route(String input, Map<String, String> routes, String fallbackRoute) {
        String selected = classify(input, routes);
        if (!routes.containsKey(selected)) {
            selected = fallbackRoute;                                  // 모르는 키 → 기본 경로
        }
        String answer = chatClient.prompt()
                .system(routes.get(selected))                          // 고른 경로의 전문 프롬프트
                .user(input)
                .call()
                .content();
        return new RouteResult(selected, answer);
    }

    String classify(String input, Map<String, String> routes) {
        String text = chatClient.prompt()
                .user("""
                        다음 문의를 분류하세요. 후보: %s
                        후보 중 하나의 키만 소문자로 출력하고 다른 말은 쓰지 마세요.

                        문의: %s
                        """.formatted(routes.keySet(), input))
                .call()
                .content();
        return text == null ? "" : text.trim().toLowerCase().replaceAll("[^a-z_]", "");
    }
}
