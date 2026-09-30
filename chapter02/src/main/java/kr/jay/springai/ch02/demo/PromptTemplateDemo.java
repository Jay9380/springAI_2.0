package kr.jay.springai.ch02.demo;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.template.st.StTemplateRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * [2.4.1 ~ 2.4.4] Prompt, Message, PromptTemplate — 프롬프트를 '객체'로 다루기.
 *
 * <p>실행: {@code --spring.ai.cli.step=ch2-demo-template}
 *
 * <p>왜 문자열 더하기(+) 대신 템플릿인가?
 * <ul>
 *   <li>가독성: 프롬프트 구조와 데이터가 분리된다</li>
 *   <li>안전성: 사용자 입력이 '지시문'의 자리를 침범하는 프롬프트 인젝션을 줄인다</li>
 *   <li>관리: 긴 프롬프트를 .st 파일로 빼서 코드 수정 없이 다듬을 수 있다</li>
 * </ul>
 * 템플릿 엔진은 StringTemplate(ST)이고, 기본 변수 구분자는 중괄호 {변수}다.
 */
@Component
@ConditionalOnProperty(prefix = "spring.ai.cli", name = "step", havingValue = "ch2-demo-template")
public class PromptTemplateDemo implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(PromptTemplateDemo.class);

    private final ChatModel chatModel;
    private final ChatClient chatClient;

    /** [2.4.4 예제 2.26] 외부 리소스 주입. classpath:, file:, https: 모두 같은 코드로 읽힌다. */
    @Value("classpath:/prompts/code-refactoring.st")
    private Resource refactoringPrompt;

    public PromptTemplateDemo(ChatModel chatModel, ChatClient.Builder builder) {
        this.chatModel = chatModel;
        this.chatClient = builder.build();
    }

    @Override
    public void run(String... args) {
        renderOnly();
        systemAndUserTemplates();
        customDelimiterForJson();
        externalResourceTemplate();
        inlineParamsWithChatClient();
    }

    /** [예제 2.17~2.19] 렌더링만 해서 결과 문자열 확인 — 모델 호출 전 디버깅에 유용. */
    private void renderOnly() {
        PromptTemplate template = new PromptTemplate("Tell me a {adjective} joke about {topic}.");
        String rendered = template.render(Map.of("adjective", "funny", "topic", "Java Developers"));
        log.info("── ① render(): {}", rendered);
    }

    /** [예제 2.21] system 템플릿과 user 템플릿을 따로 만들어 하나의 Prompt로 합치기. */
    private void systemAndUserTemplates() {
        // SystemPromptTemplate → role=system 메시지, PromptTemplate → 기본 role=user 메시지
        Message sys = new SystemPromptTemplate("당신은 {role} 전문가입니다. 두 문장으로 답합니다.")
                .createMessage(Map.of("role", "Java"));
        Message user = new PromptTemplate("{topic}의 최신 트렌드를 알려줘.")
                .createMessage(Map.of("topic", "Spring Boot"));

        Prompt prompt = new Prompt(List.of(sys, user));
        log.info("── ② 메시지 타입: {}",
                prompt.getInstructions().stream().map(m -> m.getMessageType().getValue()).toList());
        log.info("답변: {}", chatModel.call(prompt).getResult().getOutput().getText());
    }

    /**
     * [예제 2.24] JSON 예시를 넣고 싶을 때의 함정.
     * 기본 구분자가 {}라서 {"albums": [...]} 같은 JSON 예시를 넣으면 ST가 변수로 착각해 오류가 난다.
     * 구분자를 &lt; &gt;로 바꾸면 중괄호는 그대로 글자로 남는다.
     */
    private void customDelimiterForJson() {
        PromptTemplate template = PromptTemplate.builder()
                .renderer(StTemplateRenderer.builder()
                        .startDelimiterToken('<')
                        .endDelimiterToken('>')
                        .build())
                .template("""
                        <artist>의 앨범 목록을 아래 JSON 형식으로 반환하세요.
                        Example: { "albums": ["Album A", "Album B"] }
                        """)
                .build();
        log.info("── ③ 사용자 정의 구분자 렌더링:\n{}", template.render(Map.of("artist", "Beatles")));
    }

    /** [예제 2.25~2.26] .st 파일에서 템플릿을 읽어 모델 호출까지. */
    private void externalResourceTemplate() {
        PromptTemplate template = new PromptTemplate(refactoringPrompt);
        Prompt prompt = template.create(Map.of(
                "language", "Java",
                "code_snippet", "public int sum(List<Integer> l){int s=0;for(int i=0;i<l.size();i++)s+=l.get(i);return s;}"));
        log.info("── ④ .st 파일 템플릿 결과:\n{}", chatModel.call(prompt).getResult().getOutput().getText());
    }

    /** ChatClient에서는 user(u -> u.text(...).param(...))로 같은 일을 한 줄에 한다. */
    private void inlineParamsWithChatClient() {
        String answer = chatClient.prompt()
                .user(u -> u.text("{lang}로 '{word}'를 번역하고 발음을 알려줘.")
                        .param("lang", "일본어")
                        .param("word", "감사합니다"))
                .call()
                .content();
        log.info("── ⑤ ChatClient param 바인딩: {}", answer);
    }
}
