package kr.jay.springai.ch06.support;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * 콘솔 대화 루프 도우미.
 *
 * <p>책의 단계별 러너들은 모두 "입력 받기 → AI 호출 → 출력 → /exit이면 종료"를 반복한다.
 * 그 반복 부분만 여기로 빼고, 각 단계 클래스는 'AI를 어떻게 부르는가'에만 집중하게 했다.
 *
 * <p>6장에서 바뀐 점: 표준 입력을 읽는 리더를 <b>하나만</b> 두고 공유한다({@link #readLine}).
 * 6.4의 사람 승인 핸들러도 콘솔에서 y/n을 읽어야 하는데, 각자 Scanner를 만들면 먼저 만든 쪽이 입력을 버퍼에
 * 미리 읽어 가서 다른 쪽은 입력을 받지 못한다(파이프로 입력을 넣을 때 실제로 겪는 문제).
 */
public final class ChatConsole {

    private static final BufferedReader IN = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));

    private ChatConsole() {
    }

    /** 한 줄 읽기. 입력이 끝났으면 null. 여러 스레드(승인 핸들러)에서 불려도 한 줄씩 순서대로 나눠 준다. */
    public static synchronized String readLine() {
        try {
            return IN.readLine();
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * @param title  시작할 때 보여 줄 제목 (예: "[Ch6] Step2: 수동 에이전트 루프")
     * @param onInput 한 줄 입력을 받아 AI를 호출하고 출력까지 하는 코드
     */
    public static void run(String title, Consumer<String> onInput) {
        System.out.println("=== " + title + " ===");
        System.out.println("종료하려면 /exit 입력\n");
        while (true) {
            System.out.print("> ");
            String line = readLine();
            if (line == null) {
                break;                                  // 입력 스트림이 끝나면(파이프 입력 등) 종료
            }
            String input = line.trim();
            if (input.isEmpty()) {
                continue;
            }
            if (input.equalsIgnoreCase("/exit") || input.equalsIgnoreCase("/quit")) {
                break;
            }
            System.out.print("AI: ");
            onInput.accept(input);
            System.out.println("\n");
        }
    }
}
