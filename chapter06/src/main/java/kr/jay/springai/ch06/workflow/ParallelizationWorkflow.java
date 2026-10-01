package kr.jay.springai.ch06.workflow;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.ai.chat.client.ChatClient;

/**
 * [6.1.2 ③ 예제 6.3] 병렬화 — 독립적인 호출을 동시에 하고 합친다.
 *
 * <ul>
 *   <li>분할(sectioning): 서로 독립인 하위 작업을 나눠 동시에 (예: 이해관계자별 영향 분석) → {@link #parallel}</li>
 *   <li>투표(voting): 같은 작업을 여러 번 돌려 다수결로 신뢰도를 높임 (예: 취약점 검사 3회 중 2회 이상) → {@link #vote}</li>
 * </ul>
 * 핵심은 "하위 작업 목록을 <b>개발자가 미리 정한다</b>"는 점이다. 이것이 오케스트레이터-워커와의 차이다.
 */
public class ParallelizationWorkflow {

    private final ChatClient chatClient;

    public ParallelizationWorkflow(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /** 같은 지시를 입력마다 동시에 적용한다. 결과 순서는 입력 순서와 같다. */
    public List<String> parallel(String prompt, List<String> inputs, int nWorkers) {
        // 가상 스레드도 되지만, 책처럼 '동시 호출 수 상한'을 명시하는 고정 풀을 쓴다 (로컬 모델·API 요금 한도 보호)
        try (ExecutorService executor = Executors.newFixedThreadPool(nWorkers)) {
            List<CompletableFuture<String>> futures = inputs.stream()
                    .map(input -> CompletableFuture.supplyAsync(
                            () -> chatClient.prompt(prompt + "\n입력: " + input).call().content(), executor))
                    .toList();
            return futures.stream().map(CompletableFuture::join).toList();
        }
    }

    /**
     * 투표: 같은 질문을 n번 묻고 "YES"가 과반이면 true.
     * 한 번의 판정은 흔들릴 수 있지만 여러 번의 다수결은 덜 흔들린다 (대가: 호출 n배).
     */
    public boolean vote(String yesNoQuestion, int n) {
        List<String> answers = parallel(yesNoQuestion + "\nYES 또는 NO 한 단어로만 답하세요.",
                java.util.Collections.nCopies(n, ""), n);
        long yes = answers.stream().filter(a -> a != null && a.trim().toUpperCase().startsWith("YES")).count();
        return yes * 2 > n;
    }
}
