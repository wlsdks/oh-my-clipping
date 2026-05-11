package com.clipping.mcpserver.store

import com.clipping.mcpserver.model.BatchSummary
import com.clipping.mcpserver.model.Category
import com.clipping.mcpserver.model.ReviewDecisionStatus
import com.clipping.mcpserver.model.RssItem
import com.clipping.mcpserver.model.RssSource
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import java.sql.Timestamp
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * `ReviewItemAuditStore.deleteOlderThan` 청크 DELETE 동작을 검증한다.
 * JpaReviewItemAuditStore가 @Primary이므로 ReviewItemAuditStore 빈은 JPA 구현이 주입된다.
 */
@SpringBootTest
@ActiveProfiles("test")
class JpaReviewItemAuditStoreTest {

    @Autowired
    lateinit var auditStore: ReviewItemAuditStore

    @Autowired
    lateinit var categoryStore: CategoryStore

    @Autowired
    lateinit var sourceStore: RssSourceStore

    @Autowired
    lateinit var itemStore: RssItemStore

    @Autowired
    lateinit var summaryStore: BatchSummaryStore

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @BeforeEach
    fun cleanAudits() {
        // SpringBootTest는 Spring context를 공유하여 H2가 재사용되므로,
        // 각 테스트가 작성한 audit row가 다음 테스트로 누출되지 않도록 초기화한다.
        jdbc.update("DELETE FROM clipping_review_item_audits")
    }

    @Test
    fun `deleteOlderThan은 cutoff 이전 감사 이력만 지우고 이후는 보존한다`() {
        val (summaryId, categoryId) = seedFixture()
        val now = Instant.now()
        val cutoff = now.minus(1095, ChronoUnit.DAYS)

        val oldIds = listOf(
            insertAudit(summaryId, categoryId, now.minus(1200, ChronoUnit.DAYS)),
            insertAudit(summaryId, categoryId, now.minus(1100, ChronoUnit.DAYS)),
            insertAudit(summaryId, categoryId, now.minus(1096, ChronoUnit.DAYS)),
        )
        val keepIds = listOf(
            insertAudit(summaryId, categoryId, now.minus(1000, ChronoUnit.DAYS)),
            insertAudit(summaryId, categoryId, now.minus(1, ChronoUnit.DAYS)),
        )

        val deleted = auditStore.deleteOlderThan(cutoff, limit = 100)

        deleted shouldBe 3
        oldIds.forEach { countById(it) shouldBe 0 }
        keepIds.forEach { countById(it) shouldBe 1 }
    }

    @Test
    fun `deleteOlderThan은 limit보다 많은 대상이 있으면 limit만큼만 지운다`() {
        val (summaryId, categoryId) = seedFixture()
        val now = Instant.now()
        val cutoff = now.minus(1095, ChronoUnit.DAYS)

        repeat(5) { idx ->
            insertAudit(summaryId, categoryId, now.minus(1100L + idx, ChronoUnit.DAYS))
        }

        val firstBatch = auditStore.deleteOlderThan(cutoff, limit = 2)
        val secondBatch = auditStore.deleteOlderThan(cutoff, limit = 2)
        val thirdBatch = auditStore.deleteOlderThan(cutoff, limit = 2)
        val fourthBatch = auditStore.deleteOlderThan(cutoff, limit = 2)

        firstBatch shouldBe 2
        secondBatch shouldBe 2
        thirdBatch shouldBe 1
        fourthBatch shouldBe 0
    }

    @Test
    fun `deleteOlderThan은 limit이 0 이하이면 IllegalArgumentException을 던진다`() {
        // Spring @Transactional + JPA 환경에서 IllegalArgumentException은
        // InvalidDataAccessApiUsageException으로 래핑될 수 있어 root cause를 확인한다.
        val zeroException = assertThrows<Throwable> {
            auditStore.deleteOlderThan(Instant.now(), limit = 0)
        }
        assertIsIllegalArgumentCause(zeroException)

        val negativeException = assertThrows<Throwable> {
            auditStore.deleteOlderThan(Instant.now(), limit = -1)
        }
        assertIsIllegalArgumentCause(negativeException)
    }

    private fun assertIsIllegalArgumentCause(error: Throwable) {
        // 래핑 여부와 무관하게 원인 체인에 IllegalArgumentException이 있어야 한다.
        var cursor: Throwable? = error
        while (cursor != null) {
            if (cursor is IllegalArgumentException) return
            cursor = cursor.cause
        }
        throw AssertionError("expected IllegalArgumentException in cause chain of $error")
    }

    @Test
    fun `deleteOlderThan은 정확히 cutoff 시각의 row는 보존한다 (엄격한 미만 조건)`() {
        val (summaryId, categoryId) = seedFixture()
        val now = Instant.now()
        val cutoff = now.minus(1095, ChronoUnit.DAYS)

        // 정확히 cutoff에 생성된 row는 `< cutoff` 조건을 만족하지 않아 보존된다
        val boundaryId = insertAudit(summaryId, categoryId, cutoff)
        val staleId = insertAudit(summaryId, categoryId, cutoff.minusSeconds(1))

        val deleted = auditStore.deleteOlderThan(cutoff, limit = 100)

        deleted shouldBe 1
        countById(boundaryId) shouldBe 1
        countById(staleId) shouldBe 0
    }

    // ── private helpers ──

    /**
     * audit가 참조하는 부모 데이터(Category → Source → Item → Summary)를 생성해
     * FK 제약을 만족시킨다. 매번 랜덤 suffix로 고유성을 확보한다.
     */
    private fun seedFixture(): Pair<String, String> {
        val category = categoryStore.save(
            Category(id = "", name = "AuditRetention-${System.nanoTime()}")
        )
        val source = sourceStore.save(
            RssSource(
                id = "",
                name = "RetentionSource",
                url = "https://example.com/retention-${System.nanoTime()}/rss",
                categoryId = category.id
            )
        )
        val item = itemStore.save(
            RssItem(
                id = "",
                title = "retention seed title",
                content = "retention seed content",
                link = "https://example.com/retention-item-${System.nanoTime()}",
                categoryId = category.id,
                rssSourceId = source.id
            )
        )
        val summary = summaryStore.save(
            BatchSummary(
                id = "",
                originalTitle = "retention seed title",
                summary = "retention seed summary",
                importanceScore = 0.5f,
                sourceLink = item.link,
                categoryId = category.id,
                rssItemId = item.id
            )
        )
        return summary.id to category.id
    }

    /** 특정 createdAt을 가진 감사 이력을 JdbcTemplate으로 직접 삽입한다 (테스트 전용). */
    private fun insertAudit(summaryId: String, categoryId: String, createdAt: Instant): String {
        val id = UUID.randomUUID().toString()
        jdbc.update(
            """
            INSERT INTO clipping_review_item_audits
                (id, summary_id, category_id, from_status, to_status, reason, reviewed_by, reviewed_at, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            id,
            summaryId,
            categoryId,
            ReviewDecisionStatus.REVIEW.name,
            ReviewDecisionStatus.EXCLUDE.name,
            "test",
            "retention-test",
            Timestamp.from(createdAt),
            Timestamp.from(createdAt)
        )
        return id
    }

    private fun countById(id: String): Int =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM clipping_review_item_audits WHERE id = ?",
            Int::class.java,
            id
        ) ?: 0
}
