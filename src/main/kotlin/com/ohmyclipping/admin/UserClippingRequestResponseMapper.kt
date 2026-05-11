package com.ohmyclipping.admin

import com.ohmyclipping.admin.dto.UserClippingRequestResponse
import com.ohmyclipping.model.UserClippingRequest
import com.ohmyclipping.service.UserClippingRequestService
import org.springframework.stereotype.Component

/**
 * 사용자 요청 도메인 모델을 API 응답 DTO로 변환한다.
 */
@Component
class UserClippingRequestResponseMapper(
    private val userClippingRequestService: UserClippingRequestService
) {

    /**
     * 요청 기본 정보와 실제 전달 상태를 함께 응답 DTO로 조합한다.
     */
    fun toResponse(request: UserClippingRequest): UserClippingRequestResponse {
        // 승인 상태와 별개로 실제 전달 가능 상태를 함께 계산해 내려준다.
        val deliveryStatus = userClippingRequestService.getDeliveryStatus(request)
        // 승인된 카테고리 이름을 서비스를 통해 조회한다.
        val categoryName = request.approvedCategoryId?.let {
            userClippingRequestService.resolveCategoryName(it)
        }
        return UserClippingRequestResponse(
            id = request.id,
            requesterUserId = request.requesterUserId,
            requestName = request.requestName,
            sourceName = request.sourceName,
            sourceUrl = request.sourceUrl,
            slackChannelId = request.slackChannelId,
            personaName = request.personaName,
            personaPrompt = request.personaPrompt,
            summaryStyle = request.summaryStyle,
            targetAudience = request.targetAudience,
            selectedPresetId = request.selectedPresetId,
            requestNote = request.requestNote,
            status = request.status.name,
            reviewNote = request.reviewNote,
            reviewedByUserId = request.reviewedByUserId,
            reviewedAt = request.reviewedAt?.toString(),
            approvedCategoryId = request.approvedCategoryId,
            approvedCategoryName = categoryName,
            approvedPersonaId = request.approvedPersonaId,
            approvedSourceId = request.approvedSourceId,
            createdAt = request.createdAt.toString(),
            updatedAt = request.updatedAt.toString(),
            deliveryState = deliveryStatus.deliveryState,
            collectingReady = deliveryStatus.collectingReady,
            totalSourceCount = deliveryStatus.totalSourceCount,
            readySourceCount = deliveryStatus.readySourceCount,
            representativeSourceVerificationStatus = deliveryStatus.representativeSourceVerificationStatus
        )
    }
}
