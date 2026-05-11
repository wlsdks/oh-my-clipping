package com.ohmyclipping.admin.mcp

import com.ohmyclipping.error.InvalidInputException
import com.ohmyclipping.error.NotFoundException
import com.ohmyclipping.mcp.McpRateLimiter
import com.ohmyclipping.mcp.mcpToolCall
import com.ohmyclipping.model.UserClippingRequest
import com.ohmyclipping.service.CategoryService
import com.ohmyclipping.service.UserClippingRequestService
import com.ohmyclipping.service.dto.admin.ApproveClippingRequestCommand
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Admin 도구 — PENDING 상태 사용자 구독 신청을 승인 처리한다.
 *
 * ⚠️ **파괴적 액션**: 승인과 동시에 Category/Persona/Source 리소스가 DB 에 생성되고
 * 사용자에게 DM 알림이 나간다. 되돌리려면 별도 관리자 작업이 필요하다.
 *
 * LLM 오조작을 막기 위해 두 단계 확인을 강제한다:
 *  1. LLM 은 `admin_list_pending_requests` 로 대상 request 를 반드시 한 번 조회한다.
 *  2. 그 결과의 `requestName / 카테고리 / 사용자명` 을
 *     `confirmationSummary = "{requestName} → {categoryName or sourceName} (사용자: {username})"` 형태로 그대로 echo 해야 한다.
 *
 * echo 가 불일치하면 [InvalidInputException] 으로 거부하고 재조회를 요구한다.
 * 실제 승인은 [UserClippingRequestService.approveRequest] 에 위임한다.
 *
 * reviewer 는 [clipping.mcp.reviewer-username] 설정값(기본 "mcp-service")으로 고정된다.
 * 해당 계정은 ADMIN 권한이어야 하며, 없으면 서비스 레이어에서 권한 에러가 발생한다.
 */
