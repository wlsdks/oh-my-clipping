package com.ohmyclipping.service

import com.ohmyclipping.error.InvalidStateException
import com.ohmyclipping.error.NotFoundException
import com.ohmyclipping.error.ensureValid
import com.ohmyclipping.model.*
import com.ohmyclipping.service.dto.clipping.*
import com.ohmyclipping.service.dto.CategoryOverview
import com.ohmyclipping.service.digest.DigestService
import com.ohmyclipping.service.port.LlmSummarizationPort
import com.ohmyclipping.store.*
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

private val log = KotlinLogging.logger {}

/**
 * 클리핑 파이프라인과 조회 유스케이스를 조정하는 애플리케이션 서비스.
 * 수집(CollectionService), 다이제스트(DigestService), 데이터 정리(DataLifecycleService)에
 * 위임하고, 요약/조회 등 나머지 조정 메서드만 직접 수행한다.
 */
@Service
class ClippingService(
    private val collectionService: CollectionService,
    private val digestService: DigestService,
    private val dataLifecycleService: DataLifecycleService,
    private val categoryStore: CategoryStore,
    private val summaryStore: BatchSummaryStore,
    private val summaryDeliveryStore: SummaryDeliveryStore = summaryStore,
    private val categoryOverviewStatsStore: CategoryOverviewStatsStore,
    private val summarySearchStore: SummarySearchStore,
    private val dailySummaryStore: DailySummaryStore,
    private val originalContentStore: OriginalContentStore,
    private val personaStore: PersonaStore,
    private val itemStore: RssItemStore,
    @param:Lazy private val summarizer: LlmSummarizationPort,
    private val statsService: StatsService,
    private val runtimeSettingService: RuntimeSettingService,
    private val itemSummarizationService: ItemSummarizationService
) {

    /** LLM 동시 호출 수를 제한하는 코루틴 세마포어 (최대 10 병렬) */
    private val llmParallelSemaphore = Semaphore(LLM_MAX_PARALLELISM)

    companion object {
        const val LLM_MAX_PARALLELISM = 10
        /** 배치 내 실패 비율이 이 값을 넘으면 해당 카테고리 요약을 중단한다 (80%) */
        const val BATCH_FAILURE_THRESHOLD = 0.8
        private const val CONTENT_PREVIEW_LENGTH = 300
    }

    // -- delegation to CollectionService --

    /** RSS 피드에서 아이템을 수집한다. CollectionService에 위임. */
    fun collect(categoryId: String?, hoursBack: Int?): CollectResult =
        collectionService.collect(categoryId, hoursBack)

    /** 개별 URL을 수동으로 추가한다. CollectionService에 위임. */
    fun addUrl(categoryId: String, rawUrl: String): AddUrlResult =
        collectionService.addUrl(categoryId, rawUrl)

    // -- delegation to DigestService --

    /** 다이제스트를 생성하고 Slack으로 전송한다. DigestService에 위임. */
    fun digest(
        categoryId: String,
        maxItems: Int?,
        unsentOnly: Boolean?,
        sendToSlack: Boolean?,
        slackChannelId: String?
    ): DigestResult = digestService.digest(categoryId, maxItems, unsentOnly, sendToSlack, slackChannelId)

    /** 이미 선정된 다이제스트 스냅샷을 특정 Slack 목적지로 전송한다. DigestService에 위임. */
    fun sendPreparedDigest(
        categoryId: String,
        preparedDigest: DigestResult,
        slackChannelId: String,
        categoryNameOverride: String? = null
    ): DigestResult = digestService.sendPreparedDigest(categoryId, preparedDigest, slackChannelId, categoryNameOverride)

    /** 이미 전송된 prepared digest의 후처리만 다시 수행한다. DigestService에 위임. */
    fun finalizePreparedDigest(categoryId: String, preparedDigest: DigestResult): Int =
        digestService.finalizePreparedDigest(categoryId, preparedDigest)

    // -- delegation to DataLifecycleService --

    /** 데이터 보존 정책을 설정한다. DataLifecycleService에 위임. */
    fun setRetentionPolicy(categoryId: String, keepDays: Int, enabled: Boolean?): RetentionPolicyResult =
        dataLifecycleService.setRetentionPolicy(categoryId, keepDays, enabled)

    /** 보존 기간이 지난 데이터를 삭제한다. DataLifecycleService에 위임. */
    fun purge(categoryId: String?, keepDays: Int?, dryRun: Boolean?): PurgeResult =
        dataLifecycleService.purge(categoryId, keepDays, dryRun)

    /** 요약 데이터를 내보낸다. DataLifecycleService에 위임. */
    fun exportSummaries(
        categoryId: String,
        daysBack: Int?,
        includeOriginal: Boolean?,
        limit: Int?
    ): ExportResult = dataLifecycleService.exportSummaries(categoryId, daysBack, includeOriginal, limit)

    // -- summarization (remains here) --

    /**
     * 지정된 카테고리의 미처리 아이템을 요약한다.
     * Dispatchers.IO 위에서 코루틴 세마포어로 동시 LLM 호출을 제한한다.
     */
    fun summarize(categoryId: String?): SummarizeResult {
        val categories = resolveCategories(categoryId)
        ensureValid(categories.isNotEmpty()) { "No categories found" }

        var totalSummarized = 0
        val categoryResults = mutableListOf<SummarizeCategoryResult>()

        for (category in categories) {
            val persona = category.personaId?.let { personaStore.findById(it) }
            var catSummarized = 0
            val allKeywords = mutableListOf<String>()
            var totalScore = 0f

            // 배치 단위로 미처리 아이템 ID를 조회하여 코루틴 병렬로 처리
            var itemIds = itemStore.findUnprocessedIds(category.id, limit = 100)
            var batchFailureRatio = 0.0
            while (itemIds.isNotEmpty()) {
                // 배치 내 아이템을 Dispatchers.IO 위에서 최대 10 병렬로 동시 요약 처리
                val results = runBlocking(Dispatchers.IO) {
                    summarizeBatchParallel(itemIds, category, persona)
                }

                var batchFailures = 0
                for (result in results) {
                    if (result.success && !result.skippedByScreening) {
                        catSummarized++
                        allKeywords.addAll(result.keywords)
                        totalScore += result.importanceScore
                    } else if (!result.success) {
                        batchFailures++
                    }
                }

                batchFailureRatio = if (results.isNotEmpty()) {
                    batchFailures.toDouble() / results.size
                } else {
                    0.0
                }
                if (batchFailureRatio >= BATCH_FAILURE_THRESHOLD) {
                    log.warn {
                        "High failure ratio (${"%.0f".format(batchFailureRatio * 100)}%) " +
                            "for '${category.name}' — stopping batch"
                    }
                    break
                }
                itemIds = itemStore.findUnprocessedIds(category.id, limit = 100)
            }

            totalSummarized += catSummarized
            categoryResults.add(SummarizeCategoryResult(category.id, category.name, catSummarized))
            if (catSummarized > 0) {
                val avgScore = totalScore / catSummarized
                statsService.recordSummarization(
                    category.id, catSummarized, allKeywords.distinct().take(10), avgScore
                )
            }
            log.info { "Summarized $catSummarized items for category '${category.name}'" }
        }

        return SummarizeResult(totalSummarized, categoryResults)
    }

    /**
     * 배치 내 아이템을 코루틴 병렬로 요약 처리한다.
     * Dispatchers.IO 위에서 세마포어로 최대 동시 LLM 호출 수를 제한하여 API rate limit을 방지한다.
     */
    private suspend fun summarizeBatchParallel(
        itemIds: List<String>,
        category: Category,
        persona: Persona?
    ): List<ItemSummarizationResult> = coroutineScope {
        itemIds.map { itemId ->
            async(Dispatchers.IO) {
                llmParallelSemaphore.withPermit {
                    itemSummarizationService.summarizeSingleItem(category, itemId, persona)
                }
            }
        }.map { it.await() }
    }

    // -- query methods --

    /**
     * 일일 요약을 생성한다.
     */
    fun generateDailySummary(categoryId: String): DailySummaryResult {
        val category = categoryStore.findById(categoryId)
            ?: throw NotFoundException("Category not found: $categoryId")

        val today = LocalDate.now(ZoneId.of("Asia/Seoul"))
        val existing = dailySummaryStore.findByCategoryAndDate(categoryId, today)
        if (existing != null) return existing.toResult()

        val batchSummaries = summaryStore.findByCategoryId(categoryId)
        ensureValid(batchSummaries.isNotEmpty()) { "No summaries available for daily digest" }

        val topSummaries = batchSummaries
            .sortedByDescending { it.importanceScore }
            .take(category.maxItems)

        val summariesText = topSummaries.joinToString("\n\n") { bs ->
            val title = bs.translatedTitle ?: bs.originalTitle
            "- $title\n  ${bs.summary}\n  원문: ${bs.sourceLink}"
        }

        // AI로 일일 요약을 생성한다
        val aiResult = summarizer.generateDailySummary(category.name, summariesText, topSummaries.size)
            ?: throw InvalidStateException("Failed to generate daily summary via AI")

        val saved = dailySummaryStore.save(
            DailySummary(
                id = "",
                title = aiResult.title,
                totalItems = topSummaries.size,
                summaryDate = today,
                topicKeywords = aiResult.topicKeywords,
                categoryId = categoryId
            )
        )
        return saved.toResult()
    }

    /** 요약 목록을 조회한다 */
    @Transactional(readOnly = true)
    fun getSummaries(categoryId: String, unsentOnly: Boolean): SummaryListResult {
        val category = categoryStore.findById(categoryId)
            ?: throw NotFoundException("Category not found: $categoryId")

        val summaries = if (unsentOnly) {
            summaryDeliveryStore.findUnsent(categoryId)
        } else {
            summaryStore.findByCategoryId(categoryId)
        }

        val topSummaries = summaries
            .sortedByDescending { it.importanceScore }
            .take(category.maxItems)

        return SummaryListResult(
            summaries = topSummaries.map { it.toInfo() },
            totalCount = summaries.size
        )
    }

    /**
     * 전체 카테고리에 걸친 최근 요약을 반환한다.
     *
     * 사용자가 "오늘 뉴스 다 보여줘" 처럼 카테고리를 지정하지 않을 때 사용한다. 기존
     * [getSummaries] 는 카테고리 필수지만 이 메서드는 [BatchSummaryStore.findByDateRange]
     * 의 `categoryId = null` 경로로 위임해 크로스 카테고리 최신순 결과를 반환한다.
     */
    @Transactional(readOnly = true)
    fun listRecentAcrossCategories(sinceDays: Int, limit: Int): SummaryListResult {
        ensureValid(sinceDays in 1..30) { "sinceDays must be between 1 and 30" }
        val safeLimit = limit.coerceIn(1, 100)
        val cutoff = Instant.now().minus(sinceDays.toLong(), ChronoUnit.DAYS)
        // Store 가 SQL LIMIT 을 적용하므로 30일 전체 full-scan 을 피한다.
        val summaries = summaryStore
            .findByDateRange(cutoff, Instant.now(), categoryId = null, limit = safeLimit)
            .map { it.toInfo() }
        return SummaryListResult(
            summaries = summaries,
            totalCount = summaries.size,
        )
    }

    /**
     * 특정 카테고리의 최근 요약을 "최신순" 으로 반환한다.
     *
     * [getSummaries] 와의 차이:
     * - `getSummaries` 는 **중요도순** 으로 `category.maxItems` 만큼 먼저 자른 뒤 반환 —
     *   briefing/recent 관점에서는 "최신 저중요도 기사가 window 밖으로 잘려나가는" 문제가 있다.
     * - 이 메서드는 [BatchSummaryStore.findByDateRange] 의 카테고리 지정 경로로 위임해
     *   cutoff 내 결과를 created_at DESC 로 받은 뒤 limit 만 적용한다. 중요도와 무관하게
     *   시간 범위 내 최근 N개를 보장하므로 briefing/recent UX 에 더 적합하다.
     */
    @Transactional(readOnly = true)
    fun listRecentForCategory(categoryId: String, sinceDays: Int, limit: Int): SummaryListResult {
        ensureValid(sinceDays in 1..30) { "sinceDays must be between 1 and 30" }
        // 카테고리 존재 여부를 먼저 확인해 silent empty 가 아닌 NotFoundException 으로 실패시킨다.
        categoryStore.findById(categoryId)
            ?: throw NotFoundException("Category not found: $categoryId")
        val safeLimit = limit.coerceIn(1, 100)
        val cutoff = Instant.now().minus(sinceDays.toLong(), ChronoUnit.DAYS)
        val summaries = summaryStore
            .findByDateRange(cutoff, Instant.now(), categoryId = categoryId, limit = safeLimit)
            .map { it.toInfo() }
        return SummaryListResult(
            summaries = summaries,
            totalCount = summaries.size,
        )
    }

    /**
     * 여러 카테고리의 최근 요약을 카테고리별 top-N 으로 **단일 쿼리** 에 묶어 반환한다.
     *
     * briefing 도구가 구독 카테고리마다 [listRecentForCategory] 를 호출하던 N+1 경로를
     * [BatchSummaryStore.findByCategoryIdsAndDateRange] 의 윈도 함수 쿼리로 1회에 해결한다.
     *
     * @return categoryId → 해당 카테고리의 최근 요약 리스트 (created_at DESC, 최대 limitPerCategory).
     *   요청한 categoryId 중 window 안에 요약이 없는 카테고리는 **맵에서 키 자체가 누락** 된다 —
     *   호출자는 필요하면 원래 요청 ID 기준으로 GET-or-empty 로 섹션을 채운다.
     */
    @Transactional(readOnly = true)
    fun listRecentForCategories(
        categoryIds: List<String>,
        sinceDays: Int,
        limitPerCategory: Int,
    ): Map<String, List<SummaryInfo>> {
        ensureValid(sinceDays in 1..30) { "sinceDays must be between 1 and 30" }
        if (categoryIds.isEmpty()) return emptyMap()
        val cutoff = Instant.now().minus(sinceDays.toLong(), ChronoUnit.DAYS)
        val rows = summaryStore.findByCategoryIdsAndDateRange(
            categoryIds = categoryIds,
            from = cutoff,
            to = Instant.now(),
            limitPerCategory = limitPerCategory,
        )
        return rows
            .groupBy { it.categoryId }
            .mapValues { (_, list) -> list.map { it.toInfo() } }
    }

    /** 요약에 sent 마킹을 적용한다 */
    fun markSent(summaryIds: List<String>) {
        ensureValid(summaryIds.isNotEmpty()) { "No summary IDs provided" }
        summaryDeliveryStore.markSent(summaryIds)
    }

    /** 요약을 키워드로 검색한다 */
    @Transactional(readOnly = true)
    fun searchSummaries(categoryId: String, query: String, limit: Int): SummaryListResult {
        ensureValid(query.isNotBlank()) { "Query is required" }

        categoryStore.findById(categoryId)
            ?: throw NotFoundException("Category not found: $categoryId")

        val summaries = summarySearchStore.search(categoryId, query, limit.coerceIn(1, 100))
        return SummaryListResult(
            summaries = summaries.map { it.toInfo() },
            totalCount = summaries.size
        )
    }

    /** 원본 콘텐츠를 조회한다 */
    @Transactional(readOnly = true)
    fun getOriginalContent(sourceLink: String): OriginalContentResult {
        ensureValid(sourceLink.isNotBlank()) { "Source link is required" }
        val original = originalContentStore.findBySourceLink(sourceLink.trim())
            ?: throw NotFoundException("Original content not found for source link")

        return OriginalContentResult(
            found = true,
            sourceLink = original.sourceLink,
            title = original.title,
            markdown = original.markdown,
            rssItemId = original.rssItemId,
            archivedAt = original.updatedAt.toString()
        )
    }

    // -- MCP extended query methods --

    /**
     * 크로스카테고리 + 날짜 범위 요약 검색.
     * categoryId가 null이면 전체 카테고리를 대상으로 검색한다.
     *
     * @param categoryId 카테고리 ID (null이면 전체)
     * @param query 검색어 (빈 문자열 불가)
     * @param fromDate 시작 일자 (null이면 제한 없음)
     * @param toDate 종료 일자 (null이면 제한 없음)
     * @param limit 최대 결과 수 (1~100)
     */
    @Transactional(readOnly = true)
    fun searchSummaries(
        categoryId: String?,
        query: String,
        fromDate: LocalDate? = null,
        toDate: LocalDate? = null,
        limit: Int = 10,
    ): SummaryListResult {
        ensureValid(query.isNotBlank()) { "Query is required" }
        val safeLimit = limit.coerceIn(1, 100)

        // 날짜 범위가 지정된 경우 날짜 + 키워드 조건을 함께 DB로 내려 false negative를 막는다.
        val summaries = if (fromDate != null || toDate != null) {
            val from = fromDate ?: LocalDate.of(2020, 1, 1)
            val to = toDate ?: LocalDate.now(ZoneId.of("Asia/Seoul"))
            summarySearchStore.searchInDateRange(categoryId, query, from, to, safeLimit)
        } else if (categoryId != null) {
            // 카테고리 지정 검색은 기존 메서드에 위임
            summarySearchStore.search(categoryId, query, safeLimit)
        } else {
            // 전체 카테고리 검색
            summarySearchStore.searchAcrossCategories(query, safeLimit)
        }

        return SummaryListResult(
            summaries = summaries.map { it.toInfo() },
            totalCount = summaries.size,
        )
    }

    /**
     * 요약 단건 상세를 조회한다.
     * 키워드, 원본 콘텐츠 프리뷰(300자)를 포함한다.
     *
     * @param summaryId 요약 ID
     * @throws NotFoundException 요약이 존재하지 않을 때
     */
    @Transactional(readOnly = true)
    fun getSummaryDetail(summaryId: String): SummaryDetailResult {
        val summary = summaryStore.findById(summaryId)
            ?: throw NotFoundException("Summary not found: $summaryId")

        val category = categoryStore.findById(summary.categoryId)

        // 원본 콘텐츠에서 프리뷰를 추출한다 (최대 300자)
        val originalPreview = runCatching {
            originalContentStore.findBySourceLink(summary.sourceLink)
                ?.markdown?.take(CONTENT_PREVIEW_LENGTH)
        }.getOrNull()

        return SummaryDetailResult(
            id = summary.id,
            categoryId = summary.categoryId,
            categoryName = category?.name ?: "",
            originalTitle = summary.originalTitle,
            translatedTitle = summary.translatedTitle,
            summary = summary.summary,
            insights = summary.insights,
            keywords = summary.keywords,
            importanceScore = summary.importanceScore,
            sourceLink = summary.sourceLink,
            sentiment = summary.sentiment,
            eventType = summary.eventType,
            isSentToSlack = summary.isSentToSlack,
            contentPreview = originalPreview,
            createdAt = summary.createdAt.toString(),
        )
    }

    /**
     * 카테고리 내 중요도 상위 요약을 조회한다.
     *
     * @param categoryId 카테고리 ID
     * @param days 최근 N일 범위
     * @param minScore 최소 중요도 점수
     * @param limit 최대 결과 수 (1~100)
     */
    @Transactional(readOnly = true)
    fun listTopSummaries(
        categoryId: String,
        days: Int,
        minScore: Double,
        limit: Int,
    ): SummaryListResult {
        categoryStore.findById(categoryId)
            ?: throw NotFoundException("Category not found: $categoryId")

        val sinceDate = LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(days.toLong())
        val safeLimit = limit.coerceIn(1, 100)

        val summaries = summarySearchStore.findByImportanceScoreGreaterThan(
            categoryId, minScore, sinceDate, safeLimit,
        )
        return SummaryListResult(
            summaries = summaries.map { it.toInfo() },
            totalCount = summaries.size,
        )
    }

    /**
     * 카테고리 개요 통계를 조회한다.
     * 소스 수, 구독자 수, 최근 7일 기사 수, 평균 중요도를 단일 쿼리로 가져온다.
     *
     * @param categoryId 카테고리 ID
     * @throws NotFoundException 카테고리가 존재하지 않을 때
     */
    @Transactional(readOnly = true)
    fun getCategoryOverview(categoryId: String): CategoryOverview {
        val category = categoryStore.findById(categoryId)
            ?: throw NotFoundException("Category not found: $categoryId")

        val since7Days = LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(7)
        val stats = categoryOverviewStatsStore.fetchCategoryOverviewStats(categoryId, since7Days)

        return CategoryOverview(
            id = category.id,
            name = category.name,
            sourceCount = stats?.sourceCount ?: 0,
            subscriberCount = stats?.subscriberCount ?: 0,
            recentItemCount7Days = stats?.recentItemCount7Days ?: 0,
            avgImportance7Days = stats?.avgImportance7Days ?: 0.0,
            lastUpdatedAt = stats?.lastUpdatedAt?.toString(),
        )
    }

    // -- private helpers --

    private fun resolveCategories(categoryId: String?): List<Category> =
        if (categoryId != null) {
            listOfNotNull(categoryStore.findById(categoryId))
        } else {
            categoryStore.findOperational()
        }

    private fun DailySummary.toResult() = DailySummaryResult(
        id = id,
        title = title,
        totalItems = totalItems,
        summaryDate = summaryDate.toString(),
        topicKeywords = topicKeywords,
        categoryId = categoryId
    )

    private fun BatchSummary.toInfo() = SummaryInfo(
        id = id,
        originalTitle = originalTitle,
        translatedTitle = translatedTitle,
        summary = summary,
        keywords = keywords,
        importanceScore = importanceScore,
        sourceLink = sourceLink,
        isSentToSlack = isSentToSlack,
        categoryId = categoryId,
        createdAt = createdAt.toString()
    )
}
