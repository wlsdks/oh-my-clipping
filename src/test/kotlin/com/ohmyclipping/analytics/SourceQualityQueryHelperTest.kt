package com.ohmyclipping.analytics

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SourceQualityQueryHelperTest {

    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var helper: SourceQualityQueryHelper

    // 다른 (비 @Transactional) 테스트가 commit 해 둔 데이터와 충돌하지 않도록
    // "안전한 과거" 구간 (2024-01) 을 윈도우로 사용.
    private val fromTs: Instant = Instant.parse("2024-01-01T00:00:00Z")
    private val toTs: Instant = Instant.parse("2024-02-01T00:00:00Z")
    private val seedAt: Instant = Instant.parse("2024-01-15T12:00:00Z")
    // VARCHAR(36) 한도 고려. "sqh" + 8자 UUID prefix ≈ 13자
    private val prefix = "sqh${UUID.randomUUID().toString().take(8)}-"

    @BeforeEach
    fun clean() {
        jdbc.update("DELETE FROM user_events WHERE summary_id LIKE ?", "$prefix%")
        jdbc.update("DELETE FROM summary_feedback WHERE summary_id LIKE ?", "$prefix%")
        jdbc.update("DELETE FROM batch_summaries WHERE id LIKE ?", "$prefix%")
        jdbc.update("DELETE FROM rss_items WHERE id LIKE ?", "$prefix%")
        jdbc.update("DELETE FROM rss_sources WHERE id LIKE ?", "$prefix%")
        jdbc.update("DELETE FROM batch_categories WHERE id LIKE ?", "$prefix%")
        jdbc.update("DELETE FROM admin_users WHERE id LIKE ?", "$prefix%")
        seededUsers.clear()
    }

    private val seededUsers = mutableSetOf<String>()

    private fun seedUser(id: String) {
        val fullId = "$prefix$id"
        jdbc.update(
            """INSERT INTO admin_users (id, username, password_hash, is_active, created_at, updated_at)
               VALUES (?, ?, 'x', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)""",
            fullId, fullId
        )
    }

    private fun seedCategory(id: String) {
        jdbc.update(
            """INSERT INTO batch_categories (id, name, is_active, is_public, max_items,
                status, created_at, updated_at, system_updated_at)
               VALUES (?, ?, TRUE, FALSE, 5, 'ACTIVE',
                CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)""",
            "$prefix$id", "cat-$id"
        )
    }

    private fun seedSource(id: String, name: String, categorySuffix: String) {
        // rss_sources.category_id NOT NULL
        jdbc.update(
            """INSERT INTO rss_sources (id, name, url, category_id, is_active, created_at, updated_at)
               VALUES (?, ?, 'https://x/rss', ?, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)""",
            "$prefix$id", name, "$prefix$categorySuffix"
        )
    }

    // updatedAt round-trip 검증 및 비활성 상태 검증용으로 is_active/updated_at 을 명시적으로 세팅.
    private fun seedSourceWithState(
        id: String,
        name: String,
        categorySuffix: String,
        isActive: Boolean,
        updatedAt: Instant,
    ) {
        jdbc.update(
            """INSERT INTO rss_sources (id, name, url, category_id, is_active, created_at, updated_at)
               VALUES (?, ?, 'https://x/rss', ?, ?, CURRENT_TIMESTAMP, ?)""",
            "$prefix$id", name, "$prefix$categorySuffix", isActive, Timestamp.from(updatedAt)
        )
    }

    private fun seedSummary(summaryId: String, categorySuffix: String, sourceSuffix: String?, at: Instant) {
        val itemId = "$prefix$summaryId-i"
        val sourceId = sourceSuffix?.let { "$prefix$it" }
        val categoryId = "$prefix$categorySuffix"
        jdbc.update(
            """INSERT INTO rss_items (id, rss_source_id, title, link, published_at,
                is_processed, category_id, created_at)
               VALUES (?, ?, 't', ?, ?, TRUE, ?, ?)""",
            itemId, sourceId, "https://x/$itemId",
            Timestamp.from(at), categoryId, Timestamp.from(at)
        )
        jdbc.update(
            """INSERT INTO batch_summaries (id, category_id, rss_item_id, original_title,
                translated_title, summary, source_link, created_at, is_sent_to_slack)
               VALUES (?, ?, ?, 'orig', 'kr', 'sum', 'https://x/sum', ?, TRUE)""",
            "$prefix$summaryId", categoryId, itemId, Timestamp.from(at)
        )
    }

    private fun seedClick(userId: String, summaryId: String, at: Instant) {
        val fullUserId = "$prefix$userId"
        if (seededUsers.add(fullUserId)) seedUser(userId)
        jdbc.update(
            """INSERT INTO user_events (user_id, event_type, event_data, summary_id, created_at)
               VALUES (?, 'article_click', '{}', ?, ?)""",
            fullUserId, "$prefix$summaryId", Timestamp.from(at)
        )
    }

    @Test
    fun `소스별 발송수 + 유니크 사용자 클릭 집계`() {
        seedCategory("c1")
        seedSource("srcA", "Source A", "c1")
        seedSummary("s1", "c1", "srcA", seedAt)
        seedSummary("s2", "c1", "srcA", seedAt.plusSeconds(3600))
        seedClick("u1", "s1", seedAt.plusSeconds(60))
        seedClick("u1", "s1", seedAt.plusSeconds(120))  // 같은 user 중복
        seedClick("u2", "s2", seedAt.plusSeconds(3660))

        val result = helper.sourceQuality(fromTs, toTs)
            .filter { it.sourceId == "${prefix}srcA" }

        result shouldHaveSize 1
        val row = result.single()
        row.delivered shouldBe 2
        // distinct (user, summary): (u1, s1), (u2, s2) = 2
        row.uniqueUserClicks shouldBe 2
    }

    @Test
    fun `수동 URL (rss_source_id NULL) 은 '수동 URL' 버킷`() {
        seedCategory("c1")
        seedSummary("s1", "c1", null, seedAt)
        seedClick("u1", "s1", seedAt.plusSeconds(60))

        val result = helper.sourceQuality(fromTs, toTs)
            .filter { it.sourceName == "(수동 URL)" && it.sourceId == null }

        result shouldHaveSize 1
        result.single().delivered shouldBe 1
        result.single().uniqueUserClicks shouldBe 1
    }

    @Test
    fun `is_sent_to_slack = FALSE 인 summary 는 분모에서 제외`() {
        seedCategory("c1")
        seedSource("srcA", "A", "c1")
        jdbc.update(
            """INSERT INTO rss_items (id, rss_source_id, title, link, published_at,
                is_processed, category_id, created_at)
               VALUES (?, ?, 't', ?, ?, TRUE, ?, ?)""",
            "${prefix}s1-i", "${prefix}srcA", "https://x/${prefix}s1",
            Timestamp.from(seedAt), "${prefix}c1", Timestamp.from(seedAt)
        )
        jdbc.update(
            """INSERT INTO batch_summaries (id, category_id, rss_item_id, original_title,
                translated_title, summary, source_link, created_at, is_sent_to_slack)
               VALUES (?, ?, ?, 'o', 'k', 's', 'https://x/sum', ?, FALSE)""",
            "${prefix}s1", "${prefix}c1", "${prefix}s1-i", Timestamp.from(seedAt)
        )

        val result = helper.sourceQuality(fromTs, toTs)
            .filter { it.sourceId == "${prefix}srcA" }
        result shouldHaveSize 0
    }

    @Test
    fun `발송 0 이면 click_rate null`() {
        seedCategory("c1")
        seedSource("srcEmpty", "Empty", "c1")
        // 발송 summary 없음
        val result = helper.sourceQuality(fromTs, toTs)
            .filter { it.sourceId == "${prefix}srcEmpty" }
        // 결과 포함 안 됨 (JOIN 실패)
        result shouldHaveSize 0
    }

    @Test
    fun `isActive + updatedAt 가 row 에 round-trip 된다`() {
        val pinnedUpdatedAt = Instant.parse("2026-04-10T00:00:00Z")
        seedCategory("c1")
        seedSourceWithState(
            id = "srcPinned",
            name = "Pinned",
            categorySuffix = "c1",
            isActive = true,
            updatedAt = pinnedUpdatedAt,
        )
        seedSummary("s1", "c1", "srcPinned", seedAt)

        val row = helper.sourceQuality(fromTs, toTs)
            .first { it.sourceId == "${prefix}srcPinned" }

        row.isActive shouldBe true
        row.updatedAt shouldBe pinnedUpdatedAt
    }

    @Test
    fun `비활성 소스는 isActive = false 로 내려오고 updatedAt 이 보존된다`() {
        val deactivatedAt = Instant.parse("2026-04-12T08:30:00Z")
        seedCategory("c1")
        seedSourceWithState(
            id = "srcOff",
            name = "Deactivated",
            categorySuffix = "c1",
            isActive = false,
            updatedAt = deactivatedAt,
        )
        seedSummary("s1", "c1", "srcOff", seedAt)

        val row = helper.sourceQuality(fromTs, toTs)
            .first { it.sourceId == "${prefix}srcOff" }

        row.isActive shouldBe false
        row.updatedAt shouldBe deactivatedAt
    }

    @Test
    fun `수동 URL (rss_source_id NULL) 의 isActive 는 true, updatedAt 은 EPOCH`() {
        seedCategory("c1")
        seedSummary("s1", "c1", null, seedAt)
        seedClick("u1", "s1", seedAt.plusSeconds(60))

        val row = helper.sourceQuality(fromTs, toTs)
            .first { it.sourceId == null && it.sourceName == "(수동 URL)" }

        // 수동 URL 경로는 rs.* 가 LEFT JOIN 결과 NULL 이라 fallback 값이 채워진다.
        row.isActive shouldBe true
        row.updatedAt shouldBe Instant.EPOCH
    }
}
