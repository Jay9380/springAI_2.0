package kr.jay.springai.ch06cli.orchestration.meta;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import kr.jay.springai.ch06cli.channel.ChatConsole;
import org.springaicommunity.agent.tools.AskUserQuestionTool.Question;
import org.springaicommunity.agent.tools.AskUserQuestionTool.Question.Option;
import org.springaicommunity.agent.tools.AskUserQuestionTool.QuestionHandler;

/**
 * [6.5.2 예제 6.24] AskUserQuestionTool의 질문을 콘솔로 사람에게 전달하는 핸들러.
 *
 * <p>라이브러리의 CommandLineQuestionHandler를 쓰지 않은 이유: 그 클래스는 질문마다 {@code new Scanner(System.in)}를 만든다.
 * 대화 루프(ChatConsole)와 표준 입력을 나눠 써야 하는데 Scanner가 입력을 미리 버퍼에 가져가면 다음 질문을 못 읽는다.
 * 그래서 같은 동작(번호 또는 직접 입력, 쉼표로 복수 선택)을 공유 리더 위에 다시 만들었다.
 *
 * <p>반환 규칙(라이브러리가 검증한다): 키는 질문 문장 그대로, 값은 null이면 안 된다(빈 문자열은 허용).
 */
public class ConsoleQuestionHandler implements QuestionHandler {

    private final Supplier<String> input;

    public ConsoleQuestionHandler() {
        this(ChatConsole::readLine);
    }

    public ConsoleQuestionHandler(Supplier<String> input) {
        this.input = input;
    }

    @Override
    public Map<String, String> handle(List<Question> questions) {
        Map<String, String> answers = new LinkedHashMap<>();
        for (Question q : questions) {
            System.out.println("\n[질문 · " + q.header() + "] " + q.question());
            for (int i = 0; i < q.options().size(); i++) {
                Option o = q.options().get(i);
                System.out.printf("  %d. %s — %s%n", i + 1, o.label(), o.description());
            }
            System.out.print(Boolean.TRUE.equals(q.multiSelect()) ? "  번호를 쉼표로, 또는 직접 입력 > " : "  번호 또는 직접 입력 > ");
            String line = input.get();
            answers.put(q.question(), line == null ? "" : toAnswer(line.trim(), q.options()));
        }
        return answers;
    }

    /** "2" → 두 번째 선택지 라벨, "1,3" → 라벨 두 개, 숫자가 아니면 입력 그대로. */
    static String toAnswer(String line, List<Option> options) {
        List<String> labels = new ArrayList<>();
        for (String part : line.split(",")) {
            try {
                int index = Integer.parseInt(part.trim()) - 1;
                if (index < 0 || index >= options.size()) {
                    return line;                       // 범위 밖 번호는 직접 입력으로 취급
                }
                labels.add(options.get(index).label());
            }
            catch (NumberFormatException e) {
                return line;
            }
        }
        return String.join(", ", labels);
    }
}
