package com.clipping.mcpserver.service

import com.clipping.mcpserver.observability.ClippingMetrics
import com.clipping.mcpserver.store.AdminUserStore
import com.clipping.mcpserver.store.AsyncJobStore
import com.clipping.mcpserver.store.AuditLogStore
import com.clipping.mcpserver.store.DeliveryLogStore
import com.clipping.mcpserver.store.LlmRunStore
import com.clipping.mcpserver.store.McpAuditLogStore
import com.clipping.mcpserver.store.OriginalContentStore
import com.clipping.mcpserver.store.ReportDeliveryLogStore
import com.clipping.mcpserver.store.ReviewItemAuditStore
import com.clipping.mcpserver.store.RssItemStore
import com.clipping.mcpserver.store.SummaryCacheStore
import com.clipping.mcpserver.store.SummaryRetentionStore
import com.clipping.mcpserver.store.UserEventStore
import com.clipping.mcpserver.support.InterruptibleSleep
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

private val log = KotlinLogging.logger {}

/**
 * 매일 03:15에 오래된 데이터를 정리하여 DB bloat를 방지한다.
 *
 * 정리 대상 및 보관 기간:
 * - clipping_jobs (SUCCEEDED/FAILED): 7일
 * - llm_runs: 90일
 * - original_contents: 30일
 * - delivery_log: 90일
 * - report_delivery_log: 365일
 * - user_events: 90일
 * - audit_log: 365일
 * - batch_summaries: RuntimeSetting(기본 90일) — anchor(feedback/bookmark) 제외 chunk 삭제
 * - rss_items: RuntimeSetting(기본 30일) — batch_summaries 정리 후 chunk 삭제(FK SET NULL 보장)
 * - clipping_review_item_audits: 3년(1095일) — chunk 삭제(10k/회)
 *
 * 매일 02:30에 MCP 감사 로그(mcp_audit_log)를 별도로 정리한다 (보관 90일).
 */
