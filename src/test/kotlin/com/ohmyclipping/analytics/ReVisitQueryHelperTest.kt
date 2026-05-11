package com.ohmyclipping.analytics

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ReVisitQueryHelperTest {

    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var helper: ReVisitQueryHelper

    private val fromTs: Instant = Instant.parse("2026-04-01T00:00:00Z")
    private val toTs: Instant = Instant.parse("2026-05-01T00:00:00Z")

    // FK 제약(user_events.user_id -> admin_users.id) 을 만족시키기 위한 고정 테스트 유저.
    private val testUserId: String = "revisit-user-" + UUID.randomUUID().toString().take(8)
    // FK 제약(user_events.summary_id -> batch_summaries.id) 을 위한 리소스 prefix.
    private val fixturePrefix: String = "revisit-" + UUID.randomUUID().toString().take(8)
    private val categoryId: String = "$fixturePrefix-cat"
    private val createdSummaries: MutableSet<String> = mutableSetOf()

    @BeforeEach
    fun clean() {
        // 크로스 테스트 격리: 우리 fixture 시간대만 정리
        jdbc.update(
            "DELETE FROM user_events WHERE event_type='article_click' AND created_at BETWEEN ? AND ?",
            Timestamp.from(fromTs), Timestamp.from(toTs)
        )
        // 방어적 잔재 제거 (트랜잭션 롤백 되지만 ID 충돌 대비)
        jdbc.update("DELETE FROM user_events WHERE user_id = ?", testUserId)
        jdbc.update("DELETE FROM admin_users WHERE id = ?", testUserId)

        // 유저 생성
        jdbc.update(
            """
            INSERT INTO admin_users (id, username, password_hash, display_name, is_active, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """.trimIndent(),
            testUserId,
            "revisit-${testUserId.takeLast(12)}",
            "x",
            "재방문 테스트 유저",
            true
        )
        // 카테고리 생성 (batch_summaries.category_id FK)
        jdbc.update(
            """
            INSERT INTO batch_categories (id, name, is_active, created_at, updated_at)
            VALUES (?, ?, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """.trimIndent(),
            categoryId,
            "재방문 테스트 카테고리"
        )
        createdSummaries.clear()
    }

    /** summary_id 에 대응하는 rss_item + batch_summary 를 lazily 생성한다. */
    private fun ensureSummary(summaryId: String) {
        val fullId = "$fixturePrefix-$summaryId"
        if (!createdSummaries.add(fullId)) return

        val rssItemId = "$fullId-rss"
        jdbc.update(
            """
            INSERT INTO rss_items (
                id, title, content, link, language, is_processed, category_id, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
            """.trimIndent(),
            rssItemId,
            "재방문 fixture item",
            "재방문 fixture content",
            "https://example.com/revisit/$fullId",
            "FOREIGN",
            true,
            categoryId
        )
        jdbc.update(
            """
            INSERT INTO batch_summaries (
                id, original_title, summary, source_link, is_sent_to_slack, category_id, rss_item_id, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
            """.trimIndent(),
            fullId,
            "재방문 fixture title",
            "재방문 fixture summary",
            "https://example.com/revisit/$fullId",
            false,
            categoryId,
            rssItemId
        )
    }

    private fun insertClick(userAlias: String, summaryId: String, at: Instant) {
        // userAlias 는 테스트 가독성 용 — 실제 FK 는 testUserId 로 고정.
        // 여러 유저 시나리오가 필요하면 추후 유저를 추가로 만들어 쓰면 됨.
        val fullSummaryId = "$fixturePrefix-$summaryId"
        ensureSummary(summaryId)
        jdbc.update(
            """
            INSERT INTO user_events (user_id, event_type, event_data, summary_id, created_at)
            VALUES (?, 'article_click', ?, ?, ?)
            """.trimIndent(),
            testUserId,
            "{\"summaryId\":\"$fullSummaryId\",\"url\":\"https://x\",\"alias\":\"$userAlias\"}",
            fullSummaryId,
            Timestamp.from(at)
        )
    }

    @Nested
    @DisplayName("ReVisitQueryHelper.countRevisits")
    inner class CountRevisits {

        @Test
        fun `첫 클릭과 24h 이후 재클릭은 재방문 1건`() {
            val first = Instant.parse("2026-04-10T09:00:00Z")
            val second = first.plus(25, ChronoUnit.HOURS)
            insertClick("u1", "s1", first)
            insertClick("u1", "s1", second)

            helper.countRevisits(fromTs, toTs) shouldBe 1
        }

        @Test
        fun `24h 이내 재클릭은 재방문 아님`() {
            val first = Instant.parse("2026-04-10T09:00:00Z")
            val second = first.plus(10, ChronoUnit.HOURS)
            insertClick("u1", "s1", first)
            insertClick("u1", "s1", second)

            helper.countRevisits(fromTs, toTs) shouldBe 0
        }

        @Test
        fun `30d 초과 재클릭도 재방문 아님`() {
            val first = Instant.parse("2026-04-01T09:00:00Z")
            val second = first.plus(31, ChronoUnit.DAYS)
            insertClick("u1", "s1", first)
            insertClick("u1", "s1", second)

            helper.countRevisits(fromTs, toTs) shouldBe 0
        }

        @Test
        fun `다른 summary 는 독립적으로 카운트`() {
            val t = Instant.parse("2026-04-10T09:00:00Z")
            insertClick("u1", "s1", t)
            insertClick("u1", "s1", t.plus(25, ChronoUnit.HOURS))
            insertClick("u1", "s2", t)
            insertClick("u1", "s2", t.plus(25, ChronoUnit.HOURS))

            helper.countRevisits(fromTs, toTs) shouldBe 2
        }

        @Test
        fun `사용자 한 명이 3번 이상 찍어도 재방문은 1건 (조합별 1카운트)`() {
            val first = Instant.parse("2026-04-10T09:00:00Z")
            insertClick("u1", "s1", first)
            insertClick("u1", "s1", first.plus(25, ChronoUnit.HOURS))
            insertClick("u1", "s1", first.plus(48, ChronoUnit.HOURS))

            helper.countRevisits(fromTs, toTs) shouldBe 1
        }

        @Test
        fun `윈도우 밖 클릭만 있으면 0`() {
            insertClick("u1", "s1", Instant.parse("2026-03-15T09:00:00Z"))  // fromTs 이전
            helper.countRevisits(fromTs, toTs) shouldBe 0
        }
    }
}
