package kr.jay.springai.ch06.context;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.core.io.ClassPathResource;

/**
 * [6.2.4] 파일 읽기 툴 — "secrets는 절대 읽지 않는다"를 <b>코드</b>로 집행한다.
 *
 * <p>AGENTS.md에도 같은 규칙이 텍스트로 있다. 하지만 텍스트는 모델이 '참고'할 뿐이다(확률적).
 * 진짜로 막으려면 툴이 그 경로를 거부해야 한다(결정론적). 둘 다 두는 이유:
 * 텍스트 규칙은 모델이 애초에 시도하지 않게 하고(불필요한 호출 절약), 코드 규칙은 시도해도 막는다(보장).
 *
 * <p>거부할 때는 예외 대신 <b>이유와 대안을 담은 실패 메시지</b>를 돌려준다. 책의 "실패 기록을 컨텍스트에 남겨라" —
 * 모델이 그 메시지를 보고 같은 시도를 반복하지 않는다.
 */
public class ProjectFileTools {

    private static final String ROOT = "project/";

    @Tool(description = "프로젝트 문서 파일을 읽습니다. 경로는 프로젝트 루트 기준 상대 경로입니다 (예: README.md).")
    public String readProjectFile(@ToolParam(description = "읽을 파일의 상대 경로") String path) {
        String normalized = path.replace('\\', '/').strip();
        if (normalized.contains("..") || normalized.startsWith("/")) {
            return "거부: 프로젝트 루트 밖의 경로는 읽을 수 없습니다.";
        }
        // 경로 조각 단위로, 대소문자 무시: macOS 파일 시스템은 대소문자를 구분하지 않아 "Secrets/db.properties"도 열린다
        for (String segment : normalized.split("/")) {
            if (segment.equalsIgnoreCase("secrets")) {
                return "거부: secrets 아래 파일은 정책상 읽을 수 없습니다. 이 정보가 필요하면 담당자에게 요청하도록 안내하세요.";
            }
        }
        ClassPathResource resource = new ClassPathResource(ROOT + normalized);
        if (!resource.exists()) {
            return "실패: " + normalized + " 파일이 없습니다. 사용 가능한 파일: README.md, AGENTS.md";
        }
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        }
        catch (IOException e) {
            return "실패: 파일을 읽지 못했습니다 (" + e.getMessage() + ")";
        }
    }
}
