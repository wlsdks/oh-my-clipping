package com.clipping.mcpserver.service

import com.clipping.mcpserver.store.DeliveryLogStore
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

private val log = KotlinLogging.logger {}

/**
 * 지수 백오프 기반 발송 재시도 오케스트레이터.
 * 최대 7회 재시도를 수행하며, 시도 간격은 2, 5, 15, 30, 60, 120, 240분으로 증가한다.
 * 7회 모두 실패하면 해당 건을 ABANDONED 상태로 전이한다.
 */
@Component
class DeliveryRetryOrchestrator(
    private val deliveryLogStore: DeliveryLogStore
) {
    companion object {
        /** 최대 재시도 횟수. 이 횟수를 초과하면 ABANDONED로 전이한다. */
        const val MAX_RETRIES = 7

        /** stuck claim 복구 타임아웃(분). */
        private const val CLAIM_TIMEOUT_MINUTES = 5L

        /** 재시도 간격(분) 목록. 인덱스 = 현재 retry_count 값. */
        private val BACKOFF_INTERVALS = listOf(2L, 5L, 15L, 30L, 60L, 120L, 240L)

        /**
         * 현재 retry_count에 대응하는 백오프 대기 시간을 반환한다.
         * retryCount가 목록 범위를 초과하면 마지막 간격을 그대로 사용한다.
         *
         * @param retryCount 현재 retry_count (0-based)
         * @return 다음 재시도까지 대기 시간
         */
        fun backoffInterval(retryCount: Int): Duration {
            val index = retryCount.coerceAtMost(BACKOFF_INTERVALS.size - 1)
            return Duration.ofMinutes(BACKOFF_INTERVALS[index])
        }

        /**
         * 재시도 실패 결과를 계산한다.
         * MAX_RETRIES에 도달하면 ABANDONED, 그 전이면 FAILED + 다음 재시도 시각을 반환한다.
         *
         * @param currentRetryCount 실패 직전의 retry_count 값
         * @param errorMessage 오류 메시지 (500자 이내로 잘림)
         * @return 갱신할 상태, retry_count, next_retry_at을 담은 결과 객체
         */
        fun computeFailureOutcome(currentRetryCount: Int, errorMessage: String?): FailureOutcome {
            val newCount = currentRetryCount + 1
            return if (newCount >= MAX_RETRIES) {
                FailureOutcome(newCount, "ABANDONED", null, errorMessage)
            } else {
                FailureOutcome(newCount, "FAILED", Instant.now() + backoffInterval(newCount - 1), errorMessage)
            }
        }
    }

    /**
     * 재시도 실패 결과 객체.
     * recordFailure() 호출에 필요한 모든 필드를 담는다.
     */
    data class FailureOutcome(
        val newRetryCount: Int,
        val newStatus: String,
        val nextRetryAt: Instant?,
        val lastError: String?
    )

    /**
     * stuck RETRYING 행을 복구한다.
     * 워커 사이클 시작 전에 호출하여 비정상 종료로 남은 행을 회수한다.
     */
    fun recoverStuckClaims() = deliveryLogStore.recoverStuckClaims(CLAIM_TIMEOUT_MINUTES)

    /**
     * 현재 재시도 가능한 발송 실패 건 목록을 반환한다.
     *
     * @return next_retry_at이 지난 FAILED/FINALIZATION_FAILED 건 목록
     */
    fun findPendingRetries() = deliveryLogStore.findPendingRetries(MAX_RETRIES)

    /**
     * 특정 발송 건을 RETRYING 상태로 원자적으로 점유한다.
     *
     * @param id 발송 로그 ID
     * @return 점유 성공 여부 (다른 워커가 먼저 가져간 경우 false)
     */
    fun claim(id: String) = deliveryLogStore.claimForRetry(id)

    /**
     * 재시도 실패를 기록한다.
     * 지수 백오프로 next_retry_at을 계산하고, MAX_RETRIES 도달 시 ABANDONED로 전이한다.
     *
     * @param id 발송 로그 ID
     * @param currentRetryCount 현재 retry_count 값
     * @param error 발생한 예외
     */
    fun recordFailure(id: String, currentRetryCount: Int, error: Exception) {
        val outcome = computeFailureOutcome(currentRetryCount, error.message)
        deliveryLogStore.recordFailure(id, outcome.newRetryCount, outcome.nextRetryAt, outcome.newStatus, outcome.lastError)
        if (outcome.newStatus == "ABANDONED") {
            log.error { "Delivery ABANDONED after $MAX_RETRIES retries: id=$id" }
        }
    }

    /**
     * 특정 카테고리+채널의 병합 가능한 ABANDONED 건 목록을 반환한다.
     */
    fun findAbandonedForMerge(categoryId: String, channelId: String) =
        deliveryLogStore.findAbandonedForMerge(categoryId, channelId, 24)

    /**
     * 24시간 이상 경과한 ABANDONED 건을 STALE로 전이한다.
     */
    fun transitionStale() = deliveryLogStore.transitionToStale(24)
}
