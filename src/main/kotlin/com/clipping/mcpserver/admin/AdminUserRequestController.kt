package com.clipping.mcpserver.admin

import com.clipping.mcpserver.admin.dto.ApproveClippingRequestRequest
import com.clipping.mcpserver.admin.dto.BulkApproveClippingRequestRequest
import com.clipping.mcpserver.admin.dto.BulkReviewRequest
import com.clipping.mcpserver.admin.dto.ReviewUserClippingRequest
import com.clipping.mcpserver.admin.dto.UserClippingRequestResponse
import com.clipping.mcpserver.service.dto.ApproveClippingRequestCommand
import com.clipping.mcpserver.service.dto.BulkActionFailure
import com.clipping.mcpserver.service.dto.BulkActionResponse
import com.clipping.mcpserver.service.dto.UserRequestStatsResponse
import com.clipping.mcpserver.error.InvalidInputException
import com.clipping.mcpserver.model.SourceLegalBasis
import com.clipping.mcpserver.model.UserClippingRequestStatus
import com.clipping.mcpserver.service.UserClippingRequestService
import com.clipping.mcpserver.service.UserClippingRequestStatsService
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 관리자용 사용자 요청 승인/반려 API.
 */
@RestController
@RequestMapping("/api/admin/user-requests")
class AdminUserRequestController(
    private val userClippingRequestService: UserClippingRequestService,
    private val userClippingRequestStatsService: UserClippingRequestStatsService,
    private val responseMapper: UserClippingRequestResponseMapper
) {

    @GetMapping
    fun list(
        @RequestParam(required = false) status: String?,
        @RequestParam(defaultValue = "50") size: Int
    ): List<UserClippingRequestResponse> {
        val safeSize = size.coerceIn(1, 200)
        val normalizedStatus = status?.trim()?.takeIf { it.isNotBlank() }?.let { raw ->
            try {
                UserClippingRequestStatus.valueOf(raw.uppercase())
            } catch (_: IllegalArgumentException) {
                throw InvalidInputException("지원하지 않는 상태값입니다: $raw")
            }
        }
        return userClippingRequestService.listRecentRequests(normalizedStatus, safeSize)
            .map(responseMapper::toResponse)
    }

    /**
     * 사용자 요청 분석 통계를 조회한다.
     * 대기/승인/반려 건수, 평균 승인 소요시간, 토픽 랭킹, 반려 사유 분포를 반환한다.
     */
    @GetMapping("/stats")
    fun stats(): UserRequestStatsResponse =
        userClippingRequestStatsService.getRequestStats()

    @PostMapping("/{id}/approve")
    fun approve(
        @PathVariable id: String,
        authentication: Authentication,
        @RequestBody request: ApproveClippingRequestRequest
    ): UserClippingRequestResponse {
        validateLegalReview(request.legalBasis, request.responsibilityAcknowledged, request.reviewNotes)
        val command = ApproveClippingRequestCommand(
            legalBasis = request.legalBasis,
            summaryAllowed = request.summaryAllowed,
            fulltextAllowed = request.fulltextAllowed,
            reviewNotes = request.reviewNotes,
            overrideSlackChannelId = request.overrideSlackChannelId?.trim()?.ifBlank { null }
        )
        return userClippingRequestService.approveRequest(
            requestId = id,
            reviewerUsername = authentication.name,
            command = command
        ).let(responseMapper::toResponse)
    }

    @PostMapping("/{id}/reject")
    fun reject(
        @PathVariable id: String,
        authentication: Authentication,
        @RequestBody request: ReviewUserClippingRequest
    ): UserClippingRequestResponse =
        userClippingRequestService.rejectRequest(
            requestId = id,
            reviewerUsername = authentication.name,
            reviewNote = request.reviewNote
        ).let(responseMapper::toResponse)

    /**
     * 선택한 요청을 일괄 승인한다.
     * 빈 목록은 거부하고, 각 항목별 부분 실패를 허용한다.
     */
    @PostMapping("/bulk-approve")
    fun bulkApprove(
        @RequestBody request: BulkApproveClippingRequestRequest,
        authentication: Authentication
    ): BulkActionResponse {
        ensureNonEmptyIds(request.ids)
        validateLegalReview(request.legalBasis, request.responsibilityAcknowledged, request.reviewNotes)
        val command = ApproveClippingRequestCommand(
            legalBasis = request.legalBasis,
            summaryAllowed = request.summaryAllowed,
            fulltextAllowed = request.fulltextAllowed,
            reviewNotes = request.reviewNotes
        )
        val results = userClippingRequestService.bulkApprove(
            ids = request.ids,
            reviewerUsername = authentication.name,
            command = command
        )
        return toBulkActionResponse(results)
    }

    /**
     * 선택한 요청을 일괄 반려한다.
     * 빈 목록은 거부하고, 각 항목별 부분 실패를 허용한다.
     */
    @PostMapping("/bulk-reject")
    fun bulkReject(
        @RequestBody request: BulkReviewRequest,
        authentication: Authentication
    ): BulkActionResponse {
        ensureNonEmptyIds(request.ids)
        val results = userClippingRequestService.bulkReject(
            ids = request.ids,
            reviewerUsername = authentication.name,
            reviewNote = request.reviewNote
        )
        return toBulkActionResponse(results)
    }

    companion object {
        /** 일괄 처리 최대 건수. AdminUserAccountController와 동일 기준 적용. */
        const val MAX_BULK_SIZE = 50
        /** 승인 모달에서 허용되는 법적 근거 목록. PROHIBITED는 거부 흐름에서만 사용. */
        val ALLOWED_APPROVAL_LEGAL_BASES = setOf("QUOTATION_ONLY", "OPEN_LICENSE", "LICENSED")
    }

    private fun ensureNonEmptyIds(ids: List<String>) {
        if (ids.isEmpty()) throw InvalidInputException("처리할 항목을 선택해 주세요.")
        if (ids.size > MAX_BULK_SIZE) throw InvalidInputException("일괄 처리는 최대 ${MAX_BULK_SIZE}건까지 가능합니다.")
    }

    /**
     * 법적 검토 입력값을 컨트롤러 진입점에서 명시적으로 검증한다.
     * Bean Validation 의존성 없이도 동일한 400 응답을 제공한다.
     */
    private fun validateLegalReview(legalBasis: String, responsibilityAcknowledged: Boolean, reviewNotes: String?) {
        if (!responsibilityAcknowledged) {
            throw InvalidInputException("법적 책임 확인이 필요합니다")
        }
        // 승인 모달에서는 PROHIBITED를 제외한 3가지만 허용한다
        if (legalBasis !in ALLOWED_APPROVAL_LEGAL_BASES) {
            throw InvalidInputException("올바르지 않은 법적 근거: $legalBasis")
        }
        if ((reviewNotes?.length ?: 0) > 200) {
            throw InvalidInputException("검토 메모는 200자 이내여야 합니다")
        }
    }

    /**
     * 서비스 반환 결과를 프론트엔드가 기대하는 succeeded/failed 형태로 변환한다.
     */
    private fun toBulkActionResponse(
        results: List<Pair<String, Result<com.clipping.mcpserver.model.UserClippingRequest>>>
    ): BulkActionResponse {
        val succeeded = mutableListOf<String>()
        val failed = mutableListOf<BulkActionFailure>()
        for ((id, result) in results) {
            if (result.isSuccess) {
                succeeded.add(id)
            } else {
                val ex = result.exceptionOrNull()
                val reason = ex?.message ?: "알 수 없는 오류"
                val code = when (ex) {
                    is com.clipping.mcpserver.error.NotFoundException -> "NOT_FOUND"
                    is com.clipping.mcpserver.error.InvalidInputException -> "ALREADY_PROCESSED"
                    else -> "UNKNOWN"
                }
                failed.add(BulkActionFailure(id = id, reason = reason, code = code))
            }
        }
        return BulkActionResponse(succeeded = succeeded, failed = failed)
    }
}
