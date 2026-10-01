package kr.jay.springai.ch06cli;

import static kr.jay.springai.ch06cli.ScriptedModel.text;
import static kr.jay.springai.ch06cli.ScriptedModel.toolCall;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpRequest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import kr.jay.springai.ch06cli.capability.local.LocalTools;
import kr.jay.springai.ch06cli.capability.remote.KnowledgeApiKeyCustomizer;
import kr.jay.springai.ch06cli.orchestration.OrchestrationToolCallingAdvisor;
import kr.jay.springai.ch06cli.orchestration.SafeToolCallingAdvisor;
import kr.jay.springai.ch06cli.orchestration.SpringAIAgent;
import kr.jay.springai.ch06cli.orchestration.ToolCallTraceAdvisor;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.tool.toolsearch.index.vectorstore.VectorToolIndex;
import org.springframework.ai.vectorstore.SimpleVectorStore;

/** T2 오케스트레이션: 안전 가드, 메모리 위치, 툴 검색 어드바이저의 수명, 메타 툴 레이어, T3 연결별 인증. */
class OrchestrationTest {

    static SpringAIAgent agent(ScriptedModel model, ToolCallingAdvisor loop, ChatMemory memory, List<String> trace) {
        return new SpringAIAgent(ChatClient.builder(model), "system", Arrays.asList(ToolCallbacks.from(new LocalTools())),
                loop, memory, new SimpleMeterRegistry(), new ToolCallTraceAdvisor(trace::add));
    }

    @Test
    void safety_modelThatNeverStops_endsWithReadableMessage_notException() {
        // 매번 다른 인자(반복 감지에 안 걸리게)로 끝없이 툴을 부르는 모델
        ScriptedModel model = new ScriptedModel((n, p) -> toolCall("product_lookup", "{\"sku\":\"SKU-" + n + "\"}"));
        String answer = agent(model, new SafeToolCallingAdvisor(3), MessageWindowChatMemory.builder().build(), new ArrayList<>())
                .run("무한", "c1");

        assertThat(answer).contains("3라운드를 넘어 작업을 중단");                 // 툴 요청 응답 대신 사람이 읽을 안내문
        assertThat(model.prompts).hasSize(4);                                     // 툴 실행 3라운드 + 4번째 응답에서 중단
    }

    @Test
    void safety_identicalRepeatedCall_stopsEarly_withoutWaitingForRoundLimit() {
        // 실측: 4B 모델이 같은 TodoWrite를 11번 반복하며 입력 토큰 8.8만 개를 썼다
        ScriptedModel model = new ScriptedModel((n, p) -> toolCall("product_lookup", "{\"sku\":\"SKU-100\"}"));
        String answer = agent(model, new SafeToolCallingAdvisor(10), MessageWindowChatMemory.builder().build(), new ArrayList<>())
                .run("반복", "c1");

        assertThat(answer).contains("3번 연속 호출해 작업을 중단");
        assertThat(model.prompts).hasSize(3);                                     // 10라운드가 아니라 3번째에서
    }

    @Test
    void safety_sameToolWithDifferentArgs_isNotARepeat() {
        ScriptedModel model = new ScriptedModel((n, p) -> n <= 3
                ? toolCall("product_lookup", "{\"sku\":\"SKU-" + n + "00\"}") : text("세 개 다 봤습니다"));
        assertThat(agent(model, new SafeToolCallingAdvisor(10), MessageWindowChatMemory.builder().build(), new ArrayList<>())
                .run("세 SKU 조회", "c1")).isEqualTo("세 개 다 봤습니다");
    }

