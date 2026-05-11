package com.ohmyclipping.store

import com.ohmyclipping.model.BatchSummary
import com.ohmyclipping.model.Category
import com.ohmyclipping.model.ReviewDecisionStatus
import com.ohmyclipping.model.ReviewItemDecision
import com.ohmyclipping.model.RssItem
import com.ohmyclipping.model.RssSource
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.time.Instant

@SpringBootTest
@ActiveProfiles("test")
class JdbcReviewItemDecisionStoreTest {

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

    @Test
    fun `upsert should preserve createdAt when existing decision is updated`() {
        val category = categoryStore.save(Category(id = "", name = "ReviewDecision-${System.nanoTime()}"))
        val source = sourceStore.save(
            RssSource(
                id = "",
                name = "ReviewDecisionSource",
                url = "https://example.com/review-${System.nanoTime()}/rss",
                categoryId = category.id
            )
        )
        val item = itemStore.save(
            RssItem(
                id = "",
                title = "review decision title",
                content = "seed content",
                link = "https://example.com/review-item-${System.nanoTime()}",
                categoryId = category.id,
                rssSourceId = source.id
            )
        )
        val summary = summaryStore.save(
            BatchSummary(
                id = "",
                originalTitle = "review decision title",
                summary = "review decision summary",
                importanceScore = 0.7f,
                sourceLink = item.link,
                categoryId = category.id,
                rssItemId = item.id
            )
        )

        val firstSaved = reviewItemDecisionStore.upsert(
            ReviewItemDecision(
                summaryId = summary.id,
                categoryId = category.id,
                status = ReviewDecisionStatus.REVIEW,
                reason = "initial",
                reviewedBy = "policy-auto",
                reviewedAt = Instant.now()
            )
        )
        val secondSaved = reviewItemDecisionStore.upsert(
            ReviewItemDecision(
                summaryId = summary.id,
                categoryId = category.id,
                status = ReviewDecisionStatus.EXCLUDE,
                reason = "updated",
                reviewedBy = "admin-reviewer",
                reviewedAt = Instant.now().plusSeconds(60)
            )
        )

        secondSaved.createdAt shouldBe firstSaved.createdAt
        secondSaved.status shouldBe ReviewDecisionStatus.EXCLUDE
        secondSaved.reason shouldBe "updated"
    }
}
