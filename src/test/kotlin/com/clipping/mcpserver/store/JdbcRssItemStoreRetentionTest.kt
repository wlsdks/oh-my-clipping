package com.clipping.mcpserver.store

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
 * V139 migration이 batch_summaries.rss_item_id FK 를 RESTRICT 에서 SET NULL 로 변경했을 때
 * 실제 H2 MODE=PostgreSQL 환경에서 FK 동작이 올바르게 작동함을 검증한다.
 *
 * 테스트는 직접 JdbcTemplate 으로 rss_items + batch_summaries row 를 삽입하고
 * rss_item 삭제 시 자식 batch_summaries.rss_item_id 가 NULL 로 설정되는지 확인한다.
 *
 * @Transactional 을 사용하지 않으므로 @AfterEach 에서 수동으로 test data 를 정리해야 한다.
 *
 * V4 가 생성한 익명 FK(CONSTRAINT_N) 제거는 V139 Kotlin 마이그레이션이 담당한다.
 * 이전에 @BeforeAll 에 있던 workaround 는 제거되었다 —
 * V139__RetentionPrepBatchSummariesRssItemFk 가 INFORMATION_SCHEMA 를 통해 동적으로 처리한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class JdbcRssItemStoreRetentionTest {

    @Autowired
    lateinit var jdbc: JdbcTemplate

    /** 각 테스트가 고유한 category_id 를 사용해 상호 간섭을 차단한다. */
    private lateinit var categoryId: String

    /** 각 테스트가 고유한 rss_source_id 를 사용한다. */
    private lateinit var rssSourceId: String

    @BeforeEach
    fun setup() {
        // 테스트용 고유 category_id, rss_source_id 생성
        categoryId = UUID.randomUUID().toString()
        rssSourceId = UUID.randomUUID().toString()

        // batch_categories 에 FK 제약이 있으므로 먼저 생성
        jdbc.update(
            "INSERT INTO batch_categories (id, name, created_at) VALUES (?, ?, CURRENT_TIMESTAMP)",
            categoryId, "Test Category - ${System.nanoTime()}"
        )

        // rss_sources 에 FK 제약이 있으므로 생성 (category_id NOT NULL 필수)
        jdbc.update(
            "INSERT INTO rss_sources (id, name, url, is_active, category_id) VALUES (?, ?, ?, ?, ?)",
            rssSourceId, "Test RSS Source", "https://example.com/feed-$rssSourceId", true, categoryId
        )
    }

    @AfterEach
    fun cleanup() {
        // FK 의존 순서 (자식 → 부모):
        // 1. batch_summaries → rss_items (rss_item_id는 SET NULL이므로 NULL이 될 수 있음)
        //    category_id FK 가 남아있으므로 category_id 기준으로 삭제해야 안전하다.
        //    rss_item_id IN (SELECT ...) 패턴은 rss_items 삭제 후 SET NULL 이 되어 no-op이 된다.
        // 2. rss_items → rss_sources, batch_categories
        // 3. rss_sources → batch_categories  ← batch_categories 보다 먼저 삭제
        // 4. batch_categories
        jdbc.update("DELETE FROM batch_summaries WHERE category_id = ?", categoryId)
        jdbc.update("DELETE FROM rss_items WHERE rss_source_id = ?", rssSourceId)
        jdbc.update("DELETE FROM rss_sources WHERE id = ?", rssSourceId)
        jdbc.update("DELETE FROM batch_categories WHERE id = ?", categoryId)
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    /**
     * rss_items 에 row 를 직접 삽입하고 생성된 ID 를 반환한다.
     * 임의 createdAt 지정이 가능하다.
     * link 컬럼이 UNIQUE 이므로 각 호출마다 유니크한 링크를 생성한다.
     */
    private fun insertRssItem(
        createdAt: Instant = Instant.now()
    ): String {
        val id = UUID.randomUUID().toString()
        val uniqueLink = "https://example.com/article/$id/${System.nanoTime()}"
        jdbc.update(
            """
            INSERT INTO rss_items
                (id, title, content, link, language, is_processed, category_id, rss_source_id, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            id,
            "Test RSS Item Title",
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
     * batch_summaries 에 rss_item_id 를 참조하는 row 를 직접 삽입한다.
     * rss_item_id 는 V139 이후 nullable 이므로 null 로도 삽입 가능하다.
     */
    private fun insertBatchSummary(
        rssItemId: String? = null,
        createdAt: Instant = Instant.now()
    ): String {
        val id = UUID.randomUUID().toString()
        jdbc.update(
            """
            INSERT INTO batch_summaries
                (id, original_title, summary, source_link,
                 is_sent_to_slack, category_id, rss_item_id, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            id,
            "Test Summary Title",
            "Test summary content",
            "https://example.com/summary-$id",
            false,
            categoryId,
            rssItemId,
            Timestamp.from(createdAt)
        )
        return id
    }

    /**
     * batch_summaries row 의 현재 rss_item_id 값을 조회한다 (NULL 감지용).
     */
    private fun getRssItemIdForSummary(summaryId: String): String? =
        jdbc.queryForObject(
            "SELECT rss_item_id FROM batch_summaries WHERE id = ?",
            String::class.java,
            summaryId
        )

    /**
     * rssSourceId 범위 내 rss_items 건수를 반환한다.
     */
    private fun countRssItemsInSource(): Int =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM rss_items WHERE rss_source_id = ?",
            Int::class.java,
            rssSourceId
        ) ?: 0

    // ── test cases ───────────────────────────────────────────────────────────

    @Nested
    inner class `FK SET NULL 동작` {

        @Test
        fun `rss_items 삭제 시 batch_summaries의 rss_item_id가 NULL이 된다`() {
            val rssItemId = try {
                insertRssItem()
            } catch (e: Exception) {
                println("Failed to insert rss_item: ${e.message}")
                throw e
            }

            val summaryId = try {
                insertBatchSummary(rssItemId = rssItemId)
            } catch (e: Exception) {
                println("Failed to insert batch_summary: ${e.message}")
                throw e
            }

            // 초기 상태 검증: summary 가 rss_item 을 참조중
            getRssItemIdForSummary(summaryId) shouldBe rssItemId

            // Act: rss_item 삭제 (JDBC 직접 DELETE)
            jdbc.update("DELETE FROM rss_items WHERE id = ?", rssItemId)

            // Verify: batch_summary 는 여전히 존재하지만 rss_item_id 는 NULL
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM batch_summaries WHERE id = ?",
                Int::class.java,
                summaryId
            ) shouldBe 1
            getRssItemIdForSummary(summaryId) shouldBe null
        }

        @Test
        fun `여러 child 중 일부만 영향받는 경우`() {
            // Setup: rss_items A, B 와 각각을 참조하는 summaries
            val rssItemA = insertRssItem()
            val rssItemB = insertRssItem()

            val summaryA1 = insertBatchSummary(rssItemId = rssItemA)
            val summaryA2 = insertBatchSummary(rssItemId = rssItemA)
            val summaryB = insertBatchSummary(rssItemId = rssItemB)
            val summaryNone = insertBatchSummary(rssItemId = null)  // 미참조

            // Act: A만 삭제
            jdbc.update("DELETE FROM rss_items WHERE id = ?", rssItemA)

            // Verify: A 참조자들은 NULL, B 참조자는 유지, 미참조는 변화 없음
            getRssItemIdForSummary(summaryA1) shouldBe null
            getRssItemIdForSummary(summaryA2) shouldBe null
            getRssItemIdForSummary(summaryB) shouldBe rssItemB
            getRssItemIdForSummary(summaryNone) shouldBe null
        }
    }

    @Nested
    inner class `retention 경계값` {

        @Test
        fun `정확히 cutoff에 있는 row는 삭제되지 않는다`() {
            val cutoff = Instant.now().minus(30, ChronoUnit.DAYS)
            val boundaryItemId = insertRssItem(cutoff)              // created_at == cutoff → 보존
            val staleItemId = insertRssItem(cutoff.minusSeconds(1)) // created_at < cutoff → 삭제

            val before = countRssItemsInSource()
            // 직접 JDBC delete
            jdbc.update("DELETE FROM rss_items WHERE rss_source_id = ? AND created_at < ?",
                rssSourceId, Timestamp.from(cutoff)
            )
            val after = countRssItemsInSource()

            // delta-based: 이 테스트는 rssSourceId 범위에서만 2건을 seed 하므로 delta 는 1
            (before - after) shouldBe 1
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM rss_items WHERE id = ?",
                Int::class.java,
                boundaryItemId
            ) shouldBe 1
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM rss_items WHERE id = ?",
                Int::class.java,
                staleItemId
            ) shouldBe 0
        }
    }

    @Nested
    inner class `rss_source filter` {

        @Test
        fun `특정 rss_source에 속한 rss_item 만 정리된다`() {
            val source2Id = UUID.randomUUID().toString()
            val category2Id = UUID.randomUUID().toString()
            val cutoff = Instant.now().minus(30, ChronoUnit.DAYS)

            // category2 생성
            jdbc.update(
                "INSERT INTO batch_categories (id, name, created_at) VALUES (?, ?, CURRENT_TIMESTAMP)",
                category2Id, "Test Category 2"
            )

            // source2 생성 (category_id NOT NULL 필수)
            jdbc.update(
                "INSERT INTO rss_sources (id, name, url, is_active, category_id) VALUES (?, ?, ?, ?, ?)",
                source2Id, "Test RSS Source 2", "https://example.com/feed2", true, category2Id
            )

            // 두 source 에서 각 3개씩 stale item 생성
            val source1StaleIds = (1..3).map { insertRssItem(cutoff.minusSeconds(it.toLong())) }
            val source2StaleIds = (1..3).map {
                val id = UUID.randomUUID().toString()
                val uniqueLink = "https://example.com/source2/article/$id/${System.nanoTime()}"
                jdbc.update(
                    """
                    INSERT INTO rss_items
                        (id, title, content, link, language, is_processed, category_id, rss_source_id, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                    id,
                    "Test RSS Item Title",
                    "Test content",
                    uniqueLink,
                    "FOREIGN",
                    false,
                    category2Id,
                    source2Id,
                    Timestamp.from(cutoff.minusSeconds(it.toLong()))
                )
                id
            }

            val before1 = countRssItemsInSource()
            val before2 = jdbc.queryForObject(
                "SELECT COUNT(*) FROM rss_items WHERE rss_source_id = ?",
                Int::class.java,
                source2Id
            ) ?: 0

            // source1 만 삭제
            jdbc.update("DELETE FROM rss_items WHERE rss_source_id = ? AND created_at < ?",
                rssSourceId, Timestamp.from(cutoff)
            )

            val after1 = countRssItemsInSource()
            val after2 = jdbc.queryForObject(
                "SELECT COUNT(*) FROM rss_items WHERE rss_source_id = ?",
                Int::class.java,
                source2Id
            ) ?: 0

            // source1 은 3건 삭제, source2 는 무영향
            (before1 - after1) shouldBe 3
            (before2 - after2) shouldBe 0

            // Cleanup source2 와 category2 — rss_sources → batch_categories 순서 필수
            jdbc.update("DELETE FROM rss_items WHERE rss_source_id = ?", source2Id)
            jdbc.update("DELETE FROM rss_sources WHERE id = ?", source2Id)
            jdbc.update("DELETE FROM batch_categories WHERE id = ?", category2Id)
        }
    }
}
