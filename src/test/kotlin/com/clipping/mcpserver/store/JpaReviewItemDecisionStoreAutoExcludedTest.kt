package com.clipping.mcpserver.store

import com.clipping.mcpserver.model.BatchSummary
import com.clipping.mcpserver.model.Category
import com.clipping.mcpserver.model.ReviewDecisionStatus
import com.clipping.mcpserver.model.ReviewItemDecision
import com.clipping.mcpserver.model.RssItem
import com.clipping.mcpserver.model.RssSource
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

/**
 * [JpaReviewItemDecisionStore.findAutoExcluded] 의 LEFT JOIN 동작을 검증한다.
 *
 * 검증 포인트:
 *  1. 정상 케이스 — rss_items / rss_sources 가 살아있으면 sourceUrl / sourceName /
 *     publishedAt 이 채워져 반환된다 + 확장된 필드(summary/eventType/sentiment/
 *     categoryId/originalTitle/translatedTitle) 가 모두 전달된다.
 *  2. Orphan 케이스 — rss_items 에 해당하는 row 가 사라진 경우 (CASCADE 미설정의
 *     이론적 케이스) LEFT JOIN 으로 NULL 이 들어와도 NPE 없이 매핑되어야 한다.
 *     H2 의 `SET REFERENTIAL_INTEGRITY FALSE` 로 FK 를 잠시 꺼 orphan 을 인위적으로 만든다.
 *
 * 공유 H2 기반이라 delta-based 카운트 (before/after) 를 사용한다 (AGENTS.md §5.1).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class JpaReviewItemDecisionStoreAutoExcludedTest {

    @Autowired
    lateinit var reviewItemDecisionStore: ReviewItemDecisionStore

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

    @Test
    fun `정상 케이스 - 확장된 9 개 필드가 채워져 반환된다`() {
        val uniq = System.nanoTime().toString()
        val category = categoryStore.save(Category(id = "", name = "AutoExcl-OK-$uniq"))
        val source = sourceStore.save(
            RssSource(
                id = "",
                name = "Happy Source $uniq",
                url = "https://example.com/happy-$uniq/rss",
                categoryId = category.id,
            )
        )
        val publishedAt = Instant.parse("2026-04-17T09:00:00Z")
        val item = itemStore.save(
            RssItem(
                id = "",
                title = "original title $uniq",
                content = "seed content",
                link = "https://example.com/happy-item-$uniq",
                publishedAt = publishedAt,
                categoryId = category.id,
                rssSourceId = source.id,
            )
        )
        val summary = summaryStore.save(
            BatchSummary(
                id = "",
                originalTitle = "original title $uniq",
                translatedTitle = "번역된 제목 $uniq",
                summary = "이 기사는 자동 제외 drawer 용 fixture 입니다.",
                importanceScore = 0.2f,
                sourceLink = item.link,
                categoryId = category.id,
                rssItemId = item.id,
                sentiment = "NEUTRAL",
                eventType = "OTHER",
            )
        )
        val since = Instant.now().minus(Duration.ofDays(1))
        reviewItemDecisionStore.upsert(
            ReviewItemDecision(
                summaryId = summary.id,
                categoryId = category.id,
                status = ReviewDecisionStatus.EXCLUDE,
                suggestedStatus = ReviewDecisionStatus.EXCLUDE,
                reason = "rule:zero_signal",
                reviewedBy = "policy-auto",
                reviewedAt = Instant.now(),
            )
        )

        // 공유 H2 에서 다른 테스트 잔존 row 가 있을 수 있어 categoryId 로 scope 한다.
        val rows = reviewItemDecisionStore.findAutoExcluded(
            since = since,
            categoryId = category.id,
            reasonPrefix = null,
            limit = 10,
            offset = 0,
        )

        rows.size shouldBe 1
        val row = rows.single()
        row.summaryId shouldBe summary.id
        row.title shouldBe "번역된 제목 $uniq"
        row.originalTitle shouldBe "original title $uniq"
        row.translatedTitle shouldBe "번역된 제목 $uniq"
        row.categoryId shouldBe category.id
        row.categoryName shouldContain "AutoExcl-OK"
        row.summary shouldContain "drawer 용 fixture"
        row.sourceUrl shouldBe item.link
        row.sourceName shouldBe "Happy Source $uniq"
        row.publishedAt shouldBe publishedAt
        row.eventType shouldBe "OTHER"
        row.sentiment shouldBe "NEUTRAL"
        row.reason shouldBe "rule:zero_signal"
    }

    @Test
    fun `orphan 케이스 - rss_items 가 없으면 sourceUrl sourceName publishedAt 이 null 로 매핑된다`() {
        val uniq = System.nanoTime().toString()
        val category = categoryStore.save(Category(id = "", name = "AutoExcl-Orphan-$uniq"))
        val source = sourceStore.save(
            RssSource(
                id = "",
                name = "Orphan Source $uniq",
                url = "https://example.com/orphan-$uniq/rss",
                categoryId = category.id,
            )
        )
        val item = itemStore.save(
            RssItem(
                id = "",
                title = "orphan title $uniq",
                content = "seed content",
                link = "https://example.com/orphan-item-$uniq",
                publishedAt = Instant.parse("2026-04-16T08:00:00Z"),
                categoryId = category.id,
                rssSourceId = source.id,
            )
        )
        val summary = summaryStore.save(
            BatchSummary(
                id = "",
                originalTitle = "orphan title $uniq",
                summary = "orphan summary body",
                importanceScore = 0.1f,
                sourceLink = item.link,
                categoryId = category.id,
                rssItemId = item.id,
            )
        )
        val since = Instant.now().minus(Duration.ofDays(1))
        reviewItemDecisionStore.upsert(
            ReviewItemDecision(
                summaryId = summary.id,
                categoryId = category.id,
                status = ReviewDecisionStatus.EXCLUDE,
                suggestedStatus = ReviewDecisionStatus.EXCLUDE,
                reason = "rule:event_type_blacklist",
                reviewedBy = "policy-auto",
                reviewedAt = Instant.now(),
            )
        )

        // H2 specific: FK 검사를 잠시 끄고 rss_items row 를 삭제해 orphan 상태를 만든다.
        // PostgreSQL 에서는 ON DELETE CASCADE/SET NULL 이 없으므로 이런 orphan 은 실제로는
        // FK 위반으로 방지되지만, 이론적 안전 장치로 LEFT JOIN 이 NPE 없이 동작해야 함을 보장한다.
        jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE")
        try {
            jdbc.update("DELETE FROM rss_items WHERE id = ?", item.id)
        } finally {
            jdbc.execute("SET REFERENTIAL_INTEGRITY TRUE")
        }

        val rows = reviewItemDecisionStore.findAutoExcluded(
            since = since,
            categoryId = category.id,
            reasonPrefix = null,
            limit = 10,
            offset = 0,
        )

        rows.size shouldBe 1
        val row = rows.single()
        row.summaryId shouldBe summary.id
        // rss_items 가 사라졌으므로 nullable 필드는 null.
        row.sourceUrl shouldBe null
        row.sourceName shouldBe null
        row.publishedAt shouldBe null
        // 배치 레이어 필드는 여전히 살아있다.
        row.originalTitle shouldBe "orphan title $uniq"
        row.translatedTitle shouldBe null
        row.summary shouldBe "orphan summary body"
        row.categoryId shouldBe category.id
        row.categoryName shouldContain "AutoExcl-Orphan"
        row.reason shouldBe "rule:event_type_blacklist"
    }
}
