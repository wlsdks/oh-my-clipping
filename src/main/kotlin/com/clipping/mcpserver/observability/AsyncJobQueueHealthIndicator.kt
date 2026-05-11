package com.clipping.mcpserver.observability

import com.clipping.mcpserver.store.AsyncJobStore
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.stereotype.Component

/**
 * 비동기 작업 큐 헬스 인디케이터.
 * 대기 중인 작업 수가 임계값(100)을 초과하면 DOWN 상태를 반환하여
 * 작업 적체를 조기에 감지할 수 있게 한다.
 */
@Component
class AsyncJobQueueHealthIndicator(
    private val asyncJobStore: AsyncJobStore
) : HealthIndicator {

    companion object {
        /** 대기 작업 수 경고 임계값 */
        const val PENDING_THRESHOLD = 100
    }

    override fun health(): Health {
        val pending = asyncJobStore.countPending()
        val details = mapOf(
            "pendingJobs" to pending,
            "threshold" to PENDING_THRESHOLD
        )

        return if (pending <= PENDING_THRESHOLD) {
            Health.up().withDetails(details).build()
        } else {
            Health.down()
                .withDetails(details)
                .withDetail("reason", "Pending job count ($pending) exceeds threshold ($PENDING_THRESHOLD)")
                .build()
        }
    }
}
