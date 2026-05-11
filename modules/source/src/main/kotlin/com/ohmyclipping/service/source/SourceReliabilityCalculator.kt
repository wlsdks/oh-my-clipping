package com.ohmyclipping.service.source

import com.ohmyclipping.store.RssSourceStore
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import kotlin.math.max
import kotlin.math.roundToInt

private val log = KotlinLogging.logger {}

/**
 * 소스 신뢰도 점수를 계산한다.
 * successRate(40%) + freshness(30%) + articleFrequency(30%) = 0~100
 *
 * 계산된 점수는 SourceHealthScheduler에 의해 매시간 갱신된다.
 */
@Service
class SourceReliabilityCalculator(
    private val rssSourceStore: RssSourceStore
) {

    companion object {
        /** successRate 가중치 */
        const val WEIGHT_SUCCESS_RATE = 40

        /** freshness 가중치 */
        const val WEIGHT_FRESHNESS = 30

        /** articleFrequency 가중치 */
        const val WEIGHT_ARTICLE_FREQUENCY = 30

        /** successRate 계산 시 분모로 사용할 최대 실패 횟수 */
        const val MAX_FAIL_DENOMINATOR = 30.0

        /** 기사 빈도 집계 기간 (7일) */
        private val ARTICLE_FREQUENCY_WINDOW = Duration.ofDays(7)
    }

    /**
     * 모든 활성 소스에 대해 신뢰도 점수를 계산하여 반환한다.
     *
     * @return 소스 ID → 신뢰도 점수(0~100) 매핑
     */
    fun calculateAll(): Map<String, Int> {
        val sources = rssSourceStore.list()
        if (sources.isEmpty()) return emptyMap()

        // 7일간 기사 수집 건수를 소스별로 집계한다.
        val cutoff = Instant.now().minus(ARTICLE_FREQUENCY_WINDOW)
        val articleCounts = rssSourceStore.countArticlesBySource(cutoff)

        val now = Instant.now()
        return sources.associate { source ->
            val successRate = calculateSuccessRate(source.crawlFailCount)
            val freshness = calculateFreshness(source.lastSuccessAt, now)
            val frequency = calculateArticleFrequency(articleCounts[source.id] ?: 0)

            // 가중 합산 후 0~100 범위로 클램핑한다.
            val score = (successRate * WEIGHT_SUCCESS_RATE +
                freshness * WEIGHT_FRESHNESS +
                frequency * WEIGHT_ARTICLE_FREQUENCY)
                .roundToInt()
                .coerceIn(0, 100)

            source.id to score
        }
    }

    /**
     * 수집 성공률을 0.0~1.0으로 계산한다.
     * 실패 횟수가 0이면 1.0, 아니면 max(0, 1 - failCount/30.0).
     */
    private fun calculateSuccessRate(crawlFailCount: Int): Double =
        if (crawlFailCount == 0) 1.0
        else max(0.0, 1.0 - crawlFailCount / MAX_FAIL_DENOMINATOR)

    /**
     * 마지막 수집 성공 시각 기준 신선도를 0.0~1.0으로 계산한다.
     * 1시간 미만 → 1.0, 24시간 미만 → 0.7, 72시간 미만 → 0.3, 그 외 → 0.0.
     */
    private fun calculateFreshness(lastSuccessAt: Instant?, now: Instant): Double {
        if (lastSuccessAt == null) return 0.0
        val hoursSince = Duration.between(lastSuccessAt, now).toHours()
        return when {
            hoursSince < 1 -> 1.0
            hoursSince < 24 -> 0.7
            hoursSince < 72 -> 0.3
            else -> 0.0
        }
    }

    /**
     * 7일간 기사 수집 빈도를 0.0~1.0으로 계산한다.
     * 10건 이상 → 1.0, 5건 이상 → 0.7, 1건 이상 → 0.4, 0건 → 0.0.
     */
    private fun calculateArticleFrequency(count: Int): Double =
        when {
            count >= 10 -> 1.0
            count >= 5 -> 0.7
            count >= 1 -> 0.4
            else -> 0.0
        }
}