@Component
class DataCleanupScheduler(
    private val asyncJobStore: AsyncJobStore,
    private val llmRunStore: LlmRunStore,
    private val originalContentStore: OriginalContentStore,
    private val deliveryLogStore: DeliveryLogStore,
    private val reportDeliveryLogStore: ReportDeliveryLogStore,
    private val userEventStore: UserEventStore,
    private val auditLogStore: AuditLogStore,
    private val adminUserStore: AdminUserStore,
    private val mcpAuditLogStore: McpAuditLogStore,
    private val metrics: ClippingMetrics,
    private val summaryCacheStore: SummaryCacheStore,
    private val reviewItemAuditStore: ReviewItemAuditStore,
    private val summaryRetentionStore: SummaryRetentionStore,
    private val rssItemStore: RssItemStore,
    private val runtimeSettingService: RuntimeSettingService,
    private val chunkPauseSleeper: (Long, String) -> Unit = InterruptibleSleep::sleep,
) {

    companion object {
        const val ASYNC_JOB_RETENTION_DAYS = 7L
        const val LLM_RUN_RETENTION_DAYS = 90L
        const val ORIGINAL_CONTENT_RETENTION_DAYS = 30L
        const val DELIVERY_LOG_RETENTION_DAYS = 90
        const val REPORT_DELIVERY_LOG_RETENTION_DAYS = 365
        const val USER_EVENT_RETENTION_DAYS = 90L
        const val AUDIT_LOG_RETENTION_DAYS = 365
        private const val SUMMARY_CACHE_RETENTION_DAYS = 7L
        const val MCP_AUDIT_LOG_RETENTION_DAYS = 90L

        /** 개인정보보호법 / ISMS-P 기준 3년(365*3=1095일) 보존. */
        const val REVIEW_ITEM_AUDIT_RETENTION_DAYS = 1095L

        /** 대량 DELETE가 테이블 락을 장시간 점유하지 않도록 한 번에 10k row씩 지운다. */
        const val REVIEW_ITEM_AUDIT_CHUNK_SIZE = 10_000

        /** chunk 간 100ms 대기로 DB 부하를 분산한다. */
        val REVIEW_ITEM_AUDIT_CHUNK_PAUSE: Duration = Duration.ofMillis(100)

        /** 비정상 상황(0건 반환이 영원히 안 옴)에서 무한 루프를 막는 방어 한계. */
        const val REVIEW_ITEM_AUDIT_MAX_CHUNKS = 1_000

        // ── batch_summaries 청크 설정 ─────────────────────────────────────
        /** batch_summaries 한 청크당 최대 삭제 건수. */
        const val BATCH_SUMMARIES_CHUNK_SIZE = 10_000

        /** batch_summaries 청크 간 DB 양보 대기. */
        val BATCH_SUMMARIES_CHUNK_PAUSE: Duration = Duration.ofMillis(100)

        /** batch_summaries 최대 청크 수 (무한 루프 방어). */
        const val BATCH_SUMMARIES_MAX_CHUNKS = 1_000

        // ── rss_items 청크 설정 ───────────────────────────────────────────
        /** rss_items 한 청크당 최대 삭제 건수. */
        const val RSS_ITEMS_CHUNK_SIZE = 10_000

        /** rss_items 청크 간 DB 양보 대기. */
        val RSS_ITEMS_CHUNK_PAUSE: Duration = Duration.ofMillis(100)

        /** rss_items 최대 청크 수 (무한 루프 방어). */
        const val RSS_ITEMS_MAX_CHUNKS = 1_000
    }

    /**
     * 매일 03:15에 실행되는 데이터 정리 스케줄.
     * 각 store 호출을 개별 try-catch로 감싸 하나의 실패가 나머지를 방해하지 않도록 한다.
     */
    @Scheduled(cron = "0 15 3 * * *")
    fun cleanup() = metrics.recordSchedulerRun("data_cleanup") {
        log.info { "Daily data cleanup started" }
        val now = Instant.now()

        // 완료된 비동기 작업(SUCCEEDED/FAILED) 7일 이상 삭제
        val jobsDeleted = runCatching {
            asyncJobStore.deleteCompletedOlderThan(now.minus(ASYNC_JOB_RETENTION_DAYS, ChronoUnit.DAYS))
        }.onFailure { e ->
            log.error(e) { "Failed to clean up clipping_jobs" }
        }.getOrDefault(0)

        // LLM 실행 기록 90일 이상 삭제
        val llmRunsDeleted = runCatching {
            llmRunStore.deleteOlderThan(now.minus(LLM_RUN_RETENTION_DAYS, ChronoUnit.DAYS))
        }.onFailure { e ->
            log.error(e) { "Failed to clean up llm_runs" }
        }.getOrDefault(0)

        // 원본 콘텐츠 30일 이상 삭제
        val contentsDeleted = runCatching {
            originalContentStore.deleteOlderThan(now.minus(ORIGINAL_CONTENT_RETENTION_DAYS, ChronoUnit.DAYS))
        }.onFailure { e ->
            log.error(e) { "Failed to clean up original_contents" }
        }.getOrDefault(0)

        // 발송 이력 90일 이상 삭제
        val deliveryDeleted = runCatching {
            deliveryLogStore.deleteOlderThan(DELIVERY_LOG_RETENTION_DAYS)
        }.onFailure { e ->
            log.error(e) { "Failed to clean up delivery_log" }
        }.getOrDefault(0)

        // 자동 리포트 발송 로그 365일 이상 삭제
        val reportDeliveryDeleted = runCatching {
            reportDeliveryLogStore.deleteOlderThan(REPORT_DELIVERY_LOG_RETENTION_DAYS)
        }.onFailure { e ->
            log.error(e) { "Failed to clean up report_delivery_log" }
        }.getOrDefault(0)

        // 사용자 행동 raw 이벤트 90일 이상 삭제
        val userEventsDeleted = runCatching {
            userEventStore.deleteOlderThan(now.minus(USER_EVENT_RETENTION_DAYS, ChronoUnit.DAYS))
        }.onFailure { e ->
            log.error(e) { "Failed to clean up user_events" }
        }.getOrDefault(0)

        // 감사 로그 365일 이상 삭제
        val auditDeleted = runCatching {
            auditLogStore.deleteOlderThan(AUDIT_LOG_RETENTION_DAYS)
        }.onFailure { e ->
            log.error(e) { "Failed to clean up audit_log" }
        }.getOrDefault(0)

        // summary_cache 7일 이상 엔트리 삭제 (AI 비용 중복 방지 캐시)
        val summaryCacheDeleted = runCatching {
            summaryCacheStore.deleteOlderThan(now.minus(SUMMARY_CACHE_RETENTION_DAYS, ChronoUnit.DAYS))
        }.onFailure { e ->
            log.error(e) { "Failed to clean up summary_cache" }
        }.getOrDefault(0)

        // 비활성 사용자 PII 익명화 (탈퇴 후 90일 경과)
        val anonymized = runCatching {
            anonymizeDeactivatedUsers(now)
        }.onFailure { e ->
            log.error(e) { "Failed to anonymize deactivated users" }
        }.getOrDefault(0)

        // 런타임 설정을 단일 조회로 읽어 batch_summaries / rss_items 두 경로에서 공유한다
        val retentionSettings = runCatching { runtimeSettingService.current() }.getOrNull()

        // batch_summaries 먼저 정리한다 (anchored exclusion: feedback + bookmark).
        val batchSummariesDeleted = runCatching {
            val days = retentionSettings?.retentionBatchSummariesDays ?: 90
            val cutoff = now.minus(days.toLong(), ChronoUnit.DAYS)
            deleteBatchSummariesChunked(cutoff)
        }.onFailure { e ->
            log.error(e) { "Failed to clean up batch_summaries" }
        }.getOrDefault(0L)

        // rss_items 는 batch_summaries 정리 후 진행 — FK SET NULL (V139) 이 잔존 자식 row 의
        // rss_item_id 를 null 로 세팅해서 무결성 유지.
        val rssItemsDeleted = runCatching {
            val days = retentionSettings?.retentionRssItemsDays ?: 30
            val cutoff = now.minus(days.toLong(), ChronoUnit.DAYS)
            deleteRssItemsChunked(cutoff)
        }.onFailure { e ->
            log.error(e) { "Failed to clean up rss_items" }
        }.getOrDefault(0L)

        // 리뷰 결정 감사 이력 3년 이상 chunk 삭제 (DB bloat 방지)
        val reviewAuditsDeleted = runCatching {
            cleanupReviewItemAudits(now)
        }.onFailure { e ->
            log.error(e) { "Failed to clean up clipping_review_item_audits" }
        }.getOrDefault(0L)

        log.info {
            "Daily data cleanup finished — " +
                "jobs=$jobsDeleted, llmRuns=$llmRunsDeleted, contents=$contentsDeleted, " +
                "delivery=$deliveryDeleted, reportDelivery=$reportDeliveryDeleted, " +
                "userEvents=$userEventsDeleted, audit=$auditDeleted, " +
                "summaryCache=$summaryCacheDeleted, anonymized=$anonymized, " +
                "batchSummaries=$batchSummariesDeleted, rssItems=$rssItemsDeleted, " +
                "reviewAudits=$reviewAuditsDeleted"
        }
    }

    /**
     * 매일 02:30에 보관 기간(90일)이 지난 MCP 감사 로그를 정리한다.
     */
    @Scheduled(cron = "0 30 2 * * *")
    fun cleanupMcpAuditLog() {
        runCatching {
            val cutoff = Instant.now().minus(MCP_AUDIT_LOG_RETENTION_DAYS, ChronoUnit.DAYS)
            val deleted = mcpAuditLogStore.deleteOlderThan(cutoff)
            if (deleted > 0) {
                metrics.recordMcpAuditCleanup(deleted)
                log.info { "MCP 감사 로그 $deleted 건 정리 (cutoff=$cutoff)" }
            }
        }.onFailure { e ->
            metrics.recordMcpAuditCleanupFailure()
            log.error(e) { "MCP 감사 로그 정리 실패" }
        }
    }

    /**
     * `batch_summaries` 에서 보관 기간을 초과한 row 를 청크 단위로 삭제한다.
     * anchor(feedback/bookmark)가 있는 row 는 제외하여 사용자 데이터를 보호한다.
     *
     * @param cutoff created_at < cutoff 인 row 가 삭제 후보.
     * @return 이번 실행에서 삭제된 총 row 수.
     */
    private fun deleteBatchSummariesChunked(cutoff: Instant): Long {
        var total = 0L
        val startNanos = System.nanoTime()
        // 0 반환 시 정리할 대상 없음 — 조기 종료
        repeat(BATCH_SUMMARIES_MAX_CHUNKS) {
            val deleted = summaryRetentionStore.deleteOlderThanExcludingAnchored(cutoff, BATCH_SUMMARIES_CHUNK_SIZE)
            if (deleted == 0) {
                val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000
                metrics.recordRetentionRun("batch_summaries", total, elapsedMs)
                return total
            }
            total += deleted
            // 청크 사이 양보해 append 트래픽에 락 경합을 최소화한다
            chunkPauseSleeper(BATCH_SUMMARIES_CHUNK_PAUSE.toMillis(), "batch_summaries cleanup chunk pause")
        }
        // MAX_CHUNKS 도달 — 다음 실행에서 이어 정리
        val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000
        metrics.recordRetentionRun("batch_summaries", total, elapsedMs)
        log.warn {
            "batch_summaries retention hit MAX_CHUNKS cap " +
                "($BATCH_SUMMARIES_MAX_CHUNKS × $BATCH_SUMMARIES_CHUNK_SIZE = " +
                "${BATCH_SUMMARIES_MAX_CHUNKS * BATCH_SUMMARIES_CHUNK_SIZE} rows) " +
                "— rollover to next cycle"
        }
        return total
    }

    /**
     * `rss_items` 에서 보관 기간을 초과한 row 를 청크 단위로 삭제한다.
     * batch_summaries.rss_item_id FK 는 V139 에서 ON DELETE SET NULL 으로 변경되어
     * rss_items 삭제 시 자식 row 는 null 로 세팅된다 (무결성 유지).
     *
     * @param cutoff created_at < cutoff 인 row 가 삭제 후보.
     * @return 이번 실행에서 삭제된 총 row 수.
     */
    private fun deleteRssItemsChunked(cutoff: Instant): Long {
        var total = 0L
        val startNanos = System.nanoTime()
        repeat(RSS_ITEMS_MAX_CHUNKS) {
            // retention은 전역 정책이므로 categoryId=null (모든 카테고리의 오래된 item 삭제)
            val deleted = rssItemStore.deleteOlderThan(cutoff, limit = RSS_ITEMS_CHUNK_SIZE)
            if (deleted == 0) {
                val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000
                metrics.recordRetentionRun("rss_items", total, elapsedMs)
                return total
            }
            total += deleted
            // 청크 사이 양보해 수집 파이프라인 트래픽에 락 경합을 최소화한다
            chunkPauseSleeper(RSS_ITEMS_CHUNK_PAUSE.toMillis(), "rss_items cleanup chunk pause")
        }
        // MAX_CHUNKS 도달 — 다음 실행에서 이어 정리
        val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000
        metrics.recordRetentionRun("rss_items", total, elapsedMs)
        log.warn {
            "rss_items retention hit MAX_CHUNKS cap " +
                "($RSS_ITEMS_MAX_CHUNKS × $RSS_ITEMS_CHUNK_SIZE = " +
                "${RSS_ITEMS_MAX_CHUNKS * RSS_ITEMS_CHUNK_SIZE} rows) " +
                "— rollover to next cycle"
        }
        return total
    }

    /**
     * 비활성(탈퇴) 후 90일이 경과한 사용자의 PII를 익명화한다.
     * username → "withdrawn_XXXX", displayName → null, department → null, slackDmChannelId → null.
     * 감사 로그/통계에서 참조하는 ID는 유지하되 식별 정보만 제거한다.
     */
    private fun anonymizeDeactivatedUsers(now: Instant): Int {
        val cutoff = now.minus(USER_EVENT_RETENTION_DAYS, ChronoUnit.DAYS) // 90일
        return adminUserStore.anonymizeDeactivatedBefore(cutoff)
    }

    /**
     * `clipping_review_item_audits`에서 3년 보존 기간(1095일)을 넘긴 row를
     * 10k row 단위 chunk DELETE로 제거한다.
     *
     * 대량 DELETE 시 테이블 락으로 실시간 append가 막히는 것을 방지하기 위해
     * 한 청크가 끝날 때마다 [InterruptibleSleep]으로 짧게 쉰다.
     * store가 삭제 건수 0을 반환하면 정리할 대상이 더 없다고 판단하고 종료한다.
     *
     * @return 이번 실행에서 삭제된 총 row 수
     */
    private fun cleanupReviewItemAudits(now: Instant): Long {
        // 3년(1095일) 이전 감사 이력은 법적 보존 의무가 소멸해 정리 대상이다
        val cutoff = now.minus(REVIEW_ITEM_AUDIT_RETENTION_DAYS, ChronoUnit.DAYS)
        var totalDeleted = 0L
        var chunk = 0
        while (chunk < REVIEW_ITEM_AUDIT_MAX_CHUNKS) {
            // 한 번에 최대 10k row만 지워 lock hold 시간을 짧게 유지한다
            val deleted = reviewItemAuditStore.deleteOlderThan(cutoff, REVIEW_ITEM_AUDIT_CHUNK_SIZE)
            if (deleted <= 0) break
            totalDeleted += deleted
            chunk++
            // 청크 사이 쉬어 append 트래픽에 양보한다 (InterruptibleSleep: AGENTS.md 1.3 raw Thread.sleep 금지)
            chunkPauseSleeper(REVIEW_ITEM_AUDIT_CHUNK_PAUSE.toMillis(), "review_item_audits cleanup chunk pause")
        }
        if (chunk >= REVIEW_ITEM_AUDIT_MAX_CHUNKS) {
            // chunk 한도 도달 — 다음 실행에서 이어 정리하도록 경고만 남긴다
            log.warn { "review_item_audits cleanup hit max chunks ($REVIEW_ITEM_AUDIT_MAX_CHUNKS); will resume next run" }
        }
        if (totalDeleted > 0) {
            log.info { "review_item_audits cleanup: $totalDeleted rows deleted older than $cutoff in $chunk chunks" }
        }
        return totalDeleted
    }
}
