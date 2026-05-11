package com.ohmyclipping.service

import com.ohmyclipping.service.dto.analytics.ArticleRankItem
import com.ohmyclipping.service.dto.analytics.ArticleRankingResponse
import com.ohmyclipping.service.dto.analytics.CategoryStatItem
import com.ohmyclipping.service.dto.analytics.CategoryStatsResponse
import com.ohmyclipping.service.dto.analytics.ClickRateSummaryResponse
import com.ohmyclipping.service.dto.analytics.DauPoint
import com.ohmyclipping.service.dto.analytics.DauResponse
import com.ohmyclipping.service.dto.analytics.WizardFunnelResponse
import com.ohmyclipping.service.dto.analytics.WizardFunnelStep
import com.ohmyclipping.store.ArticleEventRow
import com.ohmyclipping.store.PersonaStore
import com.ohmyclipping.store.WizardStepRow
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private val log = KotlinLogging.logger {}

/**
 * 유저 행동 분석 서비스.
 * UserEventService를 통해 집계 데이터를 조회하고, 페르소나 통계는 PersonaStore를 통해 조회한다.
 */
@Service
class AnalyticsService(
    private val userEventService: UserEventService,
    private val personaStore: PersonaStore,
    private val objectMapper: ObjectMapper,
    private val deliveryLogStore: com.ohmyclipping.store.DeliveryLogStore
) {

    /**
     * 기간 내 일별 활성 사용자 수(DAU)를 조회한다.
     */
    fun getDau(from: Instant, to: Instant): DauResponse {
        val dailyCounts = userEventService.dailyActiveUsers(from, to)
        return DauResponse(
            data = dailyCounts.map { DauPoint(date = it.date, count = it.count) }
        )
    }

    /**
     * 위자드 퍼널 분석 데이터를 조회한다.
     * wizard_step 이벤트의 event_data JSON에서 step과 action을 파싱하여 집계한다.
     */
    fun getWizardFunnel(from: Instant, to: Instant): WizardFunnelResponse {
        val rawEvents = userEventService.findWizardStepEvents(from, to)
        // event_data JSON에서 step과 action을 파싱한다.
        val parsed = rawEvents.mapNotNull { row -> parseWizardStep(row) }

        // 단계별 enter/complete 횟수를 집계한다.
        val stepEnters = parsed.filter { it.action == "enter" }
            .groupBy { it.step }
            .mapValues { it.value.size.toLong() }
        val stepCompletes = parsed.filter { it.action == "complete" }
            .groupBy { it.step }
            .mapValues { it.value.size.toLong() }

        // 모든 단계를 수집하고 정렬한다.
        val allSteps = (stepEnters.keys + stepCompletes.keys).distinct().sorted()

        return WizardFunnelResponse(
            data = allSteps.map { step ->
                val enters = stepEnters[step] ?: 0L
                val completes = stepCompletes[step] ?: 0L
                val dropRate = if (enters > 0) {
                    ((enters - completes).toDouble() / enters * 100)
                        .let { Math.round(it * 100) / 100.0 }
                } else {
                    0.0
                }
                WizardFunnelStep(
                    step = step,
                    enters = enters,
                    completes = completes,
                    dropRate = dropRate
                )
            }
        )
    }

    /**
     * 기사 랭킹을 조회한다.
     * article_click/impression 이벤트를 집계하고,
     * 기사 메타데이터(카테고리명, 출처명, 발행일)와 북마크 수를 함께 반환한다.
     *
     * @param from 조회 시작 시각
     * @param to 조회 종료 시각
     * @param sort 정렬 기준 (clicks, ctr, bookmarks)
     * @param limit 최대 반환 건수
     * @return 정렬된 기사 랭킹 응답
     */
    fun getArticleRanking(
        from: Instant,
        to: Instant,
        sort: String = "clicks",
        limit: Int = 20
    ): ArticleRankingResponse {
        // 기사 노출/클릭 이벤트를 조회한다.
        val rawEvents = userEventService.findArticleEvents(from, to)
        val parsed = rawEvents.mapNotNull { row -> parseArticleEvent(row) }
        val fallbackTitleBySummaryId = firstTitleBySummaryId(parsed)

        // summaryId별 노출/클릭 횟수를 집계한다.
        val impressionMap = parsed
            .filter { it.eventType == "article_impression" }
            .groupBy { it.summaryId }
            .mapValues { it.value.size.toLong() }
        val clickMap = parsed
            .filter { it.eventType == "article_click" }
            .groupBy { it.summaryId }
            .mapValues { it.value.size.toLong() }

        val allIds = (impressionMap.keys + clickMap.keys).distinct()
        if (allIds.isEmpty()) {
            return ArticleRankingResponse(data = emptyList())
        }

        // 기사 메타데이터와 북마크 수를 일괄 조회한다.
        val metadataMap = userEventService
            .findArticleMetadata(allIds)
            .associateBy { it.summaryId }
        val bookmarkMap = userEventService
            .countBookmarksBySummaryIds(allIds)

        // 각 기사의 집계 데이터를 조합한다.
        val items = allIds.map { summaryId ->
            val impCount = impressionMap[summaryId] ?: 0L
            val clickCount = clickMap[summaryId] ?: 0L
            val ctr = if (impCount > 0) {
                (clickCount.toDouble() / impCount * 100)
                    .let { Math.round(it * 100) / 100.0 }
            } else {
                0.0
            }
            val meta = metadataMap[summaryId]
            val title = meta?.title
                ?: fallbackTitleBySummaryId[summaryId]
            val bookmarks = bookmarkMap[summaryId] ?: 0L

            ArticleRankItem(
                rank = 0,
                summaryId = summaryId,
                title = title,
                categoryName = meta?.categoryName,
                sourceName = meta?.sourceName,
                publishedAt = meta?.publishedAt,
                clicks = clickCount,
                impressions = impCount,
                ctr = ctr,
                bookmarks = bookmarks
            )
        }

        // 정렬 기준에 따라 내림차순 정렬 후 limit 적용한다.
        val sorted = when (sort) {
            "ctr" -> items.sortedByDescending { it.ctr }
            "bookmarks" -> items.sortedByDescending { it.bookmarks }
            else -> items.sortedByDescending { it.clicks }
        }.take(limit)

        // 순위를 1부터 부여한다.
        val ranked = sorted.mapIndexed { index, item ->
            item.copy(rank = index + 1)
        }

        return ArticleRankingResponse(data = ranked)
    }

    /**
     * 클릭률 요약을 조회한다.
     * 기간 내 총 기사 클릭 수와 발송 건수를 집계하여 클릭률을 계산한다.
     *
     * @param from 조회 시작 시각
     * @param to 조회 종료 시각
     * @return 총 클릭, 총 발송, 클릭률(%)
     */
    fun getClickRateSummary(from: Instant, to: Instant): ClickRateSummaryResponse {
        // 기사 클릭 이벤트 수를 집계한다
        val totalClicks = userEventService.countByEventType("article_click", from, to)

        // 발송 건수를 delivery_log에서 집계한다
        val fromDate = from.atZone(java.time.ZoneId.of("Asia/Seoul")).toLocalDate()
        val toDate = to.atZone(java.time.ZoneId.of("Asia/Seoul")).toLocalDate()
        val dailyStats = deliveryLogStore.dailyStats(fromDate, toDate)
        val totalDeliveries = dailyStats.sumOf { it.sent.toLong() }

        // 클릭률을 계산한다 (발송 0건이면 0%)
        val clickRate = if (totalDeliveries > 0) {
            (totalClicks.toDouble() / totalDeliveries * 100)
                .let { Math.round(it * 100) / 100.0 }
        } else {
            0.0
        }

        return ClickRateSummaryResponse(
            totalClicks = totalClicks,
            totalDeliveries = totalDeliveries,
            clickRate = clickRate
        )
    }

    /**
     * 카테고리별 클릭/노출/CTR/점유율 통계를 조회한다.
     * 기사 이벤트를 집계한 뒤 메타데이터에서 카테고리 정보를 매핑하여
     * 카테고리 단위로 재집계한다.
     *
     * @param from 조회 시작 시각
     * @param to 조회 종료 시각
     * @return 클릭 수 내림차순으로 정렬된 카테고리 통계 응답
     */
    fun getCategoryStats(
        from: Instant,
        to: Instant
    ): CategoryStatsResponse {
        // 기사 노출/클릭 이벤트를 조회한다.
        val rawEvents = userEventService.findArticleEvents(from, to)
        val parsed = rawEvents.mapNotNull { row -> parseArticleEvent(row) }

        // summaryId별 노출/클릭 횟수를 집계한다.
        val impressionMap = parsed
            .filter { it.eventType == "article_impression" }
            .groupBy { it.summaryId }
            .mapValues { it.value.size.toLong() }
        val clickMap = parsed
            .filter { it.eventType == "article_click" }
            .groupBy { it.summaryId }
            .mapValues { it.value.size.toLong() }

        val allIds = (impressionMap.keys + clickMap.keys).distinct()
        if (allIds.isEmpty()) {
            return CategoryStatsResponse(data = emptyList())
        }

        // 기사 메타데이터에서 카테고리 정보를 조회한다.
        val metadataMap = userEventService
            .findArticleMetadata(allIds)
            .associateBy { it.summaryId }

        // 카테고리별로 클릭/노출을 재집계한다.
        data class CategoryAcc(
            var clicks: Long = 0L,
            var impressions: Long = 0L
        )
        val categoryMap = mutableMapOf<String, CategoryAcc>()
        val categoryNames = mutableMapOf<String, String>()

        for (summaryId in allIds) {
            val meta = metadataMap[summaryId]
            // 카테고리 정보가 없는 기사는 "미분류"로 처리한다.
            val catId = meta?.categoryId ?: "unknown"
            val catName = meta?.categoryName ?: "미분류"
            categoryNames[catId] = catName
            val acc = categoryMap.getOrPut(catId) { CategoryAcc() }
            acc.clicks += clickMap[summaryId] ?: 0L
            acc.impressions += impressionMap[summaryId] ?: 0L
        }

        // 전체 클릭 수를 산출하여 점유율을 계산한다.
        val totalClicks = categoryMap.values.sumOf { it.clicks }

        val items = categoryMap.map { (catId, acc) ->
            val ctr = if (acc.impressions > 0) {
                (acc.clicks.toDouble() / acc.impressions * 100)
                    .let { Math.round(it * 100) / 100.0 }
            } else {
                0.0
            }
            val share = if (totalClicks > 0) {
                (acc.clicks.toDouble() / totalClicks * 100)
                    .let { Math.round(it * 100) / 100.0 }
            } else {
                0.0
            }
            CategoryStatItem(
                categoryId = catId,
                categoryName = categoryNames[catId] ?: "미분류",
                clicks = acc.clicks,
                impressions = acc.impressions,
                ctr = ctr,
                sharePercent = share
            )
        }
            // 클릭 수 내림차순으로 정렬한다.
            .sortedByDescending { it.clicks }

        return CategoryStatsResponse(data = items)
    }

    /**
     * 특정 날짜의 클릭률(%)을 반환한다.
     * 홈 대시보드 "사용자 참여 트렌드" 카드의 7일 추이 계산에 사용한다.
     *
     * @param day 집계 대상 날짜 (KST 기준)
     * @return 클릭률(%) — 발송 0건이면 0.0 반환
     */
    fun getClickRateForDay(day: LocalDate): Double {
        val seoulZone = ZoneId.of("Asia/Seoul")
        // 해당 날짜의 시작/종료 Instant를 KST 기준으로 계산한다
        val from = day.atStartOfDay(seoulZone).toInstant()
        val to = day.plusDays(1).atStartOfDay(seoulZone).toInstant()

        // 해당 날짜의 기사 클릭 이벤트 수를 집계한다
        val clicks = userEventService.countByEventType("article_click", from, to)

        // 해당 날짜의 발송 건수를 delivery_log에서 집계한다
        val stats = deliveryLogStore.dailyStats(day, day)
        val deliveries = stats.sumOf { it.sent.toLong() }

        // 발송 0건이면 클릭률을 0.0으로 반환한다
        return if (deliveries > 0) (clicks.toDouble() / deliveries * 100) else 0.0
    }

    /**
     * 여러 날짜의 클릭률을 한 번에 계산한다.
     *
     * 날짜별 클릭 수와 발송 수를 범위 집계로 읽어 대시보드 7일 추이의 반복 쿼리를 줄인다.
     */
    fun getClickRatesForDays(days: List<LocalDate>): Map<LocalDate, Double> {
        val distinctDays = days.distinct()
        if (distinctDays.isEmpty()) return emptyMap()

        val clickCounts = userEventService.countByEventTypeForDays("article_click", distinctDays)
        val from = distinctDays.minOrNull() ?: return emptyMap()
        val to = distinctDays.maxOrNull() ?: return emptyMap()
        // delivery_log는 이미 날짜 범위 일별 집계를 지원하므로 한 번에 조회한다.
        val deliveriesByDay = deliveryLogStore.dailyStats(from, to).associate { it.date to it.sent.toLong() }

        return distinctDays.associateWith { day ->
            val deliveries = deliveriesByDay[day] ?: 0L
            if (deliveries > 0L) ((clickCounts[day] ?: 0L).toDouble() / deliveries * 100) else 0.0
        }
    }

    // -- Private helpers --

    /** wizard_step 이벤트의 event_data JSON을 파싱한다. */
    private fun parseWizardStep(row: WizardStepRow): ParsedWizardStep? {
        val data = row.eventData ?: return null
        return try {
            val node = objectMapper.readTree(data)
            val step = node.get("step")?.asText() ?: return null
            val action = node.get("action")?.asText() ?: "enter"
            ParsedWizardStep(step = step, action = action)
        } catch (e: Exception) {
            log.debug { "wizard_step event_data 파싱 실패: $data" }
            null
        }
    }

    /**
     * 메타데이터가 없는 기사 제목 fallback을 summaryId 기준으로 한 번만 구성한다.
     *
     * 랭킹 row를 만들 때마다 이벤트 전체를 다시 검색하지 않도록 첫 번째 제목만 보관한다.
     */
    private fun firstTitleBySummaryId(parsed: List<ParsedArticleEvent>): Map<String, String> {
        val titleBySummaryId = linkedMapOf<String, String>()
        for (event in parsed) {
            val title = event.title?.takeIf { it.isNotBlank() } ?: continue
            titleBySummaryId.putIfAbsent(event.summaryId, title)
        }
        return titleBySummaryId
    }

    /** article_impression/click 이벤트의 event_data JSON을 파싱한다. */
    private fun parseArticleEvent(row: ArticleEventRow): ParsedArticleEvent? {
        val data = row.eventData ?: return null
        return try {
            val node = objectMapper.readTree(data)
            val summaryId = node.get("summaryId")?.asText() ?: return null
            val title = node.get("title")?.asText()
            ParsedArticleEvent(
                eventType = row.eventType,
                summaryId = summaryId,
                title = title
            )
        } catch (e: Exception) {
            log.debug { "article event_data 파싱 실패: $data" }
            null
        }
    }

    /** wizard_step 파싱 결과 내부 모델. */
    private data class ParsedWizardStep(
        val step: String,
        val action: String
    )

    /** article 이벤트 파싱 결과 내부 모델. */
    private data class ParsedArticleEvent(
        val eventType: String,
        val summaryId: String,
        val title: String?
    )
}
