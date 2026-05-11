package com.ohmyclipping.service

import com.ohmyclipping.model.UserClippingRequest
import com.ohmyclipping.service.dto.RejectionReasonItem
import com.ohmyclipping.service.dto.TopicRankItem
import com.ohmyclipping.service.dto.UserRequestStatsResponse
import com.ohmyclipping.store.UserClippingRequestStore
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * 사용자 클리핑 요청의 분석 통계를 계산하는 전용 서비스.
 *
 * UserClippingRequestService 에서 통계 집계 책임을 분리해, 요청 lifecycle 처리와 독립적으로
 * 유지보수/테스트한다. 상태별 건수, 평균 승인 소요시간, 토픽 TOP 5, 반려 사유 TOP 5,
 * 주간 처리량을 하나의 응답 DTO 로 반환한다.
 */
@Service
class UserClippingRequestStatsService(
    private val requestStore: UserClippingRequestStore,
    private val clock: Clock = Clock.systemDefaultZone()
) {
    /**
     * 전체 사용자 요청의 분석 통계를 계산하여 반환한다.
     * 대기/승인/반려 건수, 평균 승인 소요시간, 토픽 랭킹 TOP 5,
     * 반려 사유 분포, 이번 주 처리량을 포함한다.
     */
    fun getRequestStats(): UserRequestStatsResponse {
        val weekAgo = Instant.now(clock).minus(7, ChronoUnit.DAYS)
        // 통계 화면은 전체 엔티티 목록 대신 DB 집계 스냅샷을 사용해 안전 상한과 메모리 사용을 피한다.
        val snapshot = requestStore.getStatsSnapshot(weekAgo)
        return UserRequestStatsResponse(
            pendingCount = snapshot.pendingCount,
            approvedCount = snapshot.approvedCount,
            rejectedCount = snapshot.rejectedCount,
            totalCount = snapshot.totalCount,
            avgApprovalHours = snapshot.avgApprovalHours,
            topTopics = snapshot.topTopics.map { TopicRankItem(it.name, it.count) },
            rejectionReasons = snapshot.rejectionReasons.map { RejectionReasonItem(it.name, it.count) },
            weeklyProcessedCount = snapshot.weeklyProcessedCount
        )
    }

    /**
     * 요청 목록으로부터 상태별 건수, 평균 승인 소요시간, 토픽 랭킹, 반려 사유 분포,
     * 주간 처리량을 계산해 통계 응답 DTO를 구성한다.
     */
    internal fun buildStats(allRequests: List<UserClippingRequest>): UserRequestStatsResponse {
        // 요청 엔티티의 상태 질의를 그대로 사용해 통계를 계산한다.
        val pendingCount = allRequests.count(UserClippingRequest::isPendingReview)
        val approvedCount = allRequests.count(UserClippingRequest::isApproved)
        val rejectedCount = allRequests.count(UserClippingRequest::isRejected)

        // 승인 소요시간(시간 단위)을 평균으로 계산한다.
        val approvalDurations = allRequests
            .mapNotNull(UserClippingRequest::approvalLeadTimeHours)
        val avgApprovalHours = if (approvalDurations.isNotEmpty()) {
            approvalDurations.average().let { "%.1f".format(it).toDouble() }
        } else {
            null
        }

        // 토픽 랭킹 TOP 5
        val topTopics = allRequests
            .groupBy { it.requestName.trim() }
            .map { (name, reqs) -> TopicRankItem(name, reqs.size) }
            .sortedByDescending { it.count }
            .take(5)

        // 반려 사유 분포
        val rejectionReasons = allRequests
            .mapNotNull { request -> request.rejectionReason()?.let { it to request } }
            .groupBy({ it.first }, { it.second })
            .map { (reason, reqs) ->
                RejectionReasonItem(reason, reqs.size)
            }
            .sortedByDescending { it.count }
            .take(5)

        // 이번 주 처리량 (7일 이내 승인/반려 건수)
        val weekAgo = Instant.now(clock).minus(7, ChronoUnit.DAYS)
        val weeklyProcessedCount = allRequests.count { request ->
            request.wasReviewedAfter(weekAgo)
        }

        return UserRequestStatsResponse(
            pendingCount = pendingCount,
            approvedCount = approvedCount,
            rejectedCount = rejectedCount,
            totalCount = allRequests.size,
            avgApprovalHours = avgApprovalHours,
            topTopics = topTopics,
            rejectionReasons = rejectionReasons,
            weeklyProcessedCount = weeklyProcessedCount
        )
    }
}
