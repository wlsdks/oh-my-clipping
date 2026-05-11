package com.clipping.mcpserver.service

import com.clipping.mcpserver.config.ClippingMcpServerProperties
import com.clipping.mcpserver.error.NotFoundException
import com.clipping.mcpserver.error.ensureValid
import com.clipping.mcpserver.model.*
import com.clipping.mcpserver.service.dto.clipping.*
import com.clipping.mcpserver.store.*
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.net.URI
import java.net.URISyntaxException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale

private val log = KotlinLogging.logger {}

/**
 * 데이터 보존 정책, 정리(purge), 요약 내보내기를 담당하는 서비스.
 * 카테고리별 보존 기간 설정, 오래된 데이터 삭제, 요약 데이터 CSV/JSON 내보내기를 수행한다.
 */
@Service
class DataLifecycleService(
    private val categoryStore: CategoryStore,
    private val summaryStore: BatchSummaryStore,
    private val summaryRetentionStore: SummaryRetentionStore,
    private val itemStore: RssItemStore,
    private val dailySummaryStore: DailySummaryStore,
    private val originalContentStore: OriginalContentStore,
    private val retentionPolicyStore: RetentionPolicyStore,
    private val statsStore: StatsStore,
    private val sourceStore: RssSourceStore,
    private val properties: ClippingMcpServerProperties
) {

    /**
     * 카테고리별 데이터 보존 기간을 설정한다.
     *
     * @param categoryId 대상 카테고리 ID
     * @param keepDays 보존 기간(일), 0보다 커야 한다
     * @param enabled 정책 활성화 여부 (null이면 true)
     * @return 저장된 보존 정책 결과
     */
    fun setRetentionPolicy(categoryId: String, keepDays: Int, enabled: Boolean?): RetentionPolicyResult {
        ensureValid(keepDays > 0) { "keepDays must be greater than 0" }
        categoryStore.findById(categoryId)
            ?: throw NotFoundException("Category not found: $categoryId")

        val saved = retentionPolicyStore.saveOrUpdate(
            RetentionPolicy(
                id = "",
                categoryId = categoryId,
                keepDays = keepDays,
                isEnabled = enabled ?: true
            )
        )
        return RetentionPolicyResult(
            categoryId = saved.categoryId,
            keepDays = saved.keepDays,
            isEnabled = saved.isEnabled,
            source = "category_policy"
        )
    }

    /**
     * 보존 기간이 지난 데이터를 삭제한다.
     * dryRun이 true이면 실제 삭제 없이 삭제 대상 건수만 반환한다.
     *
     * @param categoryId 특정 카테고리만 정리할 경우 ID (null이면 전체)
     * @param keepDays 보존 기간 오버라이드 (null이면 카테고리 정책 또는 기본값)
     * @param dryRun 시뮬레이션 모드 (기본: true)
     * @return 삭제 결과
     */
    @Transactional
    fun purge(categoryId: String?, keepDays: Int?, dryRun: Boolean?): PurgeResult {
        if (categoryId != null) {
            categoryStore.findById(categoryId)
                ?: throw NotFoundException("Category not found: $categoryId")
        }
        if (keepDays != null) {
            ensureValid(keepDays > 0) { "keepDays must be greater than 0" }
        }

        val effectiveKeepDays = resolveKeepDays(categoryId, keepDays)
        val cutoffDate = LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(effectiveKeepDays.toLong())
        val cutoffInstant = cutoffDate.atStartOfDay().toInstant(java.time.ZoneOffset.UTC)
        val isDryRun = dryRun ?: true

        // 삭제 대상 건수를 먼저 카운트한다
        val candidateSummaries = summaryRetentionStore.countByItemOlderThan(cutoffInstant, categoryId)
        val candidateItems = itemStore.countOlderThan(cutoffInstant, categoryId)
        val candidateOriginals = originalContentStore.countByItemOlderThan(cutoffInstant, categoryId)
        val candidateDaily = dailySummaryStore.countOlderThan(cutoffDate, categoryId)
        val candidateStats = statsStore.countOlderThan(cutoffDate, categoryId)

        if (isDryRun) {
            return PurgeResult(
                dryRun = true,
                categoryId = categoryId,
                keepDays = effectiveKeepDays,
                cutoffDate = cutoffDate.toString(),
                deletedSummaries = candidateSummaries,
                deletedItems = candidateItems,
                deletedOriginals = candidateOriginals,
                deletedDailySummaries = candidateDaily,
                deletedStats = candidateStats
            )
        }

        // 실제 삭제를 수행한다
        val deletedSummaries = summaryRetentionStore.deleteByItemOlderThan(cutoffInstant, categoryId)
        val deletedItems = itemStore.deleteOlderThan(cutoffInstant, categoryId)
        val deletedDaily = dailySummaryStore.deleteOlderThan(cutoffDate, categoryId)
        val deletedStats = statsStore.deleteOlderThan(cutoffDate, categoryId)

        return PurgeResult(
            dryRun = false,
            categoryId = categoryId,
            keepDays = effectiveKeepDays,
            cutoffDate = cutoffDate.toString(),
            deletedSummaries = deletedSummaries,
            deletedItems = deletedItems,
            // original_contents는 rss_items FK cascade로 함께 정리되므로 사전 count를 결과에 반영한다
            deletedOriginals = candidateOriginals,
            deletedDailySummaries = deletedDaily,
            deletedStats = deletedStats
        )
    }

    /**
     * 카테고리별 요약 데이터를 내보낸다.
     * includeOriginal이 true이면 원본 콘텐츠 마크다운도 포함한다.
     * N+1 문제를 방지하기 위해 원본 콘텐츠를 일괄 조회한다.
     *
     * @param categoryId 대상 카테고리 ID
     * @param daysBack 조회 기간(일), null이면 전체
     * @param includeOriginal 원본 마크다운 포함 여부 (기본: false)
     * @param limit 최대 레코드 수 (기본: 100, 최대: 500)
     * @return 내보내기 결과
     */
    @Transactional(readOnly = true)
    fun exportSummaries(
        categoryId: String,
        daysBack: Int?,
        includeOriginal: Boolean?,
        limit: Int?
    ): ExportResult {
        categoryStore.findById(categoryId)
            ?: throw NotFoundException("Category not found: $categoryId")
        if (daysBack != null) {
            ensureValid(daysBack > 0) { "daysBack must be greater than 0" }
        }

        val safeLimit = (limit ?: 100).coerceIn(1, 500)
        val includeOriginalValue = includeOriginal ?: false
        val since = daysBack?.let { Instant.now().minus(it.toLong(), ChronoUnit.DAYS) }
        val from = since ?: Instant.EPOCH

        // 기간과 limit을 DB에 적용해 카테고리 전체 요약을 JVM으로 가져오지 않는다.
        val summaries = summaryStore.findByDateRange(
            from = from,
            to = Instant.now(),
            categoryId = categoryId,
            limit = safeLimit
        )

        val resolveOriginalContent = if (includeOriginalValue) {
            buildOriginalContentResolver(summaries)
        } else {
            { _: BatchSummary -> null }
        }

        val records = summaries.map { summary ->
            val title = summary.translatedTitle?.takeIf { it.isNotBlank() } ?: summary.originalTitle
            ExportRecord(
                summaryId = summary.id,
                title = title,
                summary = summary.summary,
                keywords = summary.keywords,
                importanceScore = summary.importanceScore,
                sourceLink = summary.sourceLink,
                createdAt = summary.createdAt.toString(),
                originalMarkdown = if (includeOriginalValue &&
                    isFulltextAllowed(categoryId, summary.sourceLink)
                ) {
                    resolveOriginalContent(summary)?.markdown
                } else {
                    null
                }
            )
        }

        return ExportResult(
            categoryId = categoryId,
            exportedAt = Instant.now().toString(),
            daysBack = daysBack,
            includeOriginal = includeOriginalValue,
            count = records.size,
            records = records
        )
    }

    // -- private helpers --

    /**
     * export 대상 요약에 필요한 원본 콘텐츠 조회 함수를 만든다.
     * rss_item_id 우선, retention으로 연결이 끊긴 요약은 source_link fallback을 사용한다.
     */
    private fun buildOriginalContentResolver(summaries: List<BatchSummary>): (BatchSummary) -> OriginalContent? {
        if (summaries.isEmpty()) return { null }
        // 원본 포함 export는 최대 500건이므로 두 번의 IN 조회로 충분히 제한된다.
        val byRssItemId = originalContentStore.findByRssItemIds(summaries.mapNotNull { it.rssItemId })
        val sourceLinksNeedingFallback = summaries
            .asSequence()
            .filter { summary -> summary.rssItemId == null || byRssItemId[summary.rssItemId] == null }
            .map { it.sourceLink }
            .toList()
        val bySourceLink = originalContentStore.findBySourceLinks(sourceLinksNeedingFallback)
        return { summary -> summary.rssItemId?.let { byRssItemId[it] } ?: bySourceLink[summary.sourceLink] }
    }

    private fun resolveKeepDays(categoryId: String?, keepDays: Int?): Int {
        if (keepDays != null) {
            return keepDays
        }
        if (categoryId != null) {
            val policy = retentionPolicyStore.findByCategoryId(categoryId)
            if (policy != null && policy.isEnabled) {
                return policy.keepDays
            }
        }
        return properties.retentionDefaultDays.coerceAtLeast(1)
    }

    /** 소스 도메인에 대해 fulltext 내보내기가 허용되는지 확인한다 */
    private fun isFulltextAllowed(categoryId: String, sourceLink: String): Boolean {
        val host = parseHost(sourceLink) ?: return false
        return sourceStore.listApproved(categoryId)
            .asSequence()
            .filter { source ->
                val sourceHost = parseHost(source.url) ?: return@filter false
                hostMatches(host, sourceHost)
            }
            .any { source ->
                source.fulltextAllowed &&
                    source.legalBasis in setOf(SourceLegalBasis.LICENSED, SourceLegalBasis.OPEN_LICENSE)
            }
    }

    private fun parseHost(url: String): String? = try {
        URI(url).host?.trim()?.lowercase(Locale.ROOT)
    } catch (_: URISyntaxException) {
        null
    }

    private fun hostMatches(targetHost: String, allowedHost: String): Boolean {
        if (targetHost == allowedHost) return true
        return targetHost.endsWith(".$allowedHost")
    }
}
