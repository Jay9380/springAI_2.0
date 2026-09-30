package kr.jay.springai.ch03.etl;

import java.util.List;
import java.util.regex.Pattern;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentTransformer;
import org.springframework.stereotype.Component;

/**
 * [3.3.5] 커스텀 DocumentTransformer — 개인정보(이메일·휴대전화) 마스킹.
 *
 * <p>DocumentTransformer는 {@code Function<List<Document>, List<Document>>}이다. apply() 하나만 구현한다.
 * 문서 목록을 받아 '고친 문서 목록'을 돌려준다. 원본은 건드리지 않고 새 Document를 만든다(함수형).
 *
 * <p><b>반드시 청킹보다 먼저</b> 실행한다(3.3.6). 먼저 자르면 "010-1234-5678"이
 * "010-12" / "34-5678"로 쪼개져 정규식이 못 잡는다. (테스트에 이 실패 상황을 재현해 두었다)
 */
@Component
public class PiiMaskingTransformer implements DocumentTransformer {

    private static final Pattern EMAIL = Pattern.compile("[a-zA-Z0-9._-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
    private static final Pattern PHONE = Pattern.compile("01[0-9]-\\d{3,4}-\\d{4}");

    @Override
    public List<Document> apply(List<Document> documents) {
        return documents.stream()
                .map(d -> new Document(d.getId(), mask(d.getText()), d.getMetadata()))   // id·메타데이터 유지
                .toList();
    }

    static String mask(String text) {
        if (text == null) {
            return null;
        }
        String masked = EMAIL.matcher(text).replaceAll("[MASKED_EMAIL]");
        return PHONE.matcher(masked).replaceAll("[MASKED_PHONE]");
    }
}
