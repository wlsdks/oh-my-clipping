package com.ohmyclipping.user

import com.ohmyclipping.service.UserDeliveryLogService
import com.ohmyclipping.service.dto.user.UserDeliveryLogListView
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 사용자 발송 이력 조회 API.
 * 인증된 사용자의 구독 카테고리에 대한 발송 기록을 반환한다.
 */
@RestController
@RequestMapping("/api/user/delivery-logs")
class UserDeliveryLogController(
    private val userDeliveryLogService: UserDeliveryLogService
) {

    /**
     * 로그인 사용자의 발송 이력을 조회한다.
     *
     * @param days 조회 기간 일수 (기본 7일, 최대 90일)
     */
    @GetMapping
    fun getDeliveryLogs(
        authentication: Authentication,
        @RequestParam(defaultValue = "7") days: Int
    ): UserDeliveryLogListView =
        // 인증 사용자명으로 발송 이력을 조회하여 반환한다.
        userDeliveryLogService.getDeliveryLogs(
            requesterUsername = authentication.name,
            days = days
        )
}
