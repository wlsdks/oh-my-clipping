package com.clipping.mcpserver.service

import com.clipping.mcpserver.service.dto.ReportDeliveryHistoryItem
import com.clipping.mcpserver.store.ReportDeliveryLogStore
import org.springframework.stereotype.Service

/**
 * 리포트 발송 이력 조회 서비스.
 * 관리자 UI에서 주간/월간 리포트의 최근 실행 이력을 조회할 수 있도록 한다.
 *
 * 단순 래퍼지만 리스트 변환을 DTO 단에서 표준화하기 위해 별도 서비스로 분리했다.
 */
@Service
class ReportDeliveryHistoryService(
    private val reportDeliveryLogStore: ReportDeliveryLogStore
) {

    /**
     * 리포트 발송 이력을 최신순으로 조회한다.
     *
     * @param reportType 필터 (null/공백이면 전체 조회)
     * @param limit 최대 조회 건수 (1~200)
     * @return 최신 updated_at 기준 내림차순 DTO 목록
     */
    fun listHistory(reportType: String?, limit: Int): List<ReportDeliveryHistoryItem> {
        // 입력 reportType은 대소문자 차이를 흡수한다.
        val normalizedType = reportType?.trim()?.takeIf { it.isNotBlank() }?.uppercase()
        val entries = reportDeliveryLogStore.listHistory(normalizedType, limit)
        return entries.map { toDto(it) }
    }

    /** 저장소 HistoryEntry를 응답 DTO로 변환한다. */
    private fun toDto(entry: ReportDeliveryLogStore.HistoryEntry): ReportDeliveryHistoryItem {
        return ReportDeliveryHistoryItem(
            id = entry.id,
            reportType = entry.reportType,
            periodKey = entry.periodKey,
            channelId = entry.channelId,
            status = entry.status,
            durationMs = entry.durationMs,
            itemsProcessed = entry.itemsProcessed,
            errorMessage = entry.errorMessage,
            startedAt = entry.createdAt.toString(),
            finishedAt = entry.updatedAt.toString()
        )
    }
}
