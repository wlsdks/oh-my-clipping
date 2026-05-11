package com.ohmyclipping.admin.dto

import com.ohmyclipping.model.DeliveryPreset
import com.ohmyclipping.model.UserDeliverySchedule

/**
 * 발송 스케줄 조회 응답 DTO.
 */
data class DeliveryScheduleResponse(
    val deliveryDays: List<String>,
    val deliveryHour: Int,
    val preset: String,
    val updatedAt: String
) {
    companion object {
        fun from(schedule: UserDeliverySchedule) = DeliveryScheduleResponse(
            deliveryDays = schedule.deliveryDays,
            deliveryHour = schedule.deliveryHour,
            preset = schedule.preset.name,
            updatedAt = schedule.updatedAt.toString()
        )
    }
}

/**
 * 발송 스케줄 저장 요청 DTO.
 */
data class DeliveryScheduleRequest(
    val deliveryDays: List<String>,
    val deliveryHour: Int,
    val preset: String
) {
    fun toPreset(): DeliveryPreset = DeliveryPreset.valueOf(preset.uppercase())
}
