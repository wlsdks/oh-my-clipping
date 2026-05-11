package com.ohmyclipping.user

import com.ohmyclipping.admin.dto.DeliveryScheduleRequest
import com.ohmyclipping.admin.dto.DeliveryScheduleResponse
import com.ohmyclipping.service.UserDeliveryScheduleService
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

/**
 * 사용자 발송 스케줄 API.
 * 사용자가 자기 계정의 다이제스트 발송 요일·시간을 조회·설정한다.
 */
@RestController
@RequestMapping("/api/user/delivery-schedule")
class UserDeliveryScheduleController(
    private val service: UserDeliveryScheduleService
) {

    /**
     * 현재 사용자의 발송 스케줄을 조회한다.
     * 설정이 없으면 기본값(평일 오전 9시)을 반환한다.
     */
    @GetMapping
    fun getSchedule(authentication: Authentication): DeliveryScheduleResponse {
        // 인증된 사용자명으로 스케줄을 조회한다.
        val schedule = service.getSchedule(authentication.name)
        return DeliveryScheduleResponse.from(schedule)
    }

    /**
     * 현재 사용자의 발송 스케줄을 저장(또는 갱신)한다.
     */
    @PutMapping
    fun updateSchedule(
        authentication: Authentication,
        @RequestBody request: DeliveryScheduleRequest
    ): DeliveryScheduleResponse {
        // 요청 DTO를 서비스에 위임하여 저장한다.
        val saved = service.saveSchedule(
            username = authentication.name,
            deliveryDays = request.deliveryDays,
            deliveryHour = request.deliveryHour,
            preset = request.toPreset()
        )
        return DeliveryScheduleResponse.from(saved)
    }
}
