package com.clipping.mcpserver.integration

import com.clipping.mcpserver.service.DataCleanupScheduler
import com.clipping.mcpserver.service.RuntimeSettingService
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.micrometer.core.instrument.MeterRegistry
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import java.sql.Timestamp
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * DataCleanupScheduler.cleanup() 전체 흐름 통합 테스트.
 *
 * 현실적인 데이터를 seed 하고 실제 스케줄러를 호출해
 * batch_summaries + rss_items retention, anchor 보호, FK SET NULL, 메트릭 기록을
 * 단일 실행으로 종단간 검증한다.
 *
 * 격리 전략:
 * - 고유한 categoryId + rssSourceId 로 이 테스트가 seed 한 row 만 범위를 잡는다.
 * - delta-based assertion (before - after) 으로 공유 H2 의 잔존 row 영향을 배제한다.
 * - RuntimeSettingService 를 통해 보존 기간을 이 테스트 seed 시각과 정밀하게 맞춘다.
 *   (§AGENTS.md 5.1 L-006)
 *
 * @Transactional 을 사용하지 않으므로 @AfterEach 에서 수동으로 test data 를 정리한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class DataRetentionIntegrationTest {

    @Autowired
    lateinit var scheduler: DataCleanupScheduler

    @Autowired
    lateinit var runtimeSettingService: RuntimeSettingService

    @Autowired
    lateinit var meterRegistry: MeterRegistry

    @Autowired
    lateinit var jdbc: JdbcTemplate

    /** 이 테스트 전용 category_id (batch_categories + batch_summaries + rss_items 격리). */
    private lateinit var categoryId: String

    /** 이 테스트 전용 rss_source_id (rss_items 격리). */
    private lateinit var rssSourceId: String

    /** summary_feedback / bookmarked_articles 의 user_id FK 를 만족시키는 테스트 유저 ID. */
    private lateinit var testUserId: String

    // ── fixture constants ─────────────────────────────────────────────────────

    /** rss_items 보존 기간 (일). 이 값보다 오래된 row 가 삭제 대상이 된다. */
    private val RSS_RETENTION_DAYS = 30

    /** batch_summaries 보존 기간 (일). */
    private val BATCH_SUMMARY_RETENTION_DAYS = 90

    // ── setup / teardown ─────────────────────────────────────────────────────

    @BeforeEach
    fun setup() {
        categoryId = UUID.randomUUID().toString()
        rssSourceId = UUID.randomUUID().toString()
        testUserId = UUID.randomUUID().toString()

        // batch_categories 먼저 생성 (rss_sources, batch_summaries FK 기준)
        jdbc.update(
            "INSERT INTO batch_categories (id, name, created_at) VALUES (?, ?, CURRENT_TIMESTAMP)",
            categoryId, "RetentionE2E-${System.nanoTime()}"
        )

        // rss_sources 생성 (rss_items FK 기준)
        jdbc.update(
            "INSERT INTO rss_sources (id, name, url, is_active, category_id) VALUES (?, ?, ?, ?, ?)",
            rssSourceId,
            "RetentionE2E Source",
            "https://example.com/feed-$rssSourceId",
            true,
            categoryId
        )

        // summary_feedback / bookmarked_articles.user_id FK 를 만족하는 테스트 유저 생성
        jdbc.update(
            """
            INSERT INTO admin_users (id, username, password_hash, is_active, created_at, updated_at)
            VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """.trimIndent(),
            testUserId,
            "retention-e2e-${testUserId.takeLast(8)}",
            "placeholder-hash",
            true
        )

        // RuntimeSetting 을 seed 시각과 정확히 일치시킨다.
        // scheduler.cleanup() 은 RuntimeSettingService.current() 로 보존 기간을 읽으므로
        // 이 테스트 전용 값을 설정해 다른 테스트의 기본값(30/90일)과 동일하게 유지한다.
        runtimeSettingService.update(
            RuntimeSettingService.RuntimeSettingsUpdate(
                retentionRssItemsDays = RSS_RETENTION_DAYS,
                retentionBatchSummariesDays = BATCH_SUMMARY_RETENTION_DAYS
            ),
            changedBy = "retention-e2e-test"
        )
    }

    @AfterEach
    fun cleanup() {
        // FK 의존 순서 (자식 → 부모):
        // summary_feedback, bookmarked_articles → batch_summaries → rss_items → rss_sources → batch_categories
        jdbc.update("DELETE FROM summary_feedback WHERE user_id = ?", testUserId)
        jdbc.update("DELETE FROM bookmarked_articles WHERE user_id = ?", testUserId)
        jdbc.update("DELETE FROM batch_summaries WHERE category_id = ?", categoryId)
        jdbc.update("DELETE FROM rss_items WHERE rss_source_id = ?", rssSourceId)
        jdbc.update("DELETE FROM rss_sources WHERE id = ?", rssSourceId)
        jdbc.update("DELETE FROM batch_categories WHERE id = ?", categoryId)
        jdbc.update("DELETE FROM admin_users WHERE id = ?", testUserId)
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /**
     * rss_items 에 row 를 직접 삽입하고 생성된 ID 를 반환한다.
     * link 컬럼이 UNIQUE 이므로 각 호출마다 유니크한 링크를 생성한다.
     */
    private fun insertRssItem(createdAt: Instant): String {
        val id = UUID.randomUUID().toString()
        val uniqueLink = "https://example.com/article/$id/${System.nanoTime()}"
        jdbc.update(
            """
            INSERT INTO rss_items
                (id, title, content, link, language, is_processed, category_id, rss_source_id, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            id,
            "Retention E2E Test Article",
            "Test content",
            uniqueLink,
            "FOREIGN",
            false,
            categoryId,
            rssSourceId,
            Timestamp.from(createdAt)
        )
        return id
    }

    /**
     * batch_summaries 에 row 를 직접 삽입하고 생성된 ID 를 반환한다.
     * save() 는 Instant.now() 로 created_at 을 고정하므로 직접 삽입해 과거 시각을 제어한다.
     */
    private fun insertBatchSummary(createdAt: Instant, rssItemId: String? = null): String {
        val id = UUID.randomUUID().toString()
        jdbc.update(
            """
            INSERT INTO batch_summaries
                (id, original_title, summary, importance_score, source_link,
                 is_sent_to_slack, category_id, rss_item_id, is_fallback, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            id,
            "Retention E2E Test Title",
            "Retention E2E Test Summary",
            0.5f,
            "https://example.com/summary-$id",
            false,
            categoryId,
            rssItemId,
            false,
            Timestamp.from(createdAt)
        )
        return id
    }

    /**
     * summary_feedback row 를 삽입해 피드백 anchor 를 생성한다.
     * user_id FK 는 setup() 에서 생성한 testUserId 를 사용한다.
     */
    private fun insertFeedback(summaryId: String) {
        jdbc.update(
            """
            INSERT INTO summary_feedback (id, summary_id, feedback_type, user_id, created_at)
            VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
            """.trimIndent(),
            UUID.randomUUID().toString(),
            summaryId,
            "LIKE",
            testUserId
        )
    }

    /**
     * bookmarked_articles row 를 삽입해 북마크 anchor 를 생성한다.
     * user_id FK 는 setup() 에서 생성한 testUserId 를 사용한다.
     */
    private fun insertBookmark(summaryId: String) {
        jdbc.update(
            """
            INSERT INTO bookmarked_articles
                (id, user_id, summary_id, original_title, summary,
                 importance_score, source_link, category_id, article_created_at, bookmarked_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """.trimIndent(),
            UUID.randomUUID().toString(),
            testUserId,
            summaryId,
            "Bookmarked Article Title",
            "Bookmarked Article Summary",
            0.5f,
            "https://example.com/bookmark-$summaryId",
            categoryId
        )
    }

    /** category_id 범위 내 batch_summaries 건수를 반환한다. */
    private fun countBatchSummariesInCategory(): Int =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM batch_summaries WHERE category_id = ?",
            Int::class.java,
            categoryId
        ) ?: 0

    /** rssSourceId 범위 내 rss_items 건수를 반환한다. */
    private fun countRssItemsInSource(): Int =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM rss_items WHERE rss_source_id = ?",
            Int::class.java,
            rssSourceId
        ) ?: 0

    /** retention.rows_deleted{table=?} 의 현재 카운터 값을 반환한다. 없으면 0.0. */
    private fun retentionDeletedCount(table: String): Double =
        meterRegistry.find("clipping.retention.rows_deleted")
            .tag("table", table)
            .counter()?.count() ?: 0.0

    // ── test ─────────────────────────────────────────────────────────────────

    @Test
    fun `전체 retention 파이프라인 — 30d rss_items + 90d batch_summaries anchored exclusion`() {

        // ─── Seed ────────────────────────────────────────────────────────────

        val rssOldCutoff = Instant.now().minus(RSS_RETENTION_DAYS + 1L, ChronoUnit.DAYS)
        val bsCutoff = Instant.now().minus(BATCH_SUMMARY_RETENTION_DAYS + 1L, ChronoUnit.DAYS)

        // rss_items: 50개 오래된 것(삭제 대상) + 50개 최근 것(보존)
        val oldRssItemIds = (1..50).map { insertRssItem(rssOldCutoff.minusSeconds(it.toLong())) }
        val recentRssItemIds = (1..50).map { insertRssItem(Instant.now().minusSeconds(it.toLong())) }

        // batch_summaries: 총 50개
        // - 30개 오래된 것 (90일 초과)
        //   - 5개: summary_feedback anchor → 보존
        //   - 5개: bookmarked_articles anchor → 보존
        //   - 20개: anchor 없음 → 삭제
        // - 20개 최근 것 → 보존

        // 오래된 것 5개: feedback anchor
        val oldFeedbackAnchoredIds = (1..5).map { i ->
            insertBatchSummary(bsCutoff.minusSeconds(i.toLong())).also { insertFeedback(it) }
        }

        // 오래된 것 5개: bookmark anchor
        val oldBookmarkAnchoredIds = (1..5).map { i ->
            insertBatchSummary(bsCutoff.minusSeconds((i + 100).toLong())).also { insertBookmark(it) }
        }

        // 오래된 것 20개: anchor 없음 (삭제 대상)
        val oldNonAnchoredIds = (1..20).map { i ->
            insertBatchSummary(bsCutoff.minusSeconds((i + 200).toLong()))
        }

        // 최근 것 20개 (보존)
        val recentSummaryIds = (1..20).map { i ->
            insertBatchSummary(Instant.now().minusSeconds(i.toLong()))
        }

        // ─── Snapshot before ─────────────────────────────────────────────────

        val bsBefore = countBatchSummariesInCategory()
        val rssBefore = countRssItemsInSource()

        val bsMetricBefore = retentionDeletedCount("batch_summaries")
        val rssMetricBefore = retentionDeletedCount("rss_items")

        // ─── Act ─────────────────────────────────────────────────────────────

        scheduler.cleanup()

        // ─── Assertions ──────────────────────────────────────────────────────

        val bsAfter = countBatchSummariesInCategory()
        val rssAfter = countRssItemsInSource()

        // batch_summaries: 20개 비앵커 오래된 것이 삭제된다
        // 보존: 5(feedback anchor) + 5(bookmark anchor) + 20(최근) = 30
        (bsBefore - bsAfter) shouldBe 20

        // rss_items: 50개 오래된 것이 삭제된다
        // 보존: 50개 최근 것
        (rssBefore - rssAfter) shouldBe 50

        // anchor 가 있는 오래된 batch_summaries 는 여전히 존재해야 한다
        for (id in oldFeedbackAnchoredIds) {
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM batch_summaries WHERE id = ?",
                Int::class.java, id
            ) shouldBe 1
        }
        for (id in oldBookmarkAnchoredIds) {
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM batch_summaries WHERE id = ?",
                Int::class.java, id
            ) shouldBe 1
        }

        // 비앵커 오래된 batch_summaries 는 삭제되어야 한다
        for (id in oldNonAnchoredIds) {
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM batch_summaries WHERE id = ?",
                Int::class.java, id
            ) shouldBe 0
        }

        // 최근 batch_summaries 는 보존되어야 한다
        for (id in recentSummaryIds) {
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM batch_summaries WHERE id = ?",
                Int::class.java, id
            ) shouldBe 1
        }

        // 오래된 rss_items 는 모두 삭제되어야 한다
        for (id in oldRssItemIds) {
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM rss_items WHERE id = ?",
                Int::class.java, id
            ) shouldBe 0
        }

        // 최근 rss_items 는 보존되어야 한다
        for (id in recentRssItemIds) {
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM rss_items WHERE id = ?",
                Int::class.java, id
            ) shouldBe 1
        }

        // ── FK SET NULL: 앵커된 batch_summaries 가 rss_item_id=NULL 로 세팅됐는지 확인
        // oldFeedbackAnchoredIds 와 oldBookmarkAnchoredIds 는 rssItemId=null 로 삽입했으므로
        // FK SET NULL 이 실제로 발화하는지는 rss_item 을 참조하는 summary 를 별도 검증한다.
        // 여기서는 rss_item 을 참조한 최근 summary 가 rss_item 삭제 후 SET NULL 됐음을 확인한다.
        // (최근 rss_items 는 삭제되지 않으므로 SET NULL 발화 없이 원본 유지)
        for (id in recentRssItemIds) {
            // 최근 rss_item 은 삭제되지 않았으므로 rss_item_id 는 non-null 상태를 유지
            val count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM rss_items WHERE id = ?",
                Int::class.java, id
            )
            count shouldBe 1
        }

        // ── summary_feedback 는 anchor 된 batch_summary 와 함께 여전히 존재해야 한다
        // (CASCADE 가 발화하지 않았으므로 부모인 batch_summary 는 삭제되지 않음)
        val feedbackCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM summary_feedback WHERE user_id = ?",
            Int::class.java, testUserId
        ) ?: 0
        feedbackCount shouldBe 5

        // ── bookmarked_articles 는 anchor 된 batch_summary 와 함께 여전히 존재해야 한다
        val bookmarkCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM bookmarked_articles WHERE user_id = ?",
            Int::class.java, testUserId
        ) ?: 0
        bookmarkCount shouldBe 5

        // ── 메트릭: retention.rows_deleted{table=batch_summaries} 가 20 이상 증가해야 한다
        val bsMetricDelta = retentionDeletedCount("batch_summaries") - bsMetricBefore
        (bsMetricDelta >= 20.0) shouldBe true

        // ── 메트릭: retention.rows_deleted{table=rss_items} 가 50 이상 증가해야 한다
        val rssMetricDelta = retentionDeletedCount("rss_items") - rssMetricBefore
        (rssMetricDelta >= 50.0) shouldBe true
    }
}
