package com.ohmyclipping.store

import com.ohmyclipping.model.UserDeliverySchedule

/**
 * 사용자 발송 스케줄 저장소 인터페이스.
 * 사용자가 설정한 요일·시간 기반 다이제스트 발송 스케줄을 관리한다.
 */
interface UserDeliveryScheduleStore {
    /** 사용자 ID로 스케줄을 조회한다. 없으면 null. */
    fun findByUserId(userId: String): UserDeliverySchedule?

    /** 스케줄을 저장하거나 갱신한다 (UPSERT). */
    fun upsert(schedule: UserDeliverySchedule)

    /** 현재 요일·시간에 발송 대상인 스케줄 목록을 조회한다. */
    fun findSchedulesDueNow(dayOfWeek: String, hour: Int): List<UserDeliverySchedule>

    /** 개인 스케줄이 설정된 모든 사용자 ID를 반환한다. */
    fun findAllUserIds(): Set<String>
}
