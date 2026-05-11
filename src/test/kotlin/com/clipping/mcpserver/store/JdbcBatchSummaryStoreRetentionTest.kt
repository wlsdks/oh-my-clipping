package com.clipping.mcpserver.store

import com.clipping.mcpserver.model.Category
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
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
 * SummaryRetentionStore.deleteOlderThanExcludingAnchored 의 anchor 보호 및 chunked DELETE 동작을 검증한다.
 *
 * JpaBatchSummaryStore가 @Primary 이므로 BatchSummaryStore 빈은 JPA 구현이 주입된다.
 * 테스트는 직접 JdbcTemplate 으로 row 를 삽입해 created_at 시각을 자유롭게 제어한다
 * (save() 는 Instant.now() 로 고정하기 때문에 과거 시각 테스트에 사용 불가).
 *
 * delta-based count 패턴으로 공유 H2 의 잔존 row 영향을 최소화한다. (AGENTS.md §5.1 L-006)
 * non-anchored deletion 테스트에서 deleted 절대값 대신 category-scoped delta 를 사용한다.
 *
 * @Transactional 을 사용하지 않으므로 @AfterEach 에서 수동으로 test data 를 정리해야 한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class BatchSummaryStoreRetentionTest {

    @Autowired
    lateinit var summaryRetentionStore: SummaryRetentionStore

    @Autowired
    lateinit var categoryStore: CategoryStore

    @Autowired
    lateinit var jdbc: JdbcTemplate

    /** 각 테스트가 고유한 category_id 를 사용해 상호 간섭을 차단한다. */
    private lateinit var categoryId: String

    /** summary_feedback / bookmarked_articles 의 user_id FK 를 만족시키는 테스트 유저 ID. */
    private lateinit var testUserId: String

    @BeforeEach
    fun setup() {
        // categoryStore.save() 를 사용해 유효한 UUID 형식의 category_id 를 확보한다.
        categoryId = categoryStore.save(
            Category(id = "", name = "RetentionTest-${System.nanoTime()}")
        ).id

        // summary_feedback / bookmarked_articles.user_id 가 admin_users(id) FK 를 가지므로
        // 테스트 유저를 먼저 삽입해 제약을 만족시킨다.
        testUserId = UUID.randomUUID().toString()
        jdbc.update(
            """
            INSERT INTO admin_users (id, username, password_hash, is_active, created_at, updated_at)
            VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """.trimIndent(),
            testUserId,
            "retention-test-${testUserId.takeLast(8)}",
            "placeholder-hash",
            true
        )
    }

    @AfterEach
    fun cleanup() {
        // cascade DELETE 로 인한 FK 제약 만족 — summary_feedback, bookmarked_articles 자동 삭제
        jdbc.update("DELETE FROM batch_summaries WHERE category_id = ?", categoryId)
        // 테스트 유저 삭제
        jdbc.update("DELETE FROM admin_users WHERE id = ?", testUserId)
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    /**
     * batch_summaries 에 임의 createdAt 으로 row 를 직접 삽입한다.
     * save() 를 우회해 과거 시각 지정이 가능하다. rss_item_id 는 V139 이후 nullable.
     */
    private fun insertSummary(createdAt: Instant, rssItemId: String? = null): String {
        val id = UUID.randomUUID().toString()
        jdbc.update(
            """
            INSERT INTO batch_summaries
                (id, original_title, summary, importance_score, source_link,
                 is_sent_to_slack, category_id, rss_item_id, is_fallback, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            id,
            "Retention test title",
            "Retention test summary",
            0.5f,
            "https://example.com/retention-${UUID.randomUUID()}",
            false,
            categoryId,
            rssItemId,
            false,
            Timestamp.from(createdAt)
        )
        return id
    }

    /** summary_feedback row 를 삽입해 피드백 anchor 를 생성한다. */
    private fun insertFeedback(summaryId: String) {
        // user_id 는 admin_users(id) FK 를 가지므로 setUp 에서 생성한 testUserId 를 사용한다.
        jdbc.update(
            """
            INSERT INTO summary_feedback (id, summary_id, feedback_type, user_id, created_at)
            VALUES (?, ?, ?, ?, ?)
            """.trimIndent(),
            UUID.randomUUID().toString(),
            summaryId,
            "LIKE",
            testUserId,
            Timestamp.from(Instant.now())
        )
    }

    /** bookmarked_articles row 를 삽입해 북마크 anchor 를 생성한다. */
    private fun insertBookmark(summaryId: String) {
        // user_id 는 admin_users(id) FK, category_id 는 batch_categories(id) FK 를 가진다.
        jdbc.update(
            """
            INSERT INTO bookmarked_articles
                (id, user_id, summary_id, original_title, summary,
                 importance_score, source_link, category_id, article_created_at, bookmarked_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            UUID.randomUUID().toString(),
            testUserId,
            summaryId,
            "Bookmarked article title",
            "Bookmarked article summary",
            0.5f,
            "https://example.com/bookmark-${UUID.randomUUID()}",
            categoryId,
            Timestamp.from(Instant.now()),
            Timestamp.from(Instant.now())
        )
    }

    /** category_id 범위 내 batch_summaries 건수를 반환한다. */
    private fun countInCategory(): Int =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM batch_summaries WHERE category_id = ?",
            Int::class.java,
            categoryId
        ) ?: 0

    // ── test cases ───────────────────────────────────────────────────────────

    @Nested
    inner class `anchored preservation` {

        @Test
        fun `summary_feedback 가 있는 row 는 90일 초과여도 유지된다`() {
            val cutoff = Instant.now().minus(90, ChronoUnit.DAYS)
            val anchoredId = insertSummary(cutoff.minus(10, ChronoUnit.DAYS))
            insertFeedback(anchoredId)

            val before = countInCategory()
            summaryRetentionStore.deleteOlderThanExcludingAnchored(cutoff, limit = 1000)
            val after = countInCategory()

            // anchor 로 보호된 row 는 삭제되지 않아야 한다.
            (before - after) shouldBe 0
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM batch_summaries WHERE id = ?",
                Int::class.java, anchoredId
            ) shouldBe 1
        }

        @Test
        fun `bookmarked_articles 가 있는 row 는 90일 초과여도 유지된다`() {
            // V117 CASCADE FK 방어 검증 — bookmark anchor 가 없으면 사용자 북마크가 CASCADE 삭제된다.
            val cutoff = Instant.now().minus(90, ChronoUnit.DAYS)
            val anchoredId = insertSummary(cutoff.minus(10, ChronoUnit.DAYS))
            insertBookmark(anchoredId)

            val before = countInCategory()
            summaryRetentionStore.deleteOlderThanExcludingAnchored(cutoff, limit = 1000)
            val after = countInCategory()

            (before - after) shouldBe 0
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM batch_summaries WHERE id = ?",
                Int::class.java, anchoredId
            ) shouldBe 1
        }

        @Test
        fun `bookmark + feedback 양쪽에 anchor 있으면 유지된다 (중복 anchor)`() {
            val cutoff = Instant.now().minus(90, ChronoUnit.DAYS)
            val anchoredId = insertSummary(cutoff.minus(30, ChronoUnit.DAYS))
            insertFeedback(anchoredId)
            insertBookmark(anchoredId)

            val before = countInCategory()
            summaryRetentionStore.deleteOlderThanExcludingAnchored(cutoff, limit = 1000)
            val after = countInCategory()

            (before - after) shouldBe 0
        }
    }

    @Nested
    inner class `non-anchored deletion` {

        @Test
        fun `feedback, bookmark 둘 다 없으면 90일 초과 시 삭제된다`() {
            val cutoff = Instant.now().minus(90, ChronoUnit.DAYS)
            // 삭제 대상 3건
            repeat(3) { insertSummary(cutoff.minus(10L + it, ChronoUnit.DAYS)) }
            // 보존 대상(cutoff 이후) 2건
            repeat(2) { insertSummary(cutoff.plus(1L + it, ChronoUnit.DAYS)) }

            val before = countInCategory()
            // 공유 H2 에 다른 테스트가 삽입한 stale row 가 있을 수 있으므로
            // deleted 절대값 대신 category-scoped delta 로 검증한다. (AGENTS.md §5.1 L-006)
            summaryRetentionStore.deleteOlderThanExcludingAnchored(cutoff, limit = 1000)
            val after = countInCategory()

            // cutoff 이전 3건이 삭제되고, cutoff 이후 2건은 보존된다.
            (before - after) shouldBe 3
        }
    }

    @Nested
    inner class `경계값` {

        @Test
        fun `정확히 cutoff 에 있는 row 는 삭제되지 않는다 (created_at 엄격 미만 조건)`() {
            val cutoff = Instant.now().minus(90, ChronoUnit.DAYS)
            val boundaryId = insertSummary(cutoff)              // created_at == cutoff → 보존
            val staleId = insertSummary(cutoff.minusSeconds(1)) // created_at < cutoff → 삭제

            val before = countInCategory()
            val deleted = summaryRetentionStore.deleteOlderThanExcludingAnchored(cutoff, limit = 1000)
            val after = countInCategory()

            // delta-based: 이 테스트는 testCategoryId 범위에서만 2건을 seed 하므로 delta 는 1
            (before - after) shouldBe 1
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM batch_summaries WHERE id = ?",
                Int::class.java, boundaryId
            ) shouldBe 1
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM batch_summaries WHERE id = ?",
                Int::class.java, staleId
            ) shouldBe 0
        }
    }

    @Nested
    inner class `limit 존중` {

        @Test
        fun `한 호출당 limit row 만 삭제된다 (seed 10, limit 3)`() {
            // 실제 테스트에서 생성하는 row 보다 훨씬 오래된 시각을 사용해
            // ORDER BY created_at 에서 우선 선택되도록 한다.
            val ancientBase = Instant.parse("2000-01-01T00:00:00Z")
            val cutoff = ancientBase.plus(200, ChronoUnit.DAYS)
            // 삭제 대상 10건 (anchor 없음, ancientBase 기준 100~109일 후 = cutoff 이전)
            repeat(10) { i ->
                insertSummary(ancientBase.plus(100L + i, ChronoUnit.DAYS))
            }

            val deleted = summaryRetentionStore.deleteOlderThanExcludingAnchored(cutoff, limit = 3)

            // limit=3 이므로 한 번 호출에 정확히 3건만 삭제된다.
            // ancientBase 기준 row 는 다른 테스트보다 훨씬 오래됐으므로 ORDER BY 에서 먼저 선택된다.
            deleted shouldBe 3
        }
    }

    @Nested
    inner class `0건 처리` {

        @Test
        fun `대상이 없으면 0 반환하고 예외 없음`() {
            val cutoff = Instant.now().minus(90, ChronoUnit.DAYS)
            // cutoff 이후만 삽입 → 삭제 대상 없음
            insertSummary(cutoff.plus(1, ChronoUnit.DAYS))

            val deleted = summaryRetentionStore.deleteOlderThanExcludingAnchored(cutoff, limit = 1000)

            deleted shouldBe 0
        }
    }
}
