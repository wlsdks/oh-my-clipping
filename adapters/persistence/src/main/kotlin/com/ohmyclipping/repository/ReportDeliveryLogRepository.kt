package com.ohmyclipping.repository

import com.ohmyclipping.entity.ReportDeliveryLogEntity
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant

interface ReportDeliveryLogRepository : JpaRepository<ReportDeliveryLogEntity, String> {
    fun findByReportTypeAndPeriodKeyAndChannelId(
        reportType: String,
        periodKey: String,
        channelId: String
    ): ReportDeliveryLogEntity?

    fun findByReportTypeAndPeriodKeyAndStatus(
        reportType: String,
        periodKey: String,
        status: String
    ): List<ReportDeliveryLogEntity>

    /**
     * 리포트 타입별 이력을 최신순(updated_at DESC)으로 조회한다.
     * 관리자 UI의 실행 이력 드로어에서 사용한다.
     */
    fun findByReportTypeOrderByUpdatedAtDesc(
        reportType: String,
        pageable: Pageable
    ): List<ReportDeliveryLogEntity>

    /** 전체 리포트 이력을 최신순으로 조회한다 (report_type 필터 없음). */
    fun findAllByOrderByUpdatedAtDesc(pageable: Pageable): List<ReportDeliveryLogEntity>

    fun deleteByCreatedAtBefore(cutoff: Instant): Int
}
