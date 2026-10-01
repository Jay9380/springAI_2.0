package kr.jay.springai.ch06;

import static kr.jay.springai.ch06.ScriptedModel.text;
import static kr.jay.springai.ch06.ScriptedModel.toolCall;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import kr.jay.springai.ch06.advisor.AgentThinking;
import kr.jay.springai.ch06.advisor.ProbeAdvisor;
import kr.jay.springai.ch06.advisor.SafeGuardToolCallingAdvisor;
import kr.jay.springai.ch06.advisor.SafeGuardToolCallingAdvisor.LoopGuardException;
import kr.jay.springai.ch06.advisor.ToolLoopMetricsAdvisor;
import kr.jay.springai.ch06.advisor.WarehouseTools;
import kr.jay.springai.ch06.step.Ch6Step6_StructuredOutputValidation.TravelPlan;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.augment.AugmentedArgumentEvent;
import org.springframework.ai.tool.augment.AugmentedToolCallbackProvider;

/** 6.3 재귀 어드바이저: 체인의 세 구역, 안전 가드 훅, 파라미터 증강, 구조화 출력 교정. */
class RecursiveAdvisorTest {

    /** 툴 두 번(SKU-100, SKU-300) 부르고 답하는 모델. 모델 호출은 총 3번. */
    static ScriptedModel twoToolRounds() {
        return new ScriptedModel((n, p) -> switch (n) {
            case 1 -> toolCall("getStock", "{\"sku\":\"SKU-100\"}");
            case 2 -> toolCall("getStock", "{\"sku\":\"SKU-300\"}");
            default -> text("합계 59개");
        });
    }

    @Test
    void advisorsBelowLoopRunOnce_aboveLoopRunEveryIteration() {
        ScriptedModel model = twoToolRounds();
        ChatMemory memory = MessageWindowChatMemory.builder().build();
        var outer = new ProbeAdvisor("outer", BaseAdvisor.HIGHEST_PRECEDENCE + 250);
        var inner = new ProbeAdvisor("inner", BaseAdvisor.HIGHEST_PRECEDENCE + 500);
        ChatClient client = ChatClient.builder(model)
                .defaultTools(new WarehouseTools())
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(memory).order(BaseAdvisor.HIGHEST_PRECEDENCE + 200).build(),
                        outer, new SafeGuardToolCallingAdvisor(5, 100_000, 600), inner)
                .build();