    @Test
    void safety_counterIsPerRequest_evenWithOneSharedAdvisor() {
        // 어드바이저 하나를 공유해도 두 번째 요청이 첫 요청의 카운트를 이어받지 않는다 (책은 요청마다 어드바이저를 새로 만들어 해결)
        var shared = new SafeToolCallingAdvisor(2);
        for (int i = 0; i < 2; i++) {
            ScriptedModel model = new ScriptedModel((n, p) -> n <= 2 ? toolCall("product_lookup", "{\"sku\":\"SKU-100\"}") : text("완료"));
            assertThat(agent(model, shared, MessageWindowChatMemory.builder().build(), new ArrayList<>()).run("두 번 조회", "c" + i))
                    .isEqualTo("완료");
        }
    }

    @Test
    void memoryOutsideLoop_keepsOnlyQuestionAndAnswer_andSecondTurnSeesFirst() {
        ChatMemory memory = MessageWindowChatMemory.builder().build();
        List<String> trace = new ArrayList<>();
        ScriptedModel model = new ScriptedModel((n, p) -> switch (n) {
            case 1 -> toolCall("product_reserve", "{\"sku\":\"SKU-200\",\"quantity\":2}");
            case 2 -> text("SKU-200 자전거 전조등 2개 예약했습니다.");
            default -> text(p.contains("자전거 전조등") ? "자전거 전조등이었습니다." : "모르겠습니다.");
        });
        var agent = agent(model, new SafeToolCallingAdvisor(5), memory, trace);

        agent.run("SKU-200 2개 예약해줘", "c1");
        assertThat(agent.run("방금 예약한 상품 이름이 뭐였지?", "c1")).isEqualTo("자전거 전조등이었습니다.");   // 툴 없이 메모리로
        assertThat(memory.get("c1")).extracting(m -> m.getMessageType())
                .containsExactly(MessageType.USER, MessageType.ASSISTANT, MessageType.USER, MessageType.ASSISTANT);
        assertThat(trace).anyMatch(t -> t.startsWith("  [툴 호출] product_reserve"))
                .anyMatch(t -> t.startsWith("  [툴 결과] product_reserve") && t.contains("남은 재고 5개"));
    }

    static List<ToolCallback> domain() {
        return Arrays.asList(ToolCallbacks.from(new LocalTools()));
    }

    static ToolCallback meta(String name) {
        return FunctionToolCallback.builder(name, (String s) -> "ok").description(name + " 메타 툴").inputType(String.class).build();
    }

    @Test
    void toolSearchAdvisor_shouldBeShared_newOnePerRequestReEmbedsAllTools() {
        // 책 방식(요청마다 새 어드바이저)이면 '이미 색인함' 표시가 매번 비어 모든 툴을 다시 임베딩한다
        CountingEmbeddingModel shared = new CountingEmbeddingModel();
        var index = new VectorToolIndex(SimpleVectorStore.builder(shared).build());
        var advisor = new OrchestrationToolCallingAdvisor(index, List.of(), 5, 5);
        ask(advisor);
        int afterFirst = shared.embeddedTexts.get();
        ask(advisor);
        assertThat(afterFirst).isEqualTo(7);                                       // 첫 요청에 툴 7개 색인
        assertThat(shared.embeddedTexts.get() - afterFirst).isZero();              // 공유 어드바이저: 두 번째 요청은 재색인 없음

        CountingEmbeddingModel perRequest = new CountingEmbeddingModel();
        var index2 = new VectorToolIndex(SimpleVectorStore.builder(perRequest).build());
        ask(new OrchestrationToolCallingAdvisor(index2, List.of(), 5, 5));
        int afterFirst2 = perRequest.embeddedTexts.get();
        ask(new OrchestrationToolCallingAdvisor(index2, List.of(), 5, 5));
        assertThat(perRequest.embeddedTexts.get() - afterFirst2).isEqualTo(7);     // 요청마다 새 어드바이저: 7개를 다시 임베딩
    }

