package com.ohmyclipping.service

import com.ohmyclipping.service.dto.admin.ReviewPolicyStatus
import com.ohmyclipping.service.dto.analytics.ScoreDistribution
import com.ohmyclipping.service.dto.analytics.ScoreDistributionBucket
import com.ohmyclipping.service.query.ReviewPolicyQueryHelper
import com.ohmyclipping.store.BatchSummaryStore
import com.ohmyclipping.store.CategoryStore
import com.ohmyclipping.store.ReviewItemAuditStore
import com.ohmyclipping.store.ReviewItemDecisionStore
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * AdminReviewQueueService 의 대시보드 조회 메서드 단위 테스트.
 *
 * - getPolicyStatus(): queryHelper 결과를 response 로 래핑하고 generatedAt 을 기록하는지.
 * - getScoreDistribution(): days 파라미터 clamp 경계값과 전달 인자 검증.
 */
class AdminReviewQueueServicePolicyStatusTest {

    private val batchSummaryStore = mockk<BatchSummaryStore>()
    private val categoryStore = mockk<CategoryStore>()
    private val categoryRuleService = mockk<AdminCategoryRuleService>()
    private val reviewItemDecisionStore = mockk<ReviewItemDecisionStore>()
    private val reviewItemAuditStore = mockk<ReviewItemAuditStore>()
    private val reviewPolicyQueryHelper = mockk<ReviewPolicyQueryHelper>()
    private val ruleEvaluator = mockk<ReviewPolicyRuleEvaluator>()

    private lateinit var service: AdminReviewQueueService

    @BeforeEach
    fun setUp() {
        clearAllMocks()
        service = AdminReviewQueueService(
            batchSummaryStore = batchSummaryStore,
            categoryStore = categoryStore,
            categoryRuleService = categoryRuleService,
            reviewItemDecisionStore = reviewItemDecisionStore,
            reviewItemAuditStore = reviewItemAuditStore,
            reviewPolicyQueryHelper = reviewPolicyQueryHelper,
            ruleEvaluator = ruleEvaluator,
        )
    }

    @Nested
    inner class `getPolicyStatus` {

        @Test
        fun `queryHelper 결과를 그대로 response 로 감싼다`() {
            val mockList = listOf(
                ReviewPolicyStatus(
                    categoryId = "cat-1",
                    categoryName = "테스트 카테고리",
                    autoApproveThreshold = 0.8,
                    reviewThreshold = 0.4,
                    pendingReviewCount = 3,
                    last7DaysProcessed = 10,
                    last7DaysAutoApproved = 6,
                    last7DaysManuallyReviewed = 4,
                    avgScore = 0.55f,
                    eventTypeDistribution = mapOf("NEWS" to 7, "NULL" to 3),
                    lastReviewedAt = Instant.parse("2026-04-10T09:00:00Z"),
                ),
            )
            every { reviewPolicyQueryHelper.getPolicyStatus() } returns mockList

            val response = service.getPolicyStatus()

            response.categories shouldBe mockList
            response.generatedAt shouldNotBe null
        }

        @Test
        fun `빈 리스트도 response 래퍼로 반환한다`() {
            every { reviewPolicyQueryHelper.getPolicyStatus() } returns emptyList()

            val response = service.getPolicyStatus()

            response.categories shouldBe emptyList()
            response.generatedAt shouldNotBe null
        }
    }

    @Nested
    inner class `getScoreDistribution` {

        @Test
        fun `days 파라미터가 1 미만이면 1 로 clamp`() {
            every { reviewPolicyQueryHelper.getScoreDistribution(null, 1) } returns emptyScoreDistribution()

            service.getScoreDistribution(null, days = 0)

            verify(exactly = 1) { reviewPolicyQueryHelper.getScoreDistribution(null, 1) }
        }

        @Test
        fun `days 파라미터가 음수면 1 로 clamp`() {
            every { reviewPolicyQueryHelper.getScoreDistribution(null, 1) } returns emptyScoreDistribution()

            service.getScoreDistribution(null, days = -5)

            verify(exactly = 1) { reviewPolicyQueryHelper.getScoreDistribution(null, 1) }
        }

        @Test
        fun `days 파라미터가 90 초과면 90 으로 clamp`() {
            every { reviewPolicyQueryHelper.getScoreDistribution(null, 90) } returns emptyScoreDistribution()

            service.getScoreDistribution(null, days = 365)

            verify(exactly = 1) { reviewPolicyQueryHelper.getScoreDistribution(null, 90) }
        }

        @Test
        fun `정상 범위 days 는 그대로 전달`() {
            every { reviewPolicyQueryHelper.getScoreDistribution("cat1", 14) } returns emptyScoreDistribution()

            service.getScoreDistribution("cat1", days = 14)

            verify(exactly = 1) { reviewPolicyQueryHelper.getScoreDistribution("cat1", 14) }
        }

        @Test
        fun `queryHelper 결과를 그대로 반환한다`() {
            val expected = ScoreDistribution(
                buckets = (0 until 10).map { ScoreDistributionBucket("0.${it}-0.${it + 1}", it * 2) },
                totalCount = 90,
                medianScore = 0.45f,
                meanScore = 0.5f,
            )
            every { reviewPolicyQueryHelper.getScoreDistribution("cat1", 7) } returns expected

            val result = service.getScoreDistribution("cat1", days = 7)

            result shouldBe expected
        }
    }

    private fun emptyScoreDistribution() = ScoreDistribution(
        buckets = emptyList(),
        totalCount = 0,
        medianScore = 0.0f,
        meanScore = 0.0f,
    )
}
