package kr.jay.springai.ch06.hitl;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;
import org.springframework.ai.mcp.annotation.context.StructuredElicitResult;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * [6.4.4] 운영 MCP 서버의 툴 — 조회는 바로, 되돌리기 어려운 작업은 <b>실행 직전에 사람 승인</b>을 받는다.
 *
 * <p>흐름: LLM이 restart_service 호출 → 서버가 {@code ctx.elicit(...)}로 클라이언트에 승인 요청을 보내고 '일시 정지'
 * → 클라이언트의 {@code @McpElicitation} 핸들러가 사람에게 묻고 응답 → ACCEPT면 실행, 아니면 중단.
 *
 * <p>승인 요청이 '실제 작업 바로 앞'에서 일어난다는 게 핵심이다. 모델이 무엇을 생각하든 이 줄을 지나야 실행된다.
 *
 * <p><b>승인 창구가 없는 클라이언트(fail-closed):</b> 클라이언트가 Elicitation을 지원하지 않으면 실행하지 않는다.
 * "물어볼 수 없으면 진행"이 아니라 "물어볼 수 없으면 거부". 이 검사를 지우고 돌려 보니 라이브러리도
 * {@code ctx.elicit}에서 "Elicitation not supported by the client: …" 예외로 막았다 — 즉 안전은 이미 지켜진다.
 * 이 검사가 하는 일은 그 예외 대신 <b>모델이 이해하고 사용자에게 전할 수 있는 안내문</b>을 돌려주는 것이다.
 */
@Component
@Profile("ops-server")
public class OpsMcpTools {

    /** 사람이 승인 폼에 채울 내용. 이 record로 JSON 스키마(requestedSchema)가 만들어져 클라이언트에 전달된다. */
    public record RestartApproval(boolean confirm, String reason) {
    }

    private final Map<String, Integer> restarts = new ConcurrentHashMap<>();

    @McpTool(name = "get_service_status", description = "운영 서비스의 상태와 재시작 횟수를 조회합니다. 읽기 전용입니다.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    public String getServiceStatus(@McpToolParam(description = "서비스 이름 (예: payment-api)") String service) {
        return service + ": RUNNING (재시작 " + restarts.getOrDefault(service, 0) + "회)";
    }

    @McpTool(name = "restart_service", description = "운영 서비스를 재시작합니다. 실행 전에 운영자 승인이 필요합니다.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = true))
    public String restartService(McpSyncRequestContext ctx,
                                 @McpToolParam(description = "재시작할 서비스 이름") String service) {
        if (!ctx.elicitEnabled()) {
            return "거부: 이 클라이언트는 승인 요청(Elicitation)을 지원하지 않아 재시작할 수 없습니다.";
        }
        StructuredElicitResult<RestartApproval> answer = ctx.elicit(
                spec -> spec.message("'" + service + "' 서비스를 재시작하려 합니다. 진행할까요? (확인 여부와 사유)"),
                RestartApproval.class);

        if (answer.action() != ElicitResult.Action.ACCEPT) {
            return "중단: 운영자가 재시작을 승인하지 않았습니다 (" + answer.action() + ").";
        }
        if (answer.structuredContent() == null || !answer.structuredContent().confirm()) {
            return "중단: 승인 폼에서 확인(confirm)이 체크되지 않았습니다.";
        }
        int count = restarts.merge(service, 1, Integer::sum);
        return service + " 재시작 완료 (누적 " + count + "회, 사유: " + answer.structuredContent().reason() + ")";
    }

    int restartCount(String service) {
        return restarts.getOrDefault(service, 0);
    }
}
