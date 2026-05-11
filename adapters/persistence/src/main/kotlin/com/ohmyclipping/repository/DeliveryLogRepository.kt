package com.ohmyclipping.repository

import com.ohmyclipping.entity.DeliveryLogEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.time.LocalDate

/**
 * 발송 이력 JPA 리포지토리.
 * delivery_log 테이블에 대한 CRUD 및 조회 메서드를 제공한다.
 * 동적 필터 조합 및 통계 쿼리는 Store 구현에서 처리한다.
 */
interface DeliveryLogRepository : JpaRepository<DeliveryLogEntity, String> {

    /** 카테고리+채널+날짜+시간 조합으로 기존 발송 이력을 조회한다 (중복 방지용). */
    fun findByCategoryIdAndChannelIdAndDeliveryDateAndDeliveryHour(
        categoryId: String,
        channelId: String,
        deliveryDate: LocalDate,
        deliveryHour: Int
    ): DeliveryLogEntity?

    /** 상태별 발송 이력을 조회한다. */
    fun findByStatus(status: String): List<DeliveryLogEntity>

    /** 카테고리 ID로 발송 이력을 조회한다. */
    fun findByCategoryId(categoryId: String): List<DeliveryLogEntity>

    /** 특정 날짜의 발송 이력을 조회한다. */
    fun findByDeliveryDate(deliveryDate: LocalDate): List<DeliveryLogEntity>

    /** 오래된 발송 이력을 삭제한다. */
    @Modifying
    @Query("DELETE FROM DeliveryLogEntity d WHERE d.createdAt < :cutoff")
    fun deleteByCreatedAtBefore(cutoff: Instant): Int

    /**
     * 여러 채널 ID에 해당하는 발송 이력을 최신순으로 조회한다.
     * 개인정보 export 등 사용자별 발송 이력 열람에서 사용한다.
     */
    fun findByChannelIdInOrderByDeliveryDateDescDeliveryHourDesc(
        channelIds: Collection<String>
    ): List<DeliveryLogEntity>
}