        client.prompt().user("합계?").advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "c1")).call().content();

        assertThat(outer.calls()).isEqualTo(1);
        assertThat(inner.calls()).isEqualTo(3);
        // 루프 밖 메모리(+200): 툴 왕복은 저장하지 않고 최종 질문·답만 — 질문이 3번 중복 저장되는 버그도 없다
        assertThat(memory.get("c1")).extracting(m -> m.getMessageType())
                .containsExactly(MessageType.USER, MessageType.ASSISTANT);
    }

    @Test
    void memoryInsideLoop_storesToolTranscript_andAutoAdvisorTurnsOffInternalHistory() {
        ScriptedModel model = twoToolRounds();
        ChatMemory memory = MessageWindowChatMemory.builder().build();
        ChatClient client = ChatClient.builder(model)
                .defaultTools(new WarehouseTools())
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(memory).order(BaseAdvisor.HIGHEST_PRECEDENCE + 400).build())
                .build();                                                   // ToolCallingAdvisor는 자동 등록(+300)

        client.prompt().user("합계?").advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "c1")).call().content();

        assertThat(memory.get("c1")).extracting(m -> m.getMessageType()).contains(MessageType.TOOL);
        // 내부 기록이 꺼졌으므로 세 번째 호출에서 각 툴 결과가 '한 번씩만' 보인다 (켜져 있었다면 중복)
        long toolMessagesInLastPrompt = model.prompts.get(2).getInstructions().stream()
                .filter(m -> m.getMessageType() == MessageType.TOOL).count();
        assertThat(toolMessagesInLastPrompt).isEqualTo(2);
    }

    @Test
    void safeGuard_stopsModelThatNeverStops() {
        ScriptedModel model = new ScriptedModel((n, p) -> toolCall("getStock", "{\"sku\":\"SKU-100\"}"));
        ChatClient client = ChatClient.builder(model).defaultTools(new WarehouseTools())
                .defaultAdvisors(new SafeGuardToolCallingAdvisor(3, 100_000, 600)).build();

        assertThatThrownBy(() -> client.prompt().user("x").call().content())
                .isInstanceOf(LoopGuardException.class).hasMessageContaining("3회");
        assertThat(model.prompts).hasSize(3);
    }

    @Test
    void safeGuard_stateIsPerRequest_notShared() {
        // 상태를 필드에 두었다면 두 번째 요청은 이전 카운트(3)를 이어받아 바로 막힌다
        var guard = new SafeGuardToolCallingAdvisor(3, 100_000, 600);
        for (int i = 0; i < 2; i++) {
            ChatClient client = ChatClient.builder(twoToolRounds()).defaultTools(new WarehouseTools())
                    .defaultAdvisors(guard).build();
            assertThat(client.prompt().user("합계?").call().content()).isEqualTo("합계 59개");
        }
    }

    @Test
    void safeGuard_tokenBudget_stopsExpensiveLoop() {
        AtomicInteger n = new AtomicInteger();
        ChatModel costly = new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                n.incrementAndGet();
                return new ChatResponse(List.of(new Generation(toolCall("getStock", "{\"sku\":\"SKU-100\"}"))),
                        ChatResponseMetadata.builder().usage(new DefaultUsage(300, 100)).build());   // 호출당 400토큰
            }

            @Override
            public ChatOptions getOptions() {
                return ToolCallingChatOptions.builder().build();
            }
        };
        ChatClient client = ChatClient.builder(costly).defaultTools(new WarehouseTools())
                .defaultAdvisors(new SafeGuardToolCallingAdvisor(10, 1000, 600)).build();

        assertThatThrownBy(() -> client.prompt().user("x").call().content())
                .isInstanceOf(LoopGuardException.class).hasMessageContaining("토큰 예산");
        assertThat(n.get()).isEqualTo(3);                                   // 400, 800, 1200(초과) → 3번째에서 중단
    }

    @Test
    void safeGuard_trimsLongToolResults_includingEarlierRounds() {
        ScriptedModel model = new ScriptedModel((n, p) -> switch (n) {
            case 1 -> toolCall("getAuditLog", "{\"sku\":\"SKU-100\"}");
            case 2 -> toolCall("getAuditLog", "{\"sku\":\"SKU-300\"}");
            default -> text("요약");
        });
        ChatClient client = ChatClient.builder(model).defaultTools(new WarehouseTools())
                .defaultAdvisors(new SafeGuardToolCallingAdvisor(5, 100_000, 600)).build();
        client.prompt().user("로그 요약").call().content();

        List<String> resultsSeenAtThirdCall = model.prompts.get(2).getInstructions().stream()
                .filter(m -> m instanceof ToolResponseMessage)
                .flatMap(m -> ((ToolResponseMessage) m).getResponses().stream())
                .map(ToolResponseMessage.ToolResponse::responseData).toList();
        assertThat(resultsSeenAtThirdCall).hasSize(2)
                .allMatch(d -> d.length() < 700 && d.contains("자 생략"));    // 1회차 결과도 원래 길이(약 4천 자)로 되살아나지 않음
    }

    @Test
    void countTool_givesExactAggregate() {
        // 감사 로그 200줄 중 i % 3 == 0 인 66줄이 출고
        assertThat(new WarehouseTools().countAuditEvents("SKU-100", "출고")).startsWith("SKU-100 출고: 66건");
    }

    @Test
    void metricsAdvisor_countsIterationsAndToolCalls() {
        var registry = new SimpleMeterRegistry();
        ChatClient client = ChatClient.builder(twoToolRounds()).defaultTools(new WarehouseTools())
                .defaultAdvisors(new SafeGuardToolCallingAdvisor(5, 100_000, 600), new ToolLoopMetricsAdvisor(registry)).build();
        client.prompt().user("합계?").call().content();

        assertThat(registry.counter("agent.tool.loop.iterations").count()).isEqualTo(3);
        assertThat(registry.counter("agent.tool.calls", "tool", "getStock").count()).isEqualTo(2);
    }

    @Test
    void augmentedArguments_reachConsumer_butNotTheBusinessTool() {
        List<AugmentedArgumentEvent<AgentThinking>> events = new ArrayList<>();
        var provider = AugmentedToolCallbackProvider.<AgentThinking>builder()
                .toolObject(new WarehouseTools()).argumentType(AgentThinking.class)
                .argumentConsumer(events::add).removeExtraArgumentsAfterProcessing(true).build();
        ScriptedModel model = new ScriptedModel((n, p) -> n == 1
                ? toolCall("getStock", "{\"sku\":\"SKU-200\",\"innerThought\":\"재고 여부는 툴로 확인해야 함\",\"confidence\":\"high\"}")
                : text("품절"));
        ChatClient.builder(model).defaultToolCallbacks(provider).build().prompt().user("SKU-200 있어?").call().content();

        assertThat(events).singleElement().satisfies(e -> {
            assertThat(e.toolDefinition().name()).isEqualTo("getStock");
            assertThat(e.arguments().innerThought()).isEqualTo("재고 여부는 툴로 확인해야 함");
        });
        // 모델이 본 스키마에는 innerThought가 더해져 있다
        var sent = (ToolCallingChatOptions) model.prompts.getFirst().getOptions();
        assertThat(sent.getToolCallbacks().getFirst().getToolDefinition().inputSchema()).contains("innerThought");
        // 원래 툴은 정상 실행됐다 (SKU-200 재고 0)
        var toolResult = (ToolResponseMessage) model.prompts.get(1).getInstructions().getLast();
        assertThat(toolResult.getResponses().getFirst().responseData()).isEqualTo("0");
    }

    @Test
    void validateSchema_feedsErrorBackAndRetries() {
        ScriptedModel model = new ScriptedModel((n, p) -> n == 1
                ? new AssistantMessage("{\"destination\":\"제주\",\"days\":\"사흘\"}")        // days 타입 틀림
                : new AssistantMessage("{\"destination\":\"제주\",\"days\":3,\"dailyActivities\":[\"오름\",\"바다\",\"시장\"]}"));
        TravelPlan plan = ChatClient.create(model).prompt().user("제주 3일").call()
                .entity(TravelPlan.class, spec -> spec.validateSchema());

        assertThat(plan.days()).isEqualTo(3);
        assertThat(model.prompts).hasSize(2);
        assertThat(model.prompts.get(1).getUserMessage().getText()).contains("Output JSON validation failed");
    }

    @Test
    void validateSchema_givesUpAfterRetries_andCallerStillGetsAnError() {
        ScriptedModel model = new ScriptedModel((n, p) -> new AssistantMessage("일정을 못 짜겠어요"));
        assertThatThrownBy(() -> ChatClient.create(model).prompt().user("제주 3일").call()
                .entity(TravelPlan.class, spec -> spec.validateSchema()))
                .isInstanceOf(RuntimeException.class);
        assertThat(model.prompts).hasSize(1 + 3);                           // 첫 시도 + 기본 재시도 3회
    }
}
