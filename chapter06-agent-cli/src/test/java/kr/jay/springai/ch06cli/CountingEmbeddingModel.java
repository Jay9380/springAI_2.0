package kr.jay.springai.ch06cli;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

/** 몇 개의 텍스트를 임베딩했는지 세는 가짜 임베딩 모델. 벡터는 글자 코드 합으로 만든 의미 없는 값(검색 품질은 보지 않음). */
public class CountingEmbeddingModel implements EmbeddingModel {

    public final AtomicInteger embeddedTexts = new AtomicInteger();

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<Embedding> results = new ArrayList<>();
        for (int i = 0; i < request.getInstructions().size(); i++) {
            embeddedTexts.incrementAndGet();
            results.add(new Embedding(vector(request.getInstructions().get(i)), i));
        }
        return new EmbeddingResponse(results);
    }

    /**
     * 기본 구현은 호출될 때마다 "Test String"을 임베딩해 차원을 잰다. SimpleVectorStore는 add·delete·search마다
     * 관측 컨텍스트를 만들며 dimensions()를 부르므로, 캐시하지 않는 모델은 저장소 작업마다 임베딩이 1번씩 더 나간다.
     * (실제 OllamaEmbeddingModel은 AbstractEmbeddingModel이 캐시한다.) 세기 정확하게 고정값을 돌려준다.
     */
    @Override
    public int dimensions() {
        return 4;
    }

    @Override
    public float[] embed(Document document) {
        return embed(document.getText());
    }

    static float[] vector(String text) {
        float[] v = new float[4];
        for (int i = 0; i < text.length(); i++) {
            v[i % 4] += text.charAt(i) % 7;
        }
        v[3] += 1f;
        return v;
    }
}
