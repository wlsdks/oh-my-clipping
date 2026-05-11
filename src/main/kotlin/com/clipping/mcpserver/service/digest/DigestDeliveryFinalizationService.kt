package com.clipping.mcpserver.service.digest

import com.clipping.mcpserver.service.StatsService
import com.clipping.mcpserver.store.DeliveryLogStore
import com.clipping.mcpserver.store.SummaryDeliveryStore
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Slack 전송 성공 이후 sent 마킹과 발송 통계를 마무리하는 서비스.
 * 외부 전송 성공 여부와 분리해 별도 트랜잭션으로 후처리를 수행한다.
 */
@Service
class DigestDeliveryFinalizationService(
    private val summaryStore: SummaryDeliveryStore,
    private val statsService: StatsService,
    private val deliveryLogStore: DeliveryLogStore
) {

    /**
     * 전송 성공한 다이제스트 항목을 sent 처리하고 발송 통계를 기록한다.
     * 요약 ID가 비어 있어도 통계는 남길 수 있도록 두 단계를 분리한다.
     */
    @Transactional
    fun finalizeDelivery(
        summaryIds: List<String>,
        categoryId: String,
        sendAttempts: Int,
        sendSuccesses: Int,
        deliveryLogId: String? = null,
        slackMessageTs: String? = null
    ) {
        // 이미 Slack 전송이 끝난 항목만 sent 처리한다.
        if (summaryIds.isNotEmpty()) {
            summaryStore.markSent(summaryIds)
        }

        // 목적지별 발송 시도/성공 통계를 후처리 단계에서 함께 적재한다.
        if (sendAttempts > 0) {
            statsService.recordDigestDelivery(categoryId, sendAttempts, sendSuccesses)
        }

        // delivery_log가 있으면 후처리 복구 완료와 함께 SENT 상태로 되돌린다.
        if (!deliveryLogId.isNullOrBlank()) {
            deliveryLogStore.updateStatus(
                deliveryLogId,
                "SENT",
                summaryIds.size,
                slackMessageTs
            )
        }
    }
}
