package com.ohmyclipping.service

import com.ohmyclipping.error.InvalidInputException
import com.ohmyclipping.error.NotFoundException
import com.ohmyclipping.error.ensureValid
import com.ohmyclipping.model.BatchSummary
import com.ohmyclipping.model.SourceRegionType
import com.ohmyclipping.model.TrendPeriodType
import com.ohmyclipping.model.TrendRegionType
import com.ohmyclipping.model.TrendSnapshot
import com.ohmyclipping.model.TrendSnapshotStatus
import com.ohmyclipping.model.TrendVisualCard
import com.ohmyclipping.model.TrendVisualCardType
import com.ohmyclipping.model.TrendVisualReviewStatus
import com.ohmyclipping.store.BatchSummaryStore
import com.ohmyclipping.store.CategoryStore
import com.ohmyclipping.store.RssItemStore
import com.ohmyclipping.store.RssSourceStore
import com.ohmyclipping.store.ReviewItemDecisionStore
import com.ohmyclipping.store.StatsStore
import com.ohmyclipping.store.SummaryFeedbackStore
import com.ohmyclipping.store.TrendSnapshotStore
import com.ohmyclipping.store.TrendVisualCardStore
import org.springframework.stereotype.Service
import java.net.URI
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale

@Service
class AdminTrendSnapshotService(
    private val batchSummaryStore: BatchSummaryStore,
    private val categoryStore: CategoryStore,
    private val rssItemStore: RssItemStore,
    private val rssSourceStore: RssSourceStore,
    private val trendSnapshotStore: TrendSnapshotStore,
    private val trendVisualCardStore: TrendVisualCardStore,
    private val statsStore: StatsStore,
    private val summaryFeedbackStore: SummaryFeedbackStore,
    private val reviewItemDecisionStore: ReviewItemDecisionStore
) {

    private val seoulZone: ZoneId = ZoneId.of("Asia/Seoul")
    private val releaseKeywords = listOf("report", "whitepaper", "playbook", "리포트", "보고서", "연구")
    private val globalFallbackKeywords = listOf("global", "international", "worldwide")
    private val domesticFallbackKeywords = listOf("국내", "한국", "korea")

    /**
     * 주간/월간 트렌드 스냅샷을 생성합니다.
     */
    fun runSnapshot(
        periodTypeRaw: String,
        categoryId: String?,
        regionTypeRaw: String?,
        templateType: String = "DETAILED",
        generatedBy: String?
    ): TrendSnapshotResult {
        val periodType = parsePeriodType(periodTypeRaw)
        val regionType = parseRegionType(regionTypeRaw)
        val categoryName = resolveCategoryName(categoryId)
        val dateRange = resolveDateRange(periodType)
        val summaries = loadWindowSummaries(categoryId, dateRange.from, dateRange.to)
        val sourceRegionByItemId = resolveSourceRegionByItemId(summaries)
        val filtered = summaries.filter { matchesRegion(it, regionType, sourceRegionByItemId) }
        val topItems = filtered
            .sortedWith(compareByDescending<BatchSummary> { it.importanceScore }.thenByDescending { it.createdAt })
            .take(12)

        val topKeywords = computeTopKeywords(filtered, 8)
        val title = buildTitle(periodType, categoryName, regionType, dateRange.from, dateRange.to)
        val summary = buildSnapshotSummary(periodType, regionType, filtered, topItems, topKeywords)
        val actionItems = buildActionItems(periodType, regionType, topKeywords)
        val sourceCount = filtered.mapNotNull { parseHost(it.sourceLink) }.distinct().size

        val saved = trendSnapshotStore.save(
            TrendSnapshot(
                id = "",
                periodType = periodType,
                snapshotFrom = dateRange.from,
                snapshotTo = dateRange.to,
                categoryId = categoryId,
                categoryName = categoryName,
                regionType = regionType,
                title = title,
                summary = summary,
                keySignals = topKeywords,
                actionItems = actionItems,
                sourceCount = sourceCount,
                itemCount = filtered.size,
                status = TrendSnapshotStatus.DRAFT,
                templateType = templateType,
                generatedBy = generatedBy?.trim()?.ifBlank { null },
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )
        )
        return saved.toResult()
    }

    /**
     * 생성된 스냅샷 목록을 조회합니다.
     */
    fun listSnapshots(
        periodTypeRaw: String?,
        categoryId: String?,
        regionTypeRaw: String?,
        statusRaw: String?,
        limit: Int
    ): List<TrendSnapshotResult> {
        ensureValid(limit in 1..300) { "limit must be between 1 and 300" }
        val periodType = periodTypeRaw?.takeIf { it.isNotBlank() }?.let(::parsePeriodType)
        val normalizedCategoryId = categoryId?.trim()?.ifBlank { null }
        val regionType = regionTypeRaw?.takeIf { it.isNotBlank() }?.let(::parseRegionType)
        val status = statusRaw?.takeIf { it.isNotBlank() }?.let(::parseSnapshotStatus)
        return trendSnapshotStore.list(periodType, normalizedCategoryId, regionType, status, limit).map { it.toResult() }
    }

    /**
     * 스냅샷을 발행 상태로 전환합니다.
     */
    fun publishSnapshot(snapshotId: String, publishedBy: String?): TrendSnapshotResult {
        val snapshot = trendSnapshotStore.findById(snapshotId)
            ?: throw NotFoundException("Trend snapshot not found: $snapshotId")
        val now = Instant.now()
        val updated = trendSnapshotStore.save(
            snapshot.copy(
                status = TrendSnapshotStatus.PUBLISHED,
                generatedBy = publishedBy?.trim()?.ifBlank { snapshot.generatedBy } ?: snapshot.generatedBy,
                publishedAt = now,
                updatedAt = now
            )
        )
        return updated.toResult()
    }

    /**
     * 스냅샷 기반 비주얼 카드 초안을 생성합니다.
     */
    fun generateVisual(
        snapshotId: String,
        cardTypeRaw: String,
        generatedBy: String?
    ): TrendVisualCardResult {
        val snapshot = trendSnapshotStore.findById(snapshotId)
            ?: throw NotFoundException("Trend snapshot not found: $snapshotId")
        val cardType = parseCardType(cardTypeRaw)
        val panelCount = when (cardType) {
            TrendVisualCardType.INFO_CARD -> 3
            TrendVisualCardType.COMIC_4 -> 4
            TrendVisualCardType.COMIC_8 -> 8
        }
        val panels = buildPanels(snapshot, panelCount)
        val saved = trendVisualCardStore.save(
            TrendVisualCard(
                id = "",
                snapshotId = snapshot.id,
                cardType = cardType,
                title = "${snapshot.title} - ${cardTypeLabel(cardType)}",
                summary = snapshot.summary,
                panels = panels,
                reviewStatus = TrendVisualReviewStatus.PENDING,
                generatedBy = generatedBy?.trim()?.ifBlank { null },
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )
        )
        return saved.toResult()
    }

    /**
     * 비주얼 카드 목록을 조회합니다.
     */
    fun listVisualCards(
        snapshotId: String?,
        reviewStatusRaw: String?,
        limit: Int
    ): List<TrendVisualCardResult> {
        ensureValid(limit in 1..300) { "limit must be between 1 and 300" }
        val reviewStatus = reviewStatusRaw?.takeIf { it.isNotBlank() }?.let(::parseReviewStatus)
        val cards = if (!snapshotId.isNullOrBlank()) {
            trendVisualCardStore.listBySnapshotId(snapshotId, limit)
        } else {
            trendVisualCardStore.list(reviewStatus, limit)
        }
        return cards.map { it.toResult() }
    }

    /**
     * 비주얼 카드 검수/발행 상태를 반영합니다.
     */
    fun reviewVisualCard(
        cardId: String,
        approved: Boolean,
        reviewNote: String?,
        reviewedBy: String?,
        publish: Boolean?
    ): TrendVisualCardResult {
        val card = trendVisualCardStore.findById(cardId)
            ?: throw NotFoundException("Trend visual card not found: $cardId")
        val now = Instant.now()
        val nextStatus = if (approved) TrendVisualReviewStatus.APPROVED else TrendVisualReviewStatus.REJECTED
        val shouldPublish = publish == true && approved
        val updated = trendVisualCardStore.save(
            card.copy(
                reviewStatus = nextStatus,
                reviewNote = reviewNote?.trim()?.ifBlank { null },
                reviewedBy = reviewedBy?.trim()?.ifBlank { null },
                reviewedAt = now,
                published = shouldPublish,
                updatedAt = now
            )
        )
        return updated.toResult()
    }

    /**
     * PDF/리포트 발간 알림용 후보를 조회합니다.
     */
    fun listReportReleases(days: Int, categoryId: String?, limit: Int): List<ReportReleaseResult> {
        ensureValid(days in 1..90) { "days must be between 1 and 90" }
        ensureValid(limit in 1..200) { "limit must be between 1 and 200" }
        val from = Instant.now().minus(days.toLong(), ChronoUnit.DAYS)
        val summaries = loadWindowSummaries(categoryId, from.atZone(seoulZone).toLocalDate(), LocalDate.now(seoulZone))
            .filter { it.createdAt >= from }

        return summaries
            .asSequence()
            .mapNotNull { summary ->
                val title = summary.translatedTitle?.takeIf { it.isNotBlank() } ?: summary.originalTitle
                val normalizedTitle = title.lowercase(Locale.ROOT)
                val isPdf = summary.sourceLink.lowercase(Locale.ROOT).contains(".pdf")
                val matchedKeyword = releaseKeywords.firstOrNull { normalizedTitle.contains(it.lowercase(Locale.ROOT)) }
                val reason = when {
                    isPdf -> "PDF 링크 감지"
                    matchedKeyword != null -> "리포트 키워드 감지: $matchedKeyword"
                    else -> null
                } ?: return@mapNotNull null

                ReportReleaseResult(
                    summaryId = summary.id,
                    categoryId = summary.categoryId,
                    title = title,
                    sourceLink = summary.sourceLink,
                    importanceScore = summary.importanceScore,
                    releaseType = if (isPdf) "PDF" else "REPORT_ANNOUNCEMENT",
                    detectionReason = reason,
                    createdAt = summary.createdAt
                )
            }
            .sortedWith(compareByDescending<ReportReleaseResult> { it.importanceScore }.thenByDescending { it.createdAt })
            .take(limit)
            .toList()
    }

    /**
     * 운영 품질 요약 리포트를 생성합니다.
     */
    fun qualitySummary(days: Int): QualitySummaryResult {
        ensureValid(days in 7..90) { "days must be between 7 and 90" }
        val to = Instant.now()
        val from = to.minus(days.toLong(), ChronoUnit.DAYS)
        val windowSummaries = loadWindowSummaries(null, from.atZone(seoulZone).toLocalDate(), LocalDate.now(seoulZone))
            .filter { it.createdAt in from..to }
        val summaryIds = windowSummaries.map { it.id }
        val decisions = reviewItemDecisionStore.findBySummaryIds(summaryIds)
        val includeCount = decisions.count { it.status.name == "INCLUDE" }
        val reviewCount = decisions.count { it.status.name == "REVIEW" }
        val excludeCount = decisions.count { it.status.name == "EXCLUDE" }
        val decidedCount = includeCount + excludeCount + reviewCount
        val reviewPendingRate = if (decidedCount == 0) 0.0 else reviewCount.toDouble() / decidedCount.toDouble()
        val excludeRate = if (decidedCount == 0) 0.0 else excludeCount.toDouble() / decidedCount.toDouble()

        val fromDate = from.atZone(seoulZone).toLocalDate()
        val toDate = to.atZone(seoulZone).toLocalDate()
        val stats = statsStore.findDailyRange(null, fromDate, toDate)
        val itemsCollected = stats.sumOf { it.itemsCollected }
        val itemsSummarized = stats.sumOf { it.itemsSummarized }
        val itemsSent = stats.sumOf { it.itemsSent }

        // 발송 성공률 계산
        val totalSendAttempts = stats.sumOf { it.slackSendAttempts }
        val totalSendSuccesses = stats.sumOf { it.slackSendSuccesses }
        val sendSuccessRate = if (totalSendAttempts == 0) 0.0 else totalSendSuccesses.toDouble() / totalSendAttempts.toDouble()

        val hotFeedback = summaryFeedbackStore.findWeeklyHot(from, to, 30, null)
        val totalFeedback = hotFeedback.sumOf { it.totalCount }
        val positiveFeedback = hotFeedback.sumOf { it.likeCount }
        val positiveRate = if (totalFeedback == 0) 0.0 else positiveFeedback.toDouble() / totalFeedback.toDouble()

        val recommendations = mutableListOf<String>()
        if (reviewPendingRate > 0.35) recommendations += "검토 대기 비율이 높습니다. 카테고리 규칙 임계치를 상향하거나 제외 키워드를 보강하세요."
        if (excludeRate > 0.45) recommendations += "제외 비율이 높습니다. 소스 신뢰도 점검과 소스 정책 재검토를 권장합니다."
        if (positiveRate < 0.45 && totalFeedback >= 10) recommendations += "직원 긍정 반응이 낮습니다. 페르소나 문체와 주간 카드 액션 아이템을 조정하세요."
        if (recommendations.isEmpty()) recommendations += "현재 지표가 안정적입니다. 현 기준을 유지하면서 월간 회고만 진행하세요."

        return QualitySummaryResult(
            from = from,
            to = to,
            days = days,
            itemsCollected = itemsCollected,
            itemsSummarized = itemsSummarized,
            itemsSent = itemsSent,
            reviewPendingCount = reviewCount,
            reviewPendingRate = reviewPendingRate,
            excludeRate = excludeRate,
            feedbackTotal = totalFeedback,
            feedbackPositiveRate = positiveRate,
            sendSuccessRate = sendSuccessRate,
            recommendations = recommendations
        )
    }

    private fun resolveCategoryName(categoryId: String?): String {
        if (categoryId.isNullOrBlank()) return "전체 카테고리"
        return categoryStore.findById(categoryId)?.name
            ?: throw NotFoundException("Category not found: $categoryId")
    }

    private fun resolveDateRange(periodType: TrendPeriodType): DateRange {
        val to = LocalDate.now(seoulZone)
        val from = when (periodType) {
            TrendPeriodType.WEEKLY -> to.minusDays(6)
            TrendPeriodType.MONTHLY -> to.minusDays(29)
        }
        return DateRange(from = from, to = to)
    }

    private fun loadWindowSummaries(categoryId: String?, from: LocalDate, to: LocalDate): List<BatchSummary> {
        // LocalDate 범위를 Instant 범위로 변환한다 (서울 시간 기준 00:00 ~ 다음날 00:00).
        val fromInstant = from.atStartOfDay(seoulZone).toInstant()
        val toInstant = to.plusDays(1).atStartOfDay(seoulZone).toInstant()
        // 단일 쿼리로 전체 범위를 조회하여 카테고리별 N+1 쿼리를 방지한다.
        return batchSummaryStore.findByDateRange(fromInstant, toInstant, categoryId?.takeIf { it.isNotBlank() })
    }

    private fun resolveSourceRegionByItemId(summaries: List<BatchSummary>): Map<String, SourceRegionType> {
        // rssItemId가 null인 요약(retention 후 기사 삭제됨)은 소스 지역 조회 대상에서 제외한다.
        val rssItemIds = summaries.mapNotNull { it.rssItemId }.distinct()
        if (rssItemIds.isEmpty()) return emptyMap()
        val rssItemsById = rssItemStore.findByIds(rssItemIds).associateBy { it.id }
        val sourceRegionBySourceId = rssSourceStore.list().associate { it.id to it.sourceRegion }
        return rssItemIds.associateWith { rssItemId ->
            val sourceId = rssItemsById[rssItemId]?.rssSourceId
            sourceId?.let(sourceRegionBySourceId::get) ?: SourceRegionType.UNKNOWN
        }
    }

    private fun matchesRegion(
        summary: BatchSummary,
        regionType: TrendRegionType,
        sourceRegionByItemId: Map<String, SourceRegionType>
    ): Boolean {
        if (regionType == TrendRegionType.ALL) return true
        return classifyRegion(summary, sourceRegionByItemId) == regionType
    }

    private fun classifyRegion(
        summary: BatchSummary,
        sourceRegionByItemId: Map<String, SourceRegionType>
    ): TrendRegionType {
        // rssItemId가 null이면 소스 지역 미지정과 동일하게 처리한다.
        val sourceRegion = summary.rssItemId?.let { sourceRegionByItemId[it] }
        when (sourceRegion) {
            SourceRegionType.DOMESTIC -> return TrendRegionType.DOMESTIC
            SourceRegionType.GLOBAL -> return TrendRegionType.GLOBAL
            SourceRegionType.UNKNOWN, null -> {
                // 소스 지역 미지정인 기존 데이터만 키워드 정책으로 보조 추정한다.
            }
        }
        return classifyRegionByKeywordPolicy(summary)
    }

    private fun classifyRegionByKeywordPolicy(summary: BatchSummary): TrendRegionType {
        val globalKeywords = globalFallbackKeywords
        val domesticKeywords = domesticFallbackKeywords
        val haystack = buildString {
            append(summary.originalTitle)
            append('\n')
            append(summary.translatedTitle ?: "")
            append('\n')
            append(summary.summary)
            append('\n')
            append(summary.keywords.joinToString(" "))
        }.lowercase(Locale.ROOT)

        val global = globalKeywords.any { haystack.contains(it.lowercase(Locale.ROOT)) }
        val domestic = domesticKeywords.any { haystack.contains(it.lowercase(Locale.ROOT)) }
        return when {
            domestic && !global -> TrendRegionType.DOMESTIC
            global && !domestic -> TrendRegionType.GLOBAL
            else -> TrendRegionType.ALL
        }
    }

    private fun computeTopKeywords(summaries: List<BatchSummary>, limit: Int): List<String> =
        summaries
            .flatMap { it.keywords }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { it.key }

    private fun buildTitle(
        periodType: TrendPeriodType,
        categoryName: String,
        regionType: TrendRegionType,
        from: LocalDate,
        to: LocalDate
    ): String {
        val periodLabel = if (periodType == TrendPeriodType.WEEKLY) "주간" else "월간"
        val regionLabel = when (regionType) {
            TrendRegionType.ALL -> "전체"
            TrendRegionType.GLOBAL -> "글로벌"
            TrendRegionType.DOMESTIC -> "국내"
        }
        return "$periodLabel 트렌드 | $categoryName | $regionLabel | $from ~ $to"
    }

    private fun buildSnapshotSummary(
        periodType: TrendPeriodType,
        regionType: TrendRegionType,
        summaries: List<BatchSummary>,
        topItems: List<BatchSummary>,
        topKeywords: List<String>
    ): String {
        if (summaries.isEmpty()) {
            return "선택한 기간/조건에 해당하는 데이터가 없어 스냅샷이 비어 있습니다."
        }
        val periodLabel = if (periodType == TrendPeriodType.WEEKLY) "지난 7일" else "지난 30일"
        val regionLabel = when (regionType) {
            TrendRegionType.ALL -> "전체"
            TrendRegionType.GLOBAL -> "글로벌"
            TrendRegionType.DOMESTIC -> "국내"
        }
        val topTitles = topItems.take(3).map {
            it.translatedTitle?.takeIf { t -> t.isNotBlank() } ?: it.originalTitle
        }
        val keywordText = if (topKeywords.isEmpty()) "주요 키워드 없음" else topKeywords.joinToString(", ")
        return buildString {
            append("$periodLabel $regionLabel 관점에서 총 ${summaries.size}건을 분석했습니다.")
            append(" 핵심 신호는 $keywordText 입니다.")
            if (topTitles.isNotEmpty()) {
                append(" 중요 항목 예시는 ${topTitles.joinToString(" / ")} 입니다.")
            }
        }
    }

    private fun buildActionItems(
        periodType: TrendPeriodType,
        regionType: TrendRegionType,
        topKeywords: List<String>
    ): List<String> {
        val cadence = if (periodType == TrendPeriodType.WEEKLY) "이번 주" else "이번 달"
        val regionLabel = when (regionType) {
            TrendRegionType.ALL -> "전체"
            TrendRegionType.GLOBAL -> "글로벌"
            TrendRegionType.DOMESTIC -> "국내"
        }
        val key = topKeywords.firstOrNull() ?: "핵심 키워드"
        return listOf(
            "$cadence $regionLabel 트렌드 회의에서 '$key' 관련 과제를 우선 점검합니다.",
            "관련 소스 노이즈 여부를 검토함/피드백 지표와 함께 확인합니다.",
            "다음 발행 전 페르소나 문체와 카드 구성(요약/시사점)을 재점검합니다."
        )
    }

    private fun buildPanels(snapshot: TrendSnapshot, panelCount: Int): List<String> {
        val seeds = (snapshot.keySignals + snapshot.actionItems).filter { it.isNotBlank() }
        if (seeds.isEmpty()) {
            return List(panelCount) { index -> "${index + 1}컷: ${snapshot.categoryName} 트렌드 핵심 포인트 정리" }
        }
        return (0 until panelCount).map { index ->
            val seed = seeds[index % seeds.size]
            "${index + 1}컷: $seed"
        }
    }

    private fun parsePeriodType(raw: String): TrendPeriodType {
        val normalized = raw.trim().uppercase(Locale.ROOT)
        return try {
            TrendPeriodType.valueOf(normalized)
        } catch (_: IllegalArgumentException) {
            throw InvalidInputException("Unsupported periodType: $raw")
        }
    }

    private fun parseRegionType(raw: String?): TrendRegionType {
        val normalized = raw?.trim()?.uppercase(Locale.ROOT).orEmpty().ifBlank { "ALL" }
        return try {
            TrendRegionType.valueOf(normalized)
        } catch (_: IllegalArgumentException) {
            throw InvalidInputException("Unsupported regionType: $raw")
        }
    }

    private fun parseSnapshotStatus(raw: String): TrendSnapshotStatus {
        val normalized = raw.trim().uppercase(Locale.ROOT)
        return try {
            TrendSnapshotStatus.valueOf(normalized)
        } catch (_: IllegalArgumentException) {
            throw InvalidInputException("Unsupported snapshot status: $raw")
        }
    }

    private fun parseCardType(raw: String): TrendVisualCardType {
        val normalized = raw.trim().uppercase(Locale.ROOT)
        return try {
            TrendVisualCardType.valueOf(normalized)
        } catch (_: IllegalArgumentException) {
            throw InvalidInputException("Unsupported cardType: $raw")
        }
    }

    private fun parseReviewStatus(raw: String): TrendVisualReviewStatus {
        val normalized = raw.trim().uppercase(Locale.ROOT)
        return try {
            TrendVisualReviewStatus.valueOf(normalized)
        } catch (_: IllegalArgumentException) {
            throw InvalidInputException("Unsupported reviewStatus: $raw")
        }
    }

    private fun cardTypeLabel(type: TrendVisualCardType): String = when (type) {
        TrendVisualCardType.INFO_CARD -> "인포카드"
        TrendVisualCardType.COMIC_4 -> "4컷"
        TrendVisualCardType.COMIC_8 -> "8컷"
    }

    private fun parseHost(url: String): String? = try {
        URI(url).host?.lowercase(Locale.ROOT)
    } catch (_: Exception) {
        null
    }

    private fun TrendSnapshot.toResult() = TrendSnapshotResult(
        id = id,
        periodType = periodType,
        snapshotFrom = snapshotFrom,
        snapshotTo = snapshotTo,
        categoryId = categoryId,
        categoryName = categoryName,
        regionType = regionType,
        title = title,
        summary = summary,
        keySignals = keySignals,
        actionItems = actionItems,
        sourceCount = sourceCount,
        itemCount = itemCount,
        status = status,
        templateType = templateType,
        generatedBy = generatedBy,
        publishedAt = publishedAt,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun TrendVisualCard.toResult() = TrendVisualCardResult(
        id = id,
        snapshotId = snapshotId,
        cardType = cardType,
        title = title,
        summary = summary,
        panels = panels,
        reviewStatus = reviewStatus,
        reviewNote = reviewNote,
        generatedBy = generatedBy,
        reviewedBy = reviewedBy,
        reviewedAt = reviewedAt,
        published = published,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private data class DateRange(
        val from: LocalDate,
        val to: LocalDate
    )
}

data class TrendSnapshotResult(
    val id: String,
    val periodType: TrendPeriodType,
    val snapshotFrom: LocalDate,
    val snapshotTo: LocalDate,
    val categoryId: String?,
    val categoryName: String,
    val regionType: TrendRegionType,
    val title: String,
    val summary: String,
    val keySignals: List<String>,
    val actionItems: List<String>,
    val sourceCount: Int,
    val itemCount: Int,
    val status: TrendSnapshotStatus,
    val templateType: String,
    val generatedBy: String?,
    val publishedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant
)

data class TrendVisualCardResult(
    val id: String,
    val snapshotId: String,
    val cardType: TrendVisualCardType,
    val title: String,
    val summary: String,
    val panels: List<String>,
    val reviewStatus: TrendVisualReviewStatus,
    val reviewNote: String?,
    val generatedBy: String?,
    val reviewedBy: String?,
    val reviewedAt: Instant?,
    val published: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant
)

data class ReportReleaseResult(
    val summaryId: String,
    val categoryId: String,
    val title: String,
    val sourceLink: String,
    val importanceScore: Float,
    val releaseType: String,
    val detectionReason: String,
    val createdAt: Instant
)

data class QualitySummaryResult(
    val from: Instant,
    val to: Instant,
    val days: Int,
    val itemsCollected: Int,
    val itemsSummarized: Int,
    val itemsSent: Int,
    val reviewPendingCount: Int,
    val reviewPendingRate: Double,
    val excludeRate: Double,
    val feedbackTotal: Int,
    val feedbackPositiveRate: Double,
    val sendSuccessRate: Double,
    val recommendations: List<String>
)
