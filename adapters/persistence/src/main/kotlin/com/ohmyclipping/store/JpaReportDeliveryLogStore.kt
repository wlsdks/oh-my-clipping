package com.ohmyclipping.store

import com.ohmyclipping.entity.ReportDeliveryLogEntity
import com.ohmyclipping.repository.ReportDeliveryLogRepository
import org.springframework.context.annotation.Primary
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * 리포트 발송 이력 JPA 구현. JdbcReportDeliveryLogStore를 대체한다.
 */
@Repository
@Primary
class JpaReportDeliveryLogStore(
    private val repository: ReportDeliveryLogRepository
) : ReportDeliveryLogStore {

    override fun tryReserve(
        reportType: String,
        periodKey: String,
        channelId: String
    ): String? {
        // UNIQUE(report_type, period_key, channel_id) 제약으로 중복을 방지한다.
        val existing = repository.findByReportTypeAndPeriodKeyAndChannelId(reportType, periodKey, channelId)
        if (existing != null) return null

        val id = UUID.randomUUID().toString()
        val now = Instant.now()
        return try {
            repository.save(
                ReportDeliveryLogEntity(
                    id = id,
                    reportType = reportType,
                    periodKey = periodKey,
                    channelId = channelId,
                    status = "RESERVED",
                    createdAt = now,
                    updatedAt = now
                )
            )
            id
        } catch (_: DataIntegrityViolationException) {
            // 동시 삽입 경쟁 시 중복 키 예외를 무시한다.
            null
        }
    }

    override fun markSent(
        id: String,
        snapshotId: String,
        slackMessageTs: String?,
        durationMs: Long?,
        itemsProcessed: Int?
    ) {
        val entity = repository.findById(id).orElse(null) ?: return
        // 성공 전송 시 스냅샷과 Slack timestamp + 이력 메타데이터를 함께 기록한다.
        entity.status = "SENT"
        entity.snapshotId = snapshotId
        entity.slackMessageTs = slackMessageTs
        entity.errorMessage = null
        // 운영 이력 UI에서 소요시간과 처리 건수를 보여주기 위해 기록한다.
        if (durationMs != null) entity.durationMs = durationMs
        if (itemsProcessed != null) entity.itemsProcessed = itemsProcessed
        entity.updatedAt = Instant.now()
        repository.save(entity)
    }

    override fun markFailed(
        id: String,
        snapshotId: String?,
        errorMessage: String,
        durationMs: Long?
    ) {
        val entity = repository.findById(id).orElse(null) ?: return
        // 실패 시 스냅샷 ID와 에러 메시지를 기록한다.
        entity.status = "FAILED"
        entity.snapshotId = snapshotId
        entity.errorMessage = errorMessage
        if (durationMs != null) entity.durationMs = durationMs
        entity.updatedAt = Instant.now()
        repository.save(entity)
    }

    override fun listHistory(reportType: String?, limit: Int): List<ReportDeliveryLogStore.HistoryEntry> {
        // 페이지 크기 상한을 두어 관리자 요청 실수로 과도한 조회가 일어나지 않게 한다.
        val cappedLimit = limit.coerceIn(1, 200)
        val pageable = PageRequest.of(0, cappedLimit)

        val entities = if (reportType.isNullOrBlank()) {
            repository.findAllByOrderByUpdatedAtDesc(pageable)
        } else {
            repository.findByReportTypeOrderByUpdatedAtDesc(reportType, pageable)
        }
        return entities.map { toHistoryEntry(it) }
    }

    override fun findFailedByTypeAndPeriod(
        reportType: String,
        periodKey: String
    ): List<ReportDeliveryLogStore.FailedDeliverySlot> {
        return repository.findByReportTypeAndPeriodKeyAndStatus(reportType, periodKey, "FAILED")
            .map { entity ->
                ReportDeliveryLogStore.FailedDeliverySlot(
                    id = entity.id,
                    channelId = entity.channelId,
                    errorMessage = entity.errorMessage
                )
            }
    }

    @Transactional
    override fun deleteOlderThan(days: Int): Int {
        val cutoff = Instant.now().minus(days.toLong(), ChronoUnit.DAYS)
        return repository.deleteByCreatedAtBefore(cutoff)
    }

    override fun findByKey(
        reportType: String,
        periodKey: String,
        channelId: String
    ): ReportDeliveryLogStore.SlotInfo? {
        val entity = repository.findByReportTypeAndPeriodKeyAndChannelId(reportType, periodKey, channelId)
            ?: return null
        return ReportDeliveryLogStore.SlotInfo(
            id = entity.id,
            status = entity.status,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }

    @Transactional
    override fun deleteById(id: String) {
        repository.deleteById(id)
    }

    /** 엔티티를 응답용 HistoryEntry로 변환한다. */
    private fun toHistoryEntry(entity: ReportDeliveryLogEntity): ReportDeliveryLogStore.HistoryEntry {
        return ReportDeliveryLogStore.HistoryEntry(
            id = entity.id,
            reportType = entity.reportType,
            periodKey = entity.periodKey,
            channelId = entity.channelId,
            status = entity.status,
            snapshotId = entity.snapshotId,
            slackMessageTs = entity.slackMessageTs,
            errorMessage = entity.errorMessage,
            durationMs = entity.durationMs,
            itemsProcessed = entity.itemsProcessed,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }
}
