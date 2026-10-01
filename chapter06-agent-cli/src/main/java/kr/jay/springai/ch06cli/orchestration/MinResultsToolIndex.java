package kr.jay.springai.ch06cli.orchestration;

import java.util.List;

import org.springframework.ai.tool.toolsearch.ToolIndex;
import org.springframework.ai.tool.toolsearch.ToolReference;
import org.springframework.ai.tool.toolsearch.ToolSearchRequest;
import org.springframework.ai.tool.toolsearch.ToolSearchResponse;

/**
 * [6.6.3 실측 보정] 툴 검색 결과 수의 하한을 코드로 보장하는 ToolIndex 래퍼.
 *
 * <p>toolSearchTool에는 모델이 채우는 {@code maxResults} 인자가 있고, 모델이 값을 주면 어드바이저 설정(5)보다 우선한다
 * (ToolSearchTool 2.0.1: {@code maxResults != null ? maxResults : advisorMaxResults}). 실측에서 qwen3.5:4b가 {@code maxResults:1}로
 * 검색해 '예약' 툴을 끝내 찾지 못했고, "다른 표현으로 다시 검색하라"는 지시도 따르지 않은 채 "예약 툴이 없다"고 답했다.
 * 모델이 너무 작게 요청해도 최소 {@code floor}개는 돌려주도록 집행 영역에서 막는다.
 */
public class MinResultsToolIndex implements ToolIndex {

    private final ToolIndex delegate;
    private final int floor;

    public MinResultsToolIndex(ToolIndex delegate, int floor) {
        this.delegate = delegate;
        this.floor = floor;
    }

    @Override
    public ToolSearchResponse search(ToolSearchRequest request) {
        Integer requested = request.maxResults();
        int effective = requested == null ? floor : Math.max(requested, floor);
        return delegate.search(new ToolSearchRequest(request.sessionId(), request.query(), effective, request.categoryFilter()));
    }

    @Override
    public void indexTool(String sessionId, ToolReference toolReference) {
        delegate.indexTool(sessionId, toolReference);
    }

    @Override
    public void indexTools(String sessionId, List<ToolReference> toolReferences) {
        delegate.indexTools(sessionId, toolReferences);
    }

    @Override
    public void clearIndex(String sessionId) {
        delegate.clearIndex(sessionId);
    }
}