@Component
class AdminApprovePendingRequestTool(
    private val userClippingRequestService: UserClippingRequestService,
    private val categoryService: CategoryService,
    private val rateLimiter: McpRateLimiter,
    @Value("\${clipping.mcp.reviewer-username:mcp-service}") private val reviewerUsername: String,
) {

    @Tool(
        description = """
            PENDING 상태의 사용자 구독 신청을 승인한다.
            **⚠️ 파괴적 액션:** 승인 즉시 Category/Persona/Source 가 생성되고 사용자에게 DM 이 발송된다. 되돌릴 수 없다.
            **언제 쓰나:** 운영자가 특정 요청을 명시적으로 승인하기로 확정했을 때 (사람의 결재를 대신하는 용도가 아님).
            **쓰지 말 것:** 반려해야 할 때 / PENDING 이 아닌 요청 / 사용자가 아직 확인 중일 때.
            **2단계 확인:** 반드시 admin_list_pending_requests 로 대상을 먼저 조회한 뒤, 아래 echo 문구가 일치해야 실행된다.
              confirmationSummary = "{requestName} → {categoryName} (사용자: {username})"
              (카테고리가 아직 생성 전이면 "{requestName} → {sourceName} (사용자: {username})" 도 허용)
            **파라미터:**
              - requestId (필수): 승인할 UserClippingRequest ID.
              - confirmationSummary (필수): 위 echo 문구.
              - approveNote (선택): 200자 이내 내부 검토 메모.
            **결정 규칙:** echo 불일치 → 재조회 후 재시도. 서비스 레이어가 PENDING 외 상태를 거부한다.
            **반환:** 승인된 UserClippingRequest.
        """,
    )
    fun admin_approve_pending_request(
        @ToolParam(description = "승인할 UserClippingRequest ID") requestId: String,
        @ToolParam(
            description = "'{requestName} → {categoryName or sourceName} (사용자: {username})' 형태의 echo 확인 문구",
        ) confirmationSummary: String,
        @ToolParam(description = "내부 검토 메모 (선택, 200자 이내)", required = false) approveNote: String?,
    ): String = mcpToolCall {
        // 빈도 제한: 30회/시간. 승인은 외부 부작용이 크므로 스팸 호출을 차단한다.
        rateLimiter.checkOrThrow("admin_approve_pending_request", maxRequests = 30, windowSeconds = 3600)

        // requestId 공백 차단.
        if (requestId.isBlank()) {
            throw InvalidInputException("requestId is required")
        }
        if (confirmationSummary.isBlank()) {
            throw InvalidInputException(
                "confirmationSummary is required — admin_list_pending_requests 로 대상 확인 후 echo 하세요",
            )
        }

        // 대상 요청을 조회해 echo 일치 여부를 서비스 호출 전에 먼저 검증한다.
        val request = userClippingRequestService.findRequestById(requestId)
            ?: throw NotFoundException("Request not found: $requestId")
        if (!request.isPendingReview()) {
            throw InvalidInputException(
                "승인 가능한 상태가 아닙니다 (현재 status=${request.status}) — PENDING 만 승인할 수 있습니다",
            )
        }

        // 기대 echo 문구 — 카테고리명 / 소스명 / 사용자명으로 조립한 뒤 whitespace 비교.
        val username = userClippingRequestService.findRequesterUsername(request.requesterUserId)
            ?: request.requesterUserId
        val expectedSummaries = buildExpectedSummaries(request, username)
        val normalized = normalize(confirmationSummary)
        if (expectedSummaries.none { normalize(it) == normalized }) {
            throw InvalidInputException(
                "확인 요약 불일치 — admin_list_pending_requests 로 재확인 후 재시도 (기대: \"${expectedSummaries.first()}\")",
            )
        }

        // 실제 승인 위임 — 서비스에서 ADMIN 권한, PENDING 상태, 중복 승인 등을 재검증한다.
        val command = ApproveClippingRequestCommand(
            // RSS/RSSHub 요청을 안전하게 승인하려면 원문이 라이선스된 상태여야 한다 — 승인 후 추가 법률 검토는
            // 관리자 UI 에서 수행하되, MCP 경로에서는 가장 제한적인 QUOTATION_ONLY 로 생성한다.
            legalBasis = "QUOTATION_ONLY",
            summaryAllowed = true,
            fulltextAllowed = false,
            reviewNotes = approveNote?.takeIf { it.isNotBlank() },
            overrideSlackChannelId = null,
        )
        userClippingRequestService.approveRequest(
            requestId = requestId,
            reviewerUsername = reviewerUsername,
            command = command,
        )
    }

    /**
     * 대상 요청으로부터 허용 가능한 echo 문구 후보를 만든다.
     * - 카테고리가 승인 과정에서 생성되는 경우: "{requestName} → {sourceName} (사용자: {username})"
     * - 기존 카테고리에 추가되는 경우: "{requestName} → {categoryName} (사용자: {username})"
     *   (카테고리명은 categoryService 로 조회)
     */
    private fun buildExpectedSummaries(
        request: UserClippingRequest,
        username: String,
    ): List<String> {
        val base = "${request.requestName} → "
        val trailing = " (사용자: $username)"
        val candidates = mutableListOf<String>()
        // 기존 카테고리가 이미 매핑된 경우 카테고리명을 우선.
        request.approvedCategoryId?.let { catId ->
            categoryService.findById(catId)?.name?.let { candidates += "$base$it$trailing" }
        }
        // 신청서의 sourceName 기반 fallback.
        candidates += "$base${request.sourceName}$trailing"
        // requestName 을 카테고리명처럼 사용하는 경우도 흔함.
        candidates += "$base${request.requestName}$trailing"
        return candidates.distinct()
    }

    /** echo 비교 시 공백/전각 공백 차이에 관대하게 만든다. */
    private fun normalize(text: String): String =
        text.trim().replace(Regex("\\s+"), " ")
}
