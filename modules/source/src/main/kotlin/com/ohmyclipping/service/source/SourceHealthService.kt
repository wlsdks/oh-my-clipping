package com.ohmyclipping.service.source

import com.ohmyclipping.model.RssSource
import com.ohmyclipping.service.dto.SourceHealthResponse
import com.ohmyclipping.service.dto.UnhealthySource
import com.ohmyclipping.store.RssSourceStore
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration

/**
 * RSS 소스 헬스 상태를 집계하는 서비스.
 * 어드민 대시보드에서 "어떤 소스가 죽어가는지" 한눈에 확인하는 용도다.
 */
@Service
class SourceHealthService(
    private val rssSourceStore: RssSourceStore,
    private val clock: Clock,
) {
    /**
     * 소스 헬스 요약을 반환한다.
     *
     * 기본 "불건강" 판정 기준은 마지막 수신 성공이 24시간 이전이거나 연속
     * 실패 횟수가 3회 이상인 경우다. [staleHours] 를 지정하면 시간 기준을
     * 그 값으로 덮어쓴다 (예: `staleHours = 6` → 최근 6시간 내에 수신하지
     * 못한 소스도 불건강 취급). 실패 횟수 기준은 그대로 유지한다.
     */
    fun getHealth(staleHours: Int? = null): SourceHealthResponse {
        // 전체 소스 조회 (활성 + 비활성 모두 포함하지 않고 활성만)
        val sources = rssSourceStore.list(limit = 1000).filter { it.isActive }
        val total = sources.size

        // staleHours 가 오면 이를 Duration 으로 환산해 판정 기준으로 쓴다.
        val threshold = staleHours?.takeIf { it > 0 }
            ?.let { Duration.ofHours(it.toLong()) }
            ?: STALE_THRESHOLD

        // unhealthy 분류 후 오래된 순 정렬, 최대 5건
        val unhealthy = sources
            .filter { isUnhealthy(it, threshold) }
            .sortedWith(compareBy(nullsFirst()) { it.lastSuccessAt })
            .take(MAX_UNHEALTHY_DISPLAY)
            .map { it.toUnhealthySource(threshold) }

        return SourceHealthResponse(
            totalCount = total,
            // unhealthy.size는 표시 한계일 수 있으므로, 실제 unhealthy 개수로 계산
            healthyCount = total - sources.count { isUnhealthy(it, threshold) },
            unhealthy = unhealthy,
        )
    }

    /** 시간 기준 또는 실패 횟수 기준으로 unhealthy 판정 */
    private fun isUnhealthy(source: RssSource, threshold: Duration): Boolean {
        val lastSuccess = source.lastSuccessAt
        // 한 번도 수신 없거나 임계 시간 이전이면 unhealthy
        val staleByTime = lastSuccess == null || lastSuccess.isBefore(clock.instant().minus(threshold))
        // 연속 실패 3회 이상이면 unhealthy
        val staleByFailures = source.crawlFailCount >= FAILURE_THRESHOLD
        return staleByTime || staleByFailures
    }

    /** 사람이 읽을 수 있는 reason 문자열 생성 */
    private fun RssSource.toUnhealthySource(threshold: Duration): UnhealthySource {
        return UnhealthySource(
            id = id,
            name = name,
            lastSuccessAt = lastSuccessAt,
            crawlFailCount = crawlFailCount,
            reason = buildReason(this, threshold),
        )
    }

    private fun buildReason(source: RssSource, threshold: Duration): String {
        val lastSuccess = source.lastSuccessAt
        // 한 번도 수신되지 않은 경우
        if (lastSuccess == null) return "한 번도 수신되지 않음"
        val between = Duration.between(lastSuccess, clock.instant())
        // 24시간 이상 미수신이면 일 단위로 표시
        val daysSince = between.toDays()
        if (daysSince >= 1) return "${daysSince}일째 미수신"
        // 시간 기준에 걸렸다면 시간 단위로 표시
        if (between >= threshold) {
            val hours = between.toHours().coerceAtLeast(1)
            return "${hours}시간째 미수신"
        }
        // 시간은 정상이지만 연속 실패가 큰 경우
        if (source.crawlFailCount >= FAILURE_THRESHOLD) return "연속 ${source.crawlFailCount}회 실패"
        return "수신 지연"
    }

    companion object {
        private val STALE_THRESHOLD: Duration = Duration.ofHours(24)
        private const val FAILURE_THRESHOLD = 3
        private const val MAX_UNHEALTHY_DISPLAY = 5
    }
}
