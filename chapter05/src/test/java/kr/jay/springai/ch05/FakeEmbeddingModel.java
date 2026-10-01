package kr.jay.springai.ch05;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

/**
 * 테스트용 가짜 임베딩 모델 — '단어가 들어 있으면 그 칸을 1로' 하는 아주 단순한 벡터.
 *
 * <p>진짜 모델처럼 의미를 이해하지는 못하지만, 결과를 사람이 예측할 수 있어서
 * 벡터 저장소의 동작(유사도 정렬, 필터, 임계값)을 모델 없이 검증하기에 충분하다.
 * 무엇을 임베딩했는지(inputs)도 기록한다.
 */
public class FakeEmbeddingModel implements EmbeddingModel {

    static final List<String> VOCAB = List.of("장애", "보고", "보안", "자전거", "출퇴근", "스프링", "마스킹", "산악", "category");

    final List<String> inputs = new ArrayList<>();

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<Embedding> results = new ArrayList<>();
        for (int i = 0; i < request.getInstructions().size(); i++) {
            String text = request.getInstructions().get(i);
            inputs.add(text);
            results.add(new Embedding(vector(text), i));
        }
        return new EmbeddingResponse(results);
    }

    @Override
    public float[] embed(Document document) {
        return embed(document.getText());
    }

    static float[] vector(String text) {
        float[] v = new float[VOCAB.size() + 1];
        for (int i = 0; i < VOCAB.size(); i++) {
            v[i] = text.contains(VOCAB.get(i)) ? 1f : 0f;
        }
        v[VOCAB.size()] = 0.1f;          // 0벡터 방지 (코사인 계산에서 0으로 나누지 않게)
        return v;
    }
}
