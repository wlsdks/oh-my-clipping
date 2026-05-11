package com.clipping.mcpserver.service

import com.clipping.mcpserver.error.InvalidInputException
import com.clipping.mcpserver.error.NotFoundException
import com.clipping.mcpserver.error.ensureValid
import com.clipping.mcpserver.model.DeliveryDaySummary
import com.clipping.mcpserver.model.DeliveryLog
import com.clipping.mcpserver.store.DeliveryLogStore
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 관리자용 발송 이력 조회 및 재발송 서비스.
 * 발송 요약 통계, 이력 목록/상세 조회, 실패 건 재발송 기능을 제공한다.
 */
@Service
class DeliveryAdminService(
    private val deliveryLogStore: DeliveryLogStore
) {

    /**
     * 특정 날짜의 발송 요약 통계를 반환한다.
     *
     * @param date 조회 대상 날짜 (기본: 오늘)
     */
    @Transactional(readOnly = true)
    fun summary(
        date: LocalDate = LocalDate.now(ZoneId.of("Asia/Seoul"))
    ): DeliveryDaySummary {
        return deliveryLogStore.summary(date)
    }

    /**
     * 필터 조건에 따라 발송 이력을 페이지네이션으로 조회한다.
     *
     * @param since null이 아니면 createdAt이 이 시각 이후인 건만 반환한다 (within 파라미터 전달용)
     */
    @Transactional(readOnly = true)
    fun findLogs(
        categoryId: String? = null,
        status: String? = null,
        from: LocalDate? = null,
        to: LocalDate? = null,
        since: Instant? = null,
        offset: Int = 0,
        limit: Int = 30
    ): List<DeliveryLog> {
        return deliveryLogStore.findAll(categoryId, status, from, to, since, offset, limit)
    }

    /**
     * 필터 조건에 따라 발송 이력의 총 건수를 반환한다.
     *
     * @param since null이 아니면 createdAt이 이 시각 이후인 건만 집계한다
     */
    @Transactional(readOnly = true)
    fun countLogs(
        categoryId: String? = null,
        status: String? = null,
        from: LocalDate? = null,
        to: LocalDate? = null,
        since: Instant? = null
    ): Int {
        return deliveryLogStore.countAll(categoryId, status, from, to, since)
    }

    /**
     * 실패 상태의 발송 건을 재시도 대상으로 표시한다.
     * 실제 재발송은 SlackDigestWorker의 findPendingRetries에서 처리한다.
     *
     * @param logId 재시도 대상 발송 로그 ID
     * @throws NoSuchElementException 발송 기록이 존재하지 않을 경우
     * @throws IllegalArgumentException 실패 상태가 아닌 경우
     */
    fun retryDelivery(logId: String) {
        // 발송 기록 존재 여부를 확인한다
        val log = deliveryLogStore.findById(logId)
            ?: throw NotFoundException("발송 기록을 찾을 수 없습니다: $logId")

        // 전송 실패 또는 전송 후 후처리 실패 상태만 재시도 대상으로 허용한다
        ensureValid(log.status == "FAILED" || log.status == "FINALIZATION_FAILED") {
            "실패 상태의 발송만 재발송할 수 있습니다"
        }

        // 이미 재시도된 실패 건도 다시 집어갈 수 있도록 플래그를 되돌린다.
        deliveryLogStore.resetForRetry(logId)
    }
}
