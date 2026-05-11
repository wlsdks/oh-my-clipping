package com.clipping.mcpserver.service

import com.clipping.mcpserver.error.ensureValid
import com.clipping.mcpserver.error.requireFound
import com.clipping.mcpserver.model.DeliveryPreset
import com.clipping.mcpserver.model.UserDeliverySchedule
import com.clipping.mcpserver.store.AdminUserStore
import com.clipping.mcpserver.store.UserDeliveryScheduleStore
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service

private val log = KotlinLogging.logger {}

/**
 * 사용자 발송 스케줄 비즈니스 로직.
 * 스케줄 조회·저장 및 입력값 검증을 담당한다.
 */
@Service
class UserDeliveryScheduleService(
    private val scheduleStore: UserDeliveryScheduleStore,
    private val adminUserStore: AdminUserStore
) {
    companion object {
        private val VALID_DAYS = setOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")
        private val WEEKDAYS = listOf("MON", "TUE", "WED", "THU", "FRI")
        private val EVERYDAY = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")
        /** 허용된 발송 시간 슬롯 (정시 기준) — 배치 처리 완료 후 발송 */
        val ALLOWED_DELIVERY_HOURS = setOf(8, 12, 18)
        /** 허용된 최대 발송 건수 */
        val ALLOWED_MAX_ITEMS = setOf(1, 3, 5)
    }

    /**
     * 사용자의 발송 스케줄을 조회한다. 저장된 스케줄이 없으면 기본값을 반환한다.
     */
    fun getSchedule(username: String): UserDeliverySchedule {
        // 사용자 식별 실패는 잘못된 입력이 아니라 조회 대상 미존재로 해석한다.
        val user = requireUser(username)
        return scheduleStore.findByUserId(user.id)
            ?: UserDeliverySchedule(userId = user.id)
    }

    /**
     * 사용자의 발송 스케줄을 저장한다.
     * 프리셋에 따라 요일을 자동 결정하거나 사용자 입력을 검증한다.
     */
    fun saveSchedule(
        username: String,
        deliveryDays: List<String>,
        deliveryHour: Int,
        preset: DeliveryPreset
    ): UserDeliverySchedule {
        // 저장 전 사용자 존재를 먼저 검증한다.
        val user = requireUser(username)

        // 허용된 발송 시간 슬롯만 수락한다.
        ensureValid(deliveryHour in ALLOWED_DELIVERY_HOURS) {
            "발송 시간은 ${ALLOWED_DELIVERY_HOURS.sorted().joinToString(", ")}시만 선택할 수 있습니다."
        }

        // 프리셋에 따라 요일을 결정하되, CUSTOM은 전체 입력을 엄격히 검증한다.
        val resolvedDays = when (preset) {
            DeliveryPreset.WEEKDAYS -> WEEKDAYS
            DeliveryPreset.EVERYDAY -> EVERYDAY
            DeliveryPreset.CUSTOM -> normalizeCustomDays(deliveryDays)
        }

        val schedule = UserDeliverySchedule(
            userId = user.id,
            deliveryDays = resolvedDays,
            deliveryHour = deliveryHour,
            preset = preset
        )
        scheduleStore.upsert(schedule)
        // 저장 후 최신 데이터 반환
        return scheduleStore.findByUserId(user.id) ?: schedule
    }

    /**
     * 사용자명을 내부 사용자 엔티티로 해석한다.
     */
    private fun requireUser(username: String) =
        requireFound(adminUserStore.findByUsername(username)) {
            "사용자를 찾을 수 없습니다: $username"
        }

    /**
     * CUSTOM 요일 목록을 저장 가능한 형태로 정규화한다.
     * 잘못된 값이 하나라도 섞여 있으면 부분 허용하지 않고 전체를 거부한다.
     */
    private fun normalizeCustomDays(deliveryDays: List<String>): List<String> {
        // 직접 선택은 최소 1개 이상의 입력을 요구한다.
        ensureValid(deliveryDays.isNotEmpty()) { "직접 선택 시 최소 1개 요일을 선택해야 합니다." }
        // 저장 전 공백 제거와 대문자 정규화를 먼저 적용한다.
        val normalizedDays = deliveryDays
            .map { it.trim().uppercase() }
            .filter { it.isNotBlank() }
        ensureValid(normalizedDays.isNotEmpty()) { "유효한 요일을 선택해야 합니다." }
        // 일부만 저장되면 사용자 기대와 달라지므로 잘못된 요일이 하나라도 있으면 전체를 거부한다.
        val invalidDays = normalizedDays.filter { it !in VALID_DAYS }.distinct()
        ensureValid(invalidDays.isEmpty()) {
            "유효하지 않은 요일이 포함되어 있습니다: ${invalidDays.joinToString(",")}"
        }
        return normalizedDays.distinct()
    }
}
