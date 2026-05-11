package com.clipping.mcpserver.admin

import com.clipping.mcpserver.admin.dto.UpdateUserSubscriptionPreferenceRequest
import com.clipping.mcpserver.admin.dto.UserSubscriptionPreferenceResponse
import com.clipping.mcpserver.service.UserClippingRequestService
import com.clipping.mcpserver.service.dto.UpdateUserSubscriptionPreferenceCommand
import com.clipping.mcpserver.service.dto.UserSubscriptionPreferenceView
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 사용자 승인 구독의 즉시 반영 설정 API를 제공합니다.
 */
@RestController
@RequestMapping("/api/user/subscriptions/{requestId}/preferences")
class UserSubscriptionPreferenceController(
    private val userClippingRequestService: UserClippingRequestService
) {

    /**
     * 로그인 사용자의 승인된 구독 설정을 조회합니다.
     */
    @GetMapping
    fun get(
        authentication: Authentication,
        @PathVariable requestId: String
    ): UserSubscriptionPreferenceResponse =
        userClippingRequestService.getImmediateSubscriptionPreference(
            requesterUsername = authentication.name,
            requestId = requestId
        ).toResponse()

    /**
     * 기사 수, 제외 키워드, 중요도, 구독 활성 상태를 즉시 반영합니다.
     */
    @PutMapping
    fun update(
        authentication: Authentication,
        @PathVariable requestId: String,
        @RequestBody request: UpdateUserSubscriptionPreferenceRequest
    ): UserSubscriptionPreferenceResponse =
        userClippingRequestService.updateImmediateSubscriptionPreference(
            requesterUsername = authentication.name,
            requestId = requestId,
            command = UpdateUserSubscriptionPreferenceCommand(
                isActive = request.isActive,
                maxItems = request.maxItems,
                excludeKeywords = request.excludeKeywords,
                includeThreshold = request.includeThreshold,
                deliveryDays = request.deliveryDays,
                deliveryHour = request.deliveryHour,
                deliveryPreset = request.deliveryPreset
            )
        ).toResponse()

    /**
     * 서비스 모델을 HTTP 응답 DTO로 변환합니다.
     */
    private fun UserSubscriptionPreferenceView.toResponse() = UserSubscriptionPreferenceResponse(
        requestId = requestId,
        categoryId = categoryId,
        requestName = requestName,
        isActive = isActive,
        maxItems = maxItems,
        excludeKeywords = excludeKeywords,
        includeThreshold = includeThreshold,
        deliveryDays = deliveryDays,
        deliveryHour = deliveryHour,
        deliveryPreset = deliveryPreset,
        updatedAt = updatedAt.toString()
    )
}
