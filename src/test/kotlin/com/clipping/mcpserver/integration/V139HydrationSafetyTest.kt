package com.clipping.mcpserver.integration

import com.clipping.mcpserver.error.InvalidInputException
import com.clipping.mcpserver.model.BatchSummary
import com.clipping.mcpserver.repository.BatchSummaryRepository
import com.clipping.mcpserver.store.BatchSummaryStore
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
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
import java.util.UUID

/**
 * V139 ON DELETE SET NULL 이후 JPA 하이드레이션 안전성 회귀 테스트.
 *
 * 30일 이상 보존된 앵커드 요약(북마크/피드백)은 rss_item이 삭제될 때
 * rss_item_id 가 NULL 로 설정된다. 이 테스트는 해당 시나리오에서
 * JPA 하이드레이션이 NPE/예외 없이 성공하고, BatchSummaryStore 가
 * null-safe 하게 처리함을 검증한다.
 *
 * 격리 전략:
 * - 고유한 categoryId + rssSourceId 로 이 테스트가 seed 한 row 만 범위를 잡는다.
 * - @AfterEach 에서 수동으로 test data 를 정리한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class V139HydrationSafetyTest {

    @Autowired
    lateinit var batchSummaryRepository: BatchSummaryRepository

    @Autowired
    lateinit var batchSummaryStore: BatchSummaryStore

    @Autowired
    lateinit var jdbc: JdbcTemplate

    private lateinit var categoryId: String
    private lateinit var rssSourceId: String
    private lateinit var testUserId: String

    @BeforeEach
    fun setup() {
        categoryId = UUID.randomUUID().toString()
        rssSourceId = UUID.randomUUID().toString()
        testUserId = UUID.randomUUID().toString()

        jdbc.update(
            "INSERT INTO batch_categories (id, name, created_at) VALUES (?, ?, CURRENT_TIMESTAMP)",
            categoryId, "V139HydrationTest-${System.nanoTime()}"
        )
        jdbc.update(
            "INSERT INTO rss_sources (id, name, url, is_active, category_id) VALUES (?, ?, ?, ?, ?)",
            rssSourceId, "V139 Test Source",
            "https://v139-test.example.com/feed-$rssSourceId", true, categoryId
        )
        jdbc.update(
            """
            INSERT INTO admin_users (id, username, password_hash, is_active, created_at, updated_at)
            VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """.trimIndent(),
            testUserId, "v139-hydration-${testUserId.takeLast(8)}", "placeholder-hash", true
        )
    }

    @AfterEach
    fun cleanup() {
        // FK 의존 순서 (자식 → 부모)
        jdbc.update("DELETE FROM summary_feedback WHERE user_id = ?", testUserId)
        jdbc.update("DELETE FROM bookmarked_articles WHERE user_id = ?", testUserId)
        jdbc.update("DELETE FROM batch_summaries WHERE category_id = ?", categoryId)
        jdbc.update("DELETE FROM rss_sources WHERE id = ?", rssSourceId)
        jdbc.update("DELETE FROM batch_categories WHERE id = ?", categoryId)
        jdbc.update("DELETE FROM admin_users WHERE id = ?", testUserId)
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun insertRssItem(): String {
        val id = UUID.randomUUID().toString()
        jdbc.update(
            """
            INSERT INTO rss_items
                (id, title, content, link, language, is_processed, category_id, rss_source_id, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
            """.trimIndent(),
            id, "V139 Test Article", "Test content",
            "https://v139-test.example.com/article/$id",
            "FOREIGN", false, categoryId, rssSourceId
        )
        return id
    }

    /** batch_summaries 에 지정된 rssItemId 로 row 를 직접 삽입한다. */
    private fun insertBatchSummary(rssItemId: String?): String {
        val id = UUID.randomUUID().toString()
        jdbc.update(
            """
            INSERT INTO batch_summaries
                (id, original_title, summary, importance_score, source_link,
                 is_sent_to_slack, category_id, rss_item_id, is_fallback, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
            """.trimIndent(),
            id, "V139 Test Summary Title", "Summary text", 0.5f,
            "https://v139-test.example.com/summary-$id",
            false, categoryId, rssItemId, false
        )
        return id
    }

    // ── tests ─────────────────────────────────────────────────────────────────

    @Nested
    inner class `JPA 하이드레이션 안전성` {

        @Test
        fun `rss_item_id 가 NULL 인 요약을 JPA 로 로드할 때 예외 없이 성공하고 rssItemId 는 null 이다`() {
            // 1. rssItemId가 NULL인 row를 직접 삽입 (V139 ON DELETE SET NULL 시뮬레이션)
            val summaryId = insertBatchSummary(rssItemId = null)

            // 2. JPA findById 로 하이드레이션 시도
            val entity = batchSummaryRepository.findById(summaryId).orElse(null)

            // 3. 로드 성공 + rssItemId null 확인
            entity.shouldNotBeNull()
            entity.rssItemId.shouldBeNull()
        }

        @Test
        fun `rss_item 삭제 후 SET NULL 시뮬레이션 — 요약 재로드 시 rssItemId 가 null 이다`() {
            // 1. rss_item + summary를 정상 연결해서 삽입
            val rssItemId = insertRssItem()
            val summaryId = insertBatchSummary(rssItemId = rssItemId)

            // 연결 확인
            val before = batchSummaryRepository.findById(summaryId).orElse(null)
            before.shouldNotBeNull()
            before.rssItemId shouldBe rssItemId

            // 2. ON DELETE SET NULL을 직접 UPDATE로 시뮬레이션 (retention이 rss_item 삭제 시 발생)
            jdbc.update("UPDATE batch_summaries SET rss_item_id = NULL WHERE id = ?", summaryId)
            jdbc.update("DELETE FROM rss_items WHERE id = ?", rssItemId)

            // 3. JPA 재로드 — NPE 없이 null 반환해야 한다
            val after = batchSummaryRepository.findById(summaryId).orElse(null)
            after.shouldNotBeNull()
            after.rssItemId.shouldBeNull()
        }

        @Test
        fun `BatchSummaryStore findById 로 NULL rssItemId 요약 로드 시 예외 없이 성공한다`() {
            val summaryId = insertBatchSummary(rssItemId = null)

            // Store 경유 로드 — null-safe rowMapper가 String?으로 반환해야 한다
            val summary = batchSummaryStore.findById(summaryId)

            summary.shouldNotBeNull()
            summary.rssItemId.shouldBeNull()
            summary.id shouldBe summaryId
        }
    }

    @Nested
    inner class `INSERT 불변식 — 신규 요약에 rssItemId 필수` {

        @Test
        fun `신규 BatchSummary 에 rssItemId 가 null 이면 InvalidInputException 을 던진다`() {
            val newSummary = BatchSummary(
                id = "",  // 신규 — id 공백
                originalTitle = "Test",
                summary = "Test summary",
                sourceLink = "https://example.com/test",
                categoryId = categoryId,
                rssItemId = null  // 신규 요약에 rssItemId 없음 → 거부
            )

            shouldThrow<InvalidInputException> {
                batchSummaryStore.save(newSummary)
            }
        }
    }

    @Nested
    inner class `NULL rssItemId 요약의 도메인 메서드 안전성` {

        @Test
        fun `rssItemId 가 null 인 BatchSummary 를 findByCategoryId 로 조회해도 예외 없이 반환된다`() {
            insertBatchSummary(rssItemId = null)

            // category 단위 조회 — null rssItemId가 포함돼도 리스트 반환 성공
            val summaries = batchSummaryStore.findByCategoryId(categoryId)

            summaries.shouldNotBe(emptyList<BatchSummary>())
            val target = summaries.find { it.categoryId == categoryId }
            target.shouldNotBeNull()
            target.rssItemId.shouldBeNull()
        }
    }
}
