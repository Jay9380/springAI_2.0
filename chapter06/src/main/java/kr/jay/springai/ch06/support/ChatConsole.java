package kr.jay.springai.ch06.support;

import java.util.Scanner;
import java.util.function.Consumer;

/**
 * 콘솔 대화 루프 도우미.
 *
 * <p>책의 단계별 러너들은 모두 "입력 받기 → AI 호출 → 출력 → /exit이면 종료"를 반복한다.
 * 그 반복 부분만 여기로 빼고, 각 단계 클래스는 'AI를 어떻게 부르는가'에만 집중하게 했다.
 */
public final class ChatConsole {

    private ChatConsole() {
    }

    /**
     * @param title  시작할 때 보여 줄 제목 (예: "[Ch6] Step1: 기본 스트리밍 채팅")
     * @param onInput 한 줄 입력을 받아 AI를 호출하고 출력까지 하는 코드
     */
    public static void run(String title, Consumer<String> onInput) {
        System.out.println("=== " + title + " ===");
        System.out.println("종료하려면 /exit 입력\n");

        // try-with-resources: 루프가 끝나면 Scanner를 닫는다
        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                System.out.print("> ");
                if (!scanner.hasNextLine()) {
                    break;                              // 입력 스트림이 끝나면(파이프 입력 등) 종료
                }
                String input = scanner.nextLine().trim();
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
}
