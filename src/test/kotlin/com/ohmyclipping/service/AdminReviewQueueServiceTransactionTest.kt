package com.ohmyclipping.service

import com.ohmyclipping.model.BatchSummary
import com.ohmyclipping.model.Category
import com.ohmyclipping.model.ReviewDecisionStatus
import com.ohmyclipping.model.ReviewItemAudit
import com.ohmyclipping.model.RssItem
import com.ohmyclipping.store.BatchSummaryStore
import com.ohmyclipping.store.CategoryStore
import com.ohmyclipping.store.ReviewItemAuditStore
import com.ohmyclipping.store.ReviewItemDecisionStore
import com.ohmyclipping.store.RssItemStore
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.doThrow
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
class AdminReviewQueueServiceTransactionTest {

    @Autowired
    lateinit var adminReviewQueueService: AdminReviewQueueService

    @Autowired
    lateinit var categoryStore: CategoryStore

    @Autowired
    lateinit var itemStore: RssItemStore

    @Autowired
    lateinit var summaryStore: BatchSummaryStore

    @Autowired
    lateinit var reviewItemDecisionStore: ReviewItemDecisionStore

    @MockitoBean
    lateinit var reviewItemAuditStore: ReviewItemAuditStore

    private lateinit var summaryId: String

    @BeforeEach
    fun setup() {
        val category = categoryStore.save(Category(id = "", name = "ReviewTxCat-${System.nanoTime()}"))
        val item = itemStore.save(
            RssItem(
                id = "",
                title = "Tx target item",
                content = "Tx target content",
                link = "https://example.com/review-tx-${System.nanoTime()}",
                categoryId = category.id
            )
        )
        val summary = summaryStore.save(
            BatchSummary(
                id = "",
                originalTitle = item.title,
                summary = "트랜잭션 롤백 검증 요약",
                keywords = listOf("tx"),
                importanceScore = 0.61f,
                sourceLink = item.link,
                categoryId = category.id,
                rssItemId = item.id
            )
        )
        summaryId = summary.id
    }

    @Test
    fun `approve should rollback decision when audit append fails`() {
        doThrow(IllegalStateException("audit append failure"))
            .`when`(reviewItemAuditStore)
            .append(anyReviewItemAudit())

        assertThrows(IllegalStateException::class.java) {
            adminReviewQueueService.approve(
                summaryId = summaryId,
                reason = "승인",
                reviewedBy = "tx-admin"
            )
        }

        assertNull(reviewItemDecisionStore.findBySummaryId(summaryId))
    }

    private fun anyReviewItemAudit(): ReviewItemAudit =
        any(ReviewItemAudit::class.java) ?: ReviewItemAudit(
            id = "",
            summaryId = "any-summary",
            categoryId = "any-category",
            toStatus = ReviewDecisionStatus.REVIEW
        )
}
