package kr.jay.springai.ch06;

import static kr.jay.springai.ch06.ScriptedModel.text;
import static kr.jay.springai.ch06.ScriptedModel.toolCall;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import kr.jay.springai.ch06.agent.BasicSpringAIAgent;
import kr.jay.springai.ch06.advisor.WarehouseTools;
import kr.jay.springai.ch06.toolsearch.DecoyTools;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.toolsearch.ToolSearchToolCallingAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.toolsearch.ToolReference;
import org.springframework.ai.tool.toolsearch.ToolSearchRequest;
import org.springframework.ai.tool.toolsearch.index.lucene.LuceneToolIndex;
import org.springframework.ai.tool.toolsearch.index.regex.RegexToolIndex;

/** 6.4.3 기본 에이전트(메모리 루프 안 + 내부 기록 끔)와 6.4.5 동적 툴 탐색. */
class AgentAndToolSearchTest {

    /** Ch6Step8_ToolSearch.allTools()는 패키지 전용이라 같은 구성을 여기서 다시 만든다. */
    static final class Ch6Step8Tools {
        static List<ToolCallback> all() {
            List<ToolCallback> all = new java.util.ArrayList<>(List.of(ToolCallbacks.from(new WarehouseTools())));
            all.addAll(DecoyTools.all());
            return all;
        }
    }

    @Test
    void basicAgent_memoryInsideLoop_storesEachToolResultOnce() {
        ScriptedModel model = new ScriptedModel((n, p) -> switch (n) {
            case 1 -> toolCall("getStock", "{\"sku\":\"SKU-100\"}");
            case 2 -> toolCall("getStock", "{\"sku\":\"SKU-300\"}");
            default -> text("합계 59개");
        });
        var agent = new BasicSpringAIAgent(ChatClient.builder(model), null);     // MCP 없이도 동작

        assertThat(agent.run("합계?", "c1")).isEqualTo("합계 59개");
        assertThat(agent.memoryTypes("c1"))
                .containsExactly("USER", "ASSISTANT", "TOOL", "ASSISTANT", "TOOL", "ASSISTANT");
        // 내부 기록을 끄지 않았다면 세 번째 호출에서 툴 결과가 메모리 것 + 내부 것으로 두 번씩 보인다
        assertThat(model.prompts.get(2).getInstructions())
                .filteredOn(m -> m.getMessageType() == MessageType.TOOL).hasSize(2);
    }

    static List<String> toolNames(Prompt p) {
        return ((ToolCallingChatOptions) p.getOptions()).getToolCallbacks().stream()
                .map(t -> t.getToolDefinition().name()).toList();
    }

    static ChatClient toolSearchClient(ScriptedModel model) {
        return ChatClient.builder(model)
                .defaultToolCallbacks(Ch6Step8Tools.all())
                .defaultAdvisors(ToolSearchToolCallingAdvisor.builder().toolIndex(new RegexToolIndex()).maxResults(5).build())
                .build();
    }

    @Test
    void toolSearch_firstCallSeesOnlySearchTool_thenOnlyFoundTools() {
        ScriptedModel model = new ScriptedModel((n, p) -> switch (n) {
            case 1 -> toolCall("toolSearchTool", "{\"query\":\"getStock\"}");
            case 2 -> toolCall("getStock", "{\"sku\":\"SKU-100\"}");
            default -> text("12개");
        });
        String answer = toolSearchClient(model).prompt().user("SKU-100 재고?")
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "s1")).call().content();

        assertThat(answer).isEqualTo("12개");
        assertThat(toolNames(model.prompts.get(0))).containsExactly("toolSearchTool");          // 28개 대신 1개
        assertThat(toolNames(model.prompts.get(1))).contains("toolSearchTool", "getStock")
                .doesNotContain("sendSlackMessage", "getWeather")
                .hasSizeLessThanOrEqualTo(1 + 5);                                                  // 검색 결과 상한(maxResults)
    }

    @Test
    void toolSearch_withoutConversationId_fails() {
        ScriptedModel model = new ScriptedModel((n, p) -> text("x"));
        assertThatThrownBy(() -> toolSearchClient(model).prompt().user("q").call().content())
                .hasMessageContaining("chat_memory_conversation_id");
    }

    static List<ToolReference> refs(List<ToolCallback> tools) {
        return tools.stream().map(t -> ToolReference.builder()
                .toolName(t.getToolDefinition().name()).summary(t.getToolDefinition().description()).build()).toList();
    }

    @Test
    void luceneIndex_isKeywordMatch_soQueryLanguageMustMatchDescriptions() {
        // 실측: 모델이 영어로 검색("inventory check SKU 100 stock quantity")하자 한국어 설명의 getStock을 못 찾았다
        var index = new LuceneToolIndex(0.4f);
        index.indexTools("s1", refs(Ch6Step8Tools.all()));                         // 실행과 같은 28개

        assertThat(search(index, "inventory stock quantity")).isEmpty();          // 영어 검색 → 못 찾음
        assertThat(search(index, "재고 수량 조회")).contains("getStock");             // 한국어 검색 → 찾음
        assertThat(search(index, "SKU")).isEmpty();                                // 'SKU의'처럼 조사가 붙은 토큰은 'SKU'와 다르다
    }

    @Test
    void luceneThreshold_isAbsoluteBm25Score_soFewToolsScoreTooLow() {
        // 처음 쓴 테스트는 툴 1개만 색인했다가 한국어 검색도 실패했다. BM25 점수는 색인된 문서 수(IDF)에 따라 달라지므로
        // 같은 질의·같은 임계값(0.4)이라도 툴이 적으면 점수가 임계값 아래로 떨어진다.
        var single = new LuceneToolIndex(0.4f);
        single.indexTools("s1", refs(Ch6Step8Tools.all().stream()
                .filter(t -> t.getToolDefinition().name().equals("getStock")).toList()));      // getStock 하나
        assertThat(search(single, "재고 수량 조회")).isEmpty();
    }

    static List<String> search(LuceneToolIndex index, String query) {
        return index.search(new ToolSearchRequest("s1", query, 5, null)).toolReferences().stream()
                .map(ToolReference::toolName).toList();
    }
}
