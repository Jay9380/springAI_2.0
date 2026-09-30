package kr.jay.springai.ch02;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import kr.jay.springai.ch02.support.ChatConsole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** 콘솔 루프가 입력을 올바르게 넘기고, /exit에서 멈추는지 확인한다. */
class ChatConsoleTest {

    private final InputStream originalIn = System.in;

    @AfterEach
    void restore() {
        System.setIn(originalIn);
    }

    private List<String> feed(String typed) {
        System.setIn(new ByteArrayInputStream(typed.getBytes(StandardCharsets.UTF_8)));
        List<String> received = new ArrayList<>();
        ChatConsole.run("test", received::add);
        return received;
    }

    @Test
    void passesEachLineToHandler() {
        assertThat(feed("안녕\n자바 21\n/exit\n")).containsExactly("안녕", "자바 21");
    }

    @Test
    void stopsAtExit_andIgnoresBlankLines() {
        // /exit 뒤의 줄은 처리되면 안 된다 (실패 경로)
        assertThat(feed("\n   \n첫 질문\n/EXIT\n무시돼야 함\n")).containsExactly("첫 질문");
    }

    @Test
    void endsQuietly_whenInputRunsOut() {
        // /exit 없이 입력이 끝나도(파이프 입력) 예외 없이 끝난다
        assertThat(feed("하나\n")).containsExactly("하나");
    }
}