    static void ask(OrchestrationToolCallingAdvisor advisor) {
        ChatClient.builder(new ScriptedModel((n, p) -> text("ok"))).defaultToolCallbacks(domain()).defaultAdvisors(advisor).build()
                .prompt().user("q").advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "c1")).call().content();
    }

    @Test
    void metaToolLayer_firstRoundSeesSearchPlusMetaTools_butNoDomainTools() {
        List<ToolCallback> metaTools = List.of(meta("Skill"), meta("TodoWrite"));
        List<ToolCallback> all = new ArrayList<>(domain());
        all.addAll(metaTools);
        ScriptedModel model = new ScriptedModel((n, p) -> text("ok"));
        var index = new VectorToolIndex(SimpleVectorStore.builder(new CountingEmbeddingModel()).build());
        ChatClient.builder(model).defaultToolCallbacks(all)
                .defaultAdvisors(new OrchestrationToolCallingAdvisor(index, metaTools, 5, 5)).build()
                .prompt().system("sys").user("재발주 절차대로 해줘").advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "c1")).call().content();

        var sent = (ToolCallingChatOptions) model.prompts.getFirst().getOptions();
        assertThat(sent.getToolCallbacks()).extracting(t -> t.getToolDefinition().name())
                .containsExactlyInAnyOrder("toolSearchTool", "Skill", "TodoWrite");      // 도메인 툴 7개는 검색해야 보인다
        assertThat(model.prompts.getFirst().getSystemMessage().getText())
                .contains("검색하지 말고 필요하면 바로 호출하세요: Skill, TodoWrite");
    }

    @Test
    void localTools_reserve_successAndFailurePaths() {
        var tools = new LocalTools();
        assertThat(tools.productReserve("sku-200", 2)).contains("예약 완료").contains("남은 재고 5개");
        assertThat(tools.productReserve("SKU-200", 6)).startsWith("실패: 재고 부족");
        assertThat(tools.productReserve("SKU-200", 0)).startsWith("실패");
        assertThat(tools.productReserve("SKU-999", 1)).startsWith("실패: 없는 SKU");
        assertThat(tools.productLookup("SKU-200")).contains("매장 재고 5개");             // 실패한 예약은 재고를 바꾸지 않았다
        assertThat(tools.divide(new java.math.BigDecimal("1"), java.math.BigDecimal.ZERO)).startsWith("실패");
    }

    @Test
    void knowledgeKey_isSentOnlyToKnowledgeServer() {
        var customizer = new KnowledgeApiKeyCustomizer("http://localhost:8085", "secret");
        HttpRequest toKnowledge = build(customizer, "http://localhost:8085/mcp");
        HttpRequest toOps = build(customizer, "http://localhost:8086/mcp");
        HttpRequest otherHost = build(customizer, "http://evil.example:8085/mcp");

        assertThat(toKnowledge.headers().firstValue("X-API-Key")).contains("secret");
        assertThat(toOps.headers().firstValue("X-API-Key")).isEmpty();                     // 운영 서버로는 새지 않는다
        assertThat(otherHost.headers().firstValue("X-API-Key")).isEmpty();
    }

    static HttpRequest build(KnowledgeApiKeyCustomizer c, String url) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url));
        c.customize(b, "POST", URI.create(url), null, null);
        return b.build();
    }

    @Test
    void minResultsIndex_ignoresModelAskingForTooFew() {
        var index = new kr.jay.springai.ch06cli.orchestration.MinResultsToolIndex(
                new VectorToolIndex(SimpleVectorStore.builder(new CountingEmbeddingModel()).build()), 3);
        index.indexTools("s1", domain().stream().map(t -> org.springframework.ai.tool.toolsearch.ToolReference.builder()
                .toolName(t.getToolDefinition().name()).summary(t.getToolDefinition().description()).build()).toList());

        var few = index.search(new org.springframework.ai.tool.toolsearch.ToolSearchRequest("s1", "예약", 1, null));
        var many = index.search(new org.springframework.ai.tool.toolsearch.ToolSearchRequest("s1", "예약", 6, null));
        assertThat(few.toolReferences()).hasSize(3);                               // 모델이 1개만 달라고 해도 3개
        assertThat(many.toolReferences()).hasSize(6);                              // 더 많이 달라는 건 그대로
    }
}
