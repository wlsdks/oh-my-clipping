package com.clipping.mcpserver.store

import com.clipping.mcpserver.entity.UserDeliveryScheduleEntity
import com.clipping.mcpserver.model.DeliveryPreset
import com.clipping.mcpserver.model.UserDeliverySchedule
import com.clipping.mcpserver.repository.UserDeliveryScheduleRepository
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 사용자 발송 스케줄 JPA 구현. JdbcUserDeliveryScheduleStore를 대체한다.
 */
@Repository
@Primary
class JpaUserDeliveryScheduleStore(
    private val repository: UserDeliveryScheduleRepository
) : UserDeliveryScheduleStore {

    override fun findByUserId(userId: String): UserDeliverySchedule? =
        repository.findByUserId(userId)?.toModel()

    override fun upsert(schedule: UserDeliverySchedule) {
        val now = Instant.now()
        // 요일 리스트를 쉼표 구분 문자열로 변환한다.
        val daysStr = schedule.deliveryDays.joinToString(",")
        val existing = repository.findByUserId(schedule.userId)
        if (existing != null) {
            // 기존 레코드가 있으면 갱신한다.
            existing.deliveryDays = daysStr
            existing.deliveryHour = schedule.deliveryHour
            existing.preset = schedule.preset.name
            existing.updatedAt = now
            repository.save(existing)
        } else {
            // 새 레코드를 생성한다.
            repository.save(
                UserDeliveryScheduleEntity(
                    userId = schedule.userId,
                    deliveryDays = daysStr,
                    deliveryHour = schedule.deliveryHour,
                    preset = schedule.preset.name,
                    createdAt = now,
                    updatedAt = now
                )
            )
        }
    }

    /**
     * 현재 요일·시간에 발송 대상인 스케줄 목록을 조회한다.
     * 오늘 생성·수정된 스케줄은 다음날부터 적용되도록 제외한다.
     * DB 수준에서 updated_at 필터를 적용하여 JPA 캐시 stale 문제를 회피한다.
     */
    override fun findSchedulesDueNow(dayOfWeek: String, hour: Int): List<UserDeliverySchedule> {
        val todayStart = LocalDate.now(ZoneId.of("Asia/Seoul"))
            .atStartOfDay(ZoneId.of("Asia/Seoul"))
            .toInstant()
        return repository.findDueSchedules(dayOfWeek, hour, todayStart)
            .map { it.toModel() }
    }

    /**
     * 개인 스케줄이 설정된 모든 사용자 ID를 반환한다.
     * 오늘 생성·수정된 스케줄은 다음날부터 유효하므로 제외한다.
     * DB 수준에서 updated_at 필터를 적용하여 JPA 캐시 stale 문제를 회피한다.
     */
    override fun findAllUserIds(): Set<String> {
        val todayStart = LocalDate.now(ZoneId.of("Asia/Seoul"))
            .atStartOfDay(ZoneId.of("Asia/Seoul"))
            .toInstant()
        return repository.findUserIdsUpdatedBefore(todayStart)
    }

    private fun UserDeliveryScheduleEntity.toModel() = UserDeliverySchedule(
        userId = userId,
        deliveryDays = deliveryDays.split(",").map { it.trim() }.filter { it.isNotBlank() },
        deliveryHour = deliveryHour,
        preset = DeliveryPreset.valueOf(preset),
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
