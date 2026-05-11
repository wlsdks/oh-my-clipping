package com.ohmyclipping.store

import com.ohmyclipping.model.AsyncJob
import com.ohmyclipping.model.AsyncJobType
import java.time.Instant

interface AsyncJobStore {
    fun enqueue(type: AsyncJobType, payloadJson: String, maxAttempts: Int): AsyncJob
    fun findById(id: String): AsyncJob?
    fun claimNextDue(now: Instant): AsyncJob?
    fun countPending(): Long
    fun oldestPendingNextRunAt(): Instant?
    fun markSucceeded(id: String, resultJson: String?)
    fun markRetry(id: String, errorMessage: String, nextRunAt: Instant)
    fun markFailed(id: String, errorMessage: String)

    /** RUNNING 상태가 stuckMinutes 이상인 작업을 PENDING/FAILED로 전환. (복구 수, 최종 실패 수)를 반환. */
    fun recoverStuck(stuckMinutes: Long, maxAttempts: Int): Pair<Int, Int>

    /** 서버 종료 시 staleSeconds 이상 미갱신된 RUNNING 작업을 PENDING으로 리셋. */
    fun resetStalledRunningToPending(staleSeconds: Long): Int

    /** 완료(SUCCEEDED/FAILED) 상태이며 cutoff 이전에 생성된 작업을 삭제한다. */
    fun deleteCompletedOlderThan(cutoff: Instant): Int

    /** 최근 작업을 생성일 역순으로 조회한다. MCP 작업 목록 도구용. */
    fun listRecent(limit: Int): List<AsyncJob>
}
