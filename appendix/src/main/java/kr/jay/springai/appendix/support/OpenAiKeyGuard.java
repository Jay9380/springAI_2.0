package kr.jay.springai.appendix.support;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * [부록 A 실측 보정] openai 프로파일에서 API 키가 비어 있으면 기동을 멈춘다.
 *
 * <p>OpenAI 자동 구성은 키 '속성이 있는지'만 보고, 값이 비었거나 자리표시자 글자 그대로여도 빈을 만든다.
 * 그러면 앱은 정상 기동한 것처럼 보이다가 첫 호출에서 401을 받는다(그것도 잘못된 키를 실제로 전송한 뒤에).
 * 설정 오류는 기동 시점에, 네트워크 호출 전에 드러나야 한다.
 */
@Component
@Profile("openai")
public class OpenAiKeyGuard {

    public OpenAiKeyGuard(@Value("${spring.ai.openai.api-key:}") String apiKey) {
        if (apiKey.isBlank() || apiKey.startsWith("${")) {
            throw new IllegalStateException("OPENAI_API_KEY 환경 변수가 비어 있습니다. export OPENAI_API_KEY=... 후 다시 실행하세요.");
        }
    }
}
