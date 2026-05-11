package com.clipping.mcpserver.service.digest

import com.clipping.mcpserver.service.event.DigestDeliveryFinalizationRequestedEvent
import com.clipping.mcpserver.support.InterruptibleSleep
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

private val digestDeliveryFinalizationLog = KotlinLogging.logger {}

/**
 * 다이제스트 전송 후처리 이벤트를 비동기로 소비한다.
 * 후처리 실패가 호출자에게 되돌아가지 않도록 재시도 후 로그만 남긴다.
 */
@Component
class DigestDeliveryFinalizationListener(
    private val digestDeliveryFinalizationService: DigestDeliveryFinalizationService
) {

    companion object {
        /** 후처리 재시도 횟수 */
        const val MAX_RETRY_ATTEMPTS = 3

        /** 후처리 재시도 간격(ms) */
        const val RETRY_DELAY_MS = 200L
    }

    /**
     * Slack 성공 후 sent 마킹과 통계 적재를 비동기로 재시도한다.
     * 최종 실패해도 상위 전송 결과는 유지하고 운영 로그로만 남긴다.
     */
    @Async
    @EventListener
    fun handle(event: DigestDeliveryFinalizationRequestedEvent) {
        repeat(MAX_RETRY_ATTEMPTS) { attemptIndex ->
            try {
                // 별도 트랜잭션 서비스에 위임해 후처리를 독립적으로 완료한다.
                digestDeliveryFinalizationService.finalizeDelivery(
                    summaryIds = event.summaryIds,
                    categoryId = event.categoryId,
                    sendAttempts = event.sendAttempts,
                    sendSuccesses = event.sendSuccesses,
                    deliveryLogId = event.deliveryLogId,
                    slackMessageTs = event.slackMessageTs
                )
                return
            } catch (error: Exception) {
                val attempt = attemptIndex + 1
                val isLastAttempt = attempt == MAX_RETRY_ATTEMPTS

                // 마지막 시도 전까지는 짧게 대기해 일시적 DB 오류를 흡수한다.
                if (!isLastAttempt) {
                    digestDeliveryFinalizationLog.warn(error) {
                        "Digest delivery finalization retry scheduled: " +
                            "categoryId=${event.categoryId}, attempt=$attempt"
                    }
                    // 비동기 재시도 사이 대기도 인터럽트 플래그를 보존한다.
                    InterruptibleSleep.sleep(
                        delayMs = RETRY_DELAY_MS,
                        context = "DigestDeliveryFinalizationListener retry"
                    )
                } else {
                    digestDeliveryFinalizationLog.error(error) {
                        "Digest delivery finalization exhausted retries: " +
                            "categoryId=${event.categoryId}, summaryCount=${event.summaryIds.size}"
                    }
                }
            }
        }
    }
}
