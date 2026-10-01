package kr.jay.springai.ch06.step;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import kr.jay.springai.ch06.advisor.ProbeAdvisor;
import kr.jay.springai.ch06.advisor.WarehouseTools;
import kr.jay.springai.ch06.toolsearch.DecoyTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.client.advisor.toolsearch.ToolSearchToolCallingAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.toolsearch.ToolIndex;
import org.springframework.ai.tool.toolsearch.index.lucene.LuceneToolIndex;
import org.springframework.ai.tool.toolsearch.index.regex.RegexToolIndex;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * [Step 8 · 6.4.5 예제 6.20] 동적 툴 탐색 — "툴을 위한 RAG".
 *
 * <p>실행: {@code --spring.ai.cli.step=ch6-tool-search}  (대화형 아님)
 *
 * <p>같은 질문을 세 방식으로 돌려 입력 토큰과 LLM 호출 수를 비교한다.
 * <ol>
 *   <li>전부 노출: 툴 28개(관련 3 + 무관 25)의 정의가 매 호출마다 실린다</li>
 *   <li>툴 검색(Lucene 키워드): 처음엔 toolSearchTool 하나만 → 모델이 검색 → 찾은 툴만 다음 호출에 추가</li>
 *   <li>툴 검색(Regex): 같은 방식, 색인만 정규식</li>
 * </ol>
 * 대화 ID가 필수다: 검색해서 찾은 툴을 '세션(대화) 단위로' 누적 관리하기 때문 (없으면 예외).
 */
@Component
@ConditionalOnProperty(prefix = "spring.ai.cli", name = "step", havingValue = "ch6-tool-search")
public class Ch6Step8_ToolSearch implements CommandLineRunner {

    static final String QUESTION = "SKU-100 상품의 현재 재고 수량을 알려줘";

    private final ChatClient.Builder builder;

    public Ch6Step8_ToolSearch(ChatClient.Builder builder) {
        this.builder = builder;
    }

    static List<ToolCallback> allTools() {
        var tools = new java.util.ArrayList<ToolCallback>(Arrays.asList(ToolCallbacks.from(new WarehouseTools())));
        tools.addAll(DecoyTools.all());
        return tools;
    }

    @Override
    public void run(String... args) {
        System.out.println("질문: " + QUESTION + " / 등록된 툴 " + allTools().size() + "개\n");
        report("① 전부 노출", null);
        report("② 툴 검색 (Lucene)", new LuceneToolIndex(0.4f));
        report("③ 툴 검색 (Regex)", new RegexToolIndex());
    }

    private void report(String label, ToolIndex index) {
        ProbeAdvisor modelCalls = new ProbeAdvisor("model-calls", BaseAdvisor.HIGHEST_PRECEDENCE + 900);   // 루프 안 → LLM 호출 수
        ChatClient.Builder b = builder.clone().defaultToolCallbacks(allTools());
        if (index == null) {
            b.defaultAdvisors(modelCalls);
        }
        else {
            b.defaultAdvisors(ToolSearchToolCallingAdvisor.builder().toolIndex(index).maxResults(5).build(), modelCalls);
        }
        ChatResponse response = b.build().prompt().user(QUESTION)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, UUID.randomUUID().toString()))
                .call().chatResponse();
        Usage usage = response.getMetadata().getUsage();          // 루프 전체 누적 사용량 (UsageAccumulator)
        System.out.printf("%s%n  툴 호출: %s%n  답: %s%n  LLM 호출 %d회, 입력 토큰 %d, 전체 토큰 %d%n%n", label,
                modelCalls.toolCalls(), response.getResult().getOutput().getText().replace("\n", " "),
                modelCalls.calls(), usage.getPromptTokens(), usage.getTotalTokens());
    }
}
