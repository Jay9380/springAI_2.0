package kr.jay.springai.ch03.etl;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.transformer.splitter.TextSplitter;

/**
 * [3.3.5 예제] 한글에 직관적인 '글자 수' 기준 분할기.
 *
 * <p>TokenTextSplitter는 OpenAI 토크나이저(CL100K) 기준 '토큰'으로 센다. 한국어는 글자당 토큰 수가 들쭉날쭉해서
 * "몇 글자씩 잘릴지" 감이 잘 안 온다. 글자 수로 자르면 예측이 쉽다 (대신 문장 중간에서 잘릴 수 있다).
 *
 * <p>TextSplitter를 상속하면 splitText() 하나만 구현하면 된다. 부모가
 * 조각마다 새 Document를 만들고 chunk_index·parent_document_id 같은 메타데이터를 붙여 준다.
 */
public class SimpleLengthSplitter extends TextSplitter {

    private final int chunkLength;

    public SimpleLengthSplitter(int chunkLength) {
        this.chunkLength = chunkLength;
    }

    @Override
    protected List<String> splitText(String text) {
        List<String> chunks = new ArrayList<>();
        for (int i = 0; i < text.length(); i += chunkLength) {
            chunks.add(text.substring(i, Math.min(text.length(), i + chunkLength)));
        }
        return chunks;
    }
}
