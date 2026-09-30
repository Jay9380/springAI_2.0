package kr.jay.springai.ch03.demo;

import java.util.List;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * [3.5] 임베딩 — 글을 숫자 배열로 바꾸고, 그 거리를 직접 계산해 본다.
 *
 * <p>실행: {@code --spring.ai.cli.step=ch3-demo-embedding}
 *
 * <p>임베딩은 '의미가 비슷한 글은 가까운 벡터'가 되도록 학습된 모델의 출력이다.
 * 가까움은 보통 코사인 유사도로 잰다:
 * <pre>
 *   cos(a, b) = (a · b) / (|a| × |b|)      1에 가까울수록 같은 방향(비슷한 의미)
 * </pre>
 * 벡터 검색은 결국 '질문 벡터와 코사인이 큰 문서 벡터 찾기'다.
 */
@Component
@ConditionalOnProperty(prefix = "spring.ai.cli", name = "step", havingValue = "ch3-demo-embedding")
public class EmbeddingDemo implements CommandLineRunner {

    private final EmbeddingModel embeddingModel;

    public EmbeddingDemo(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    @Override
    public void run(String... args) {
        List<String> texts = List.of(
                "긴급 장애가 발생하면 30분 안에 보고한다",        // A
                "서비스가 멈추면 즉시 상황을 알린다",             // B: A와 단어는 다르지만 뜻이 비슷
                "출퇴근용 가벼운 도시형 자전거",                  // C: 전혀 다른 주제
                "The outage must be reported within 30 minutes"); // D: A의 영어 번역

        long start = System.currentTimeMillis();
        List<float[]> vectors = embeddingModel.embed(texts);
        long elapsed = System.currentTimeMillis() - start;

        System.out.printf("모델 차원 수: %d, 문장 %d개 임베딩 %dms (문장당 약 %dms)%n",
                vectors.get(0).length, texts.size(), elapsed, elapsed / texts.size());
        System.out.print("A의 벡터 앞 5개 값: ");
        for (int i = 0; i < 5; i++) {
            System.out.printf("%.4f ", vectors.get(0)[i]);
        }
        System.out.println("…\n");

        String[] names = {"A 장애 보고", "B 서비스 멈춤", "C 자전거", "D 영어 번역"};
        System.out.println("코사인 유사도 (직접 계산)");
        for (int i = 1; i < vectors.size(); i++) {
            System.out.printf("  A ↔ %-12s : %.4f%n", names[i], cosine(vectors.get(0), vectors.get(i)));
        }
        // 예상: B와 D가 C보다 훨씬 높다. 단어가 겹치지 않아도(B), 언어가 달라도(D) 뜻이 가까우면 가깝다.
    }

    /** 코사인 유사도 — SimpleVectorStore가 내부에서 하는 계산과 같다 */
    public static double cosine(float[] a, float[] b) {
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }
}
