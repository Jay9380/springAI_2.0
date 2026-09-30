package kr.jay.springai.ch02.demo;

import java.util.Arrays;
import java.util.List;

import org.springframework.ai.converter.AbstractConversionServiceOutputConverter;
import org.springframework.core.convert.support.DefaultConversionService;

/**
 * [2.6.4 예제 2.52] 나만의 변환기 — 파이프(|)로 구분된 목록을 List&lt;String&gt;으로.
 *
 * <p>모든 변환기는 두 가지 일을 한다 (StructuredOutputConverter = FormatProvider + Converter).
 * <ol>
 *   <li>getFormat(): 모델에게 '이런 형식으로 답해 달라'는 지시문을 만든다 → 사용자 메시지 뒤에 붙는다</li>
 *   <li>convert(text): 모델이 준 글을 원하는 자바 타입으로 바꾼다</li>
 * </ol>
 * 쉼표가 내용 안에 섞일 수 있는 데이터(예: "부산, 항구 도시")는 쉼표 구분이 깨지므로 파이프가 안전하다.
 */
public class PipeDelimitedListConverter extends AbstractConversionServiceOutputConverter<List<String>> {

    public PipeDelimitedListConverter() {
        super(new DefaultConversionService());
    }

    @Override
    public String getFormat() {
        return "Your response should be a list of values separated by a pipe character (|). "
                + "Do not number the items and do not add any other text.";
    }

    @Override
    public List<String> convert(String source) {
        // "A|B|C" → ["A", "B", "C"]. 앞뒤 공백과 빈 항목은 버린다.
        return Arrays.stream(source.split("\\|"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
