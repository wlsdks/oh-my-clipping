package com.ohmyclipping.model

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant

class DomainModelTest {

    @Test
    fun `승인 전이는 pending 요청에서만 허용된다`() {
        val createdAt = Instant.parse("2026-03-30T00:00:00Z")
        val request = pendingRequest(createdAt = createdAt)

        val approved = request.approve(
            reviewerUserId = "admin-1",
            slackChannelId = "C-APPROVED",
            reviewNote = "승인 완료",
            approvedCategoryId = "cat-1",
            approvedPersonaId = "persona-1",
            approvedSourceId = "source-1",
            reviewedAt = createdAt.plusSeconds(7200)
        )

        approved.isApproved() shouldBe true
        approved.reviewedByUserId shouldBe "admin-1"
        approved.slackChannelId shouldBe "C-APPROVED"
        approved.approvedCategoryId shouldBe "cat-1"
        approved.approvalLeadTimeHours() shouldBe 2.0
    }

    @Test
    fun `반려 전이는 pending 요청에서만 허용되고 반려 사유가 필요하다`() {
        val request = pendingRequest()

        shouldThrow<IllegalArgumentException> {
            request.reject(
                reviewerUserId = "admin-1",
                reviewNote = "   "
            )
        }

        val rejected = request.reject(
            reviewerUserId = "admin-1",
            reviewNote = " 부적합 소스 "
        )

        rejected.isRejected() shouldBe true
        rejected.rejectionReason() shouldBe "부적합 소스"
    }

    @Test
    fun `pending 요청만 철회할 수 있다`() {
        val pending = pendingRequest()
        pending.withdrawPending().isWithdrawn() shouldBe true

        shouldThrow<IllegalArgumentException> {
            approvedRequest().withdrawPending()
        }
    }

    @Test
    fun `approved 요청만 구독 해지할 수 있다`() {
        val approved = approvedRequest()
        approved.unsubscribeApproved().isWithdrawn() shouldBe true

        shouldThrow<IllegalArgumentException> {
            pendingRequest().unsubscribeApproved()
        }
    }

    @Test
    fun `구독 한도와 종료 상태 판정은 요청 상태를 기준으로 계산한다`() {
        pendingRequest().countsTowardSubscriptionLimit() shouldBe true
        approvedRequest().countsTowardSubscriptionLimit() shouldBe true
        rejectedRequest().countsTowardSubscriptionLimit() shouldBe false
        withdrawnRequest().countsTowardSubscriptionLimit() shouldBe false

        rejectedRequest().isDeletable() shouldBe true
        withdrawnRequest().isDeletable() shouldBe true
        approvedRequest().isDeletable() shouldBe false
    }

    @Test
    fun `리뷰 완료 시점 판정은 승인과 반려만 포함한다`() {
        val cutoff = Instant.parse("2026-03-29T00:00:00Z")

        approvedRequest(reviewedAt = Instant.parse("2026-03-30T00:00:00Z")).wasReviewedAfter(cutoff) shouldBe true
        rejectedRequest(reviewedAt = Instant.parse("2026-03-30T00:00:00Z")).wasReviewedAfter(cutoff) shouldBe true
        withdrawnRequest(reviewedAt = Instant.parse("2026-03-30T00:00:00Z")).wasReviewedAfter(cutoff) shouldBe false
        pendingRequest(reviewedAt = null).wasReviewedAfter(cutoff) shouldBe false
    }

    @Test
    fun `반려 사유는 반려 상태에서만 노출한다`() {
        rejectedRequest(reviewNote = " 운영 정책 위반 ").rejectionReason() shouldBe "운영 정책 위반"
        approvedRequest(reviewNote = "승인").rejectionReason().shouldBeNull()
    }

    @Test
    fun `요약은 같은 기사와 제목 링크 카테고리를 모두 만족해야 연결된다`() {
        val item = rssItem(categoryId = "cat-1")
        val summary = batchSummary(
            categoryId = "cat-1",
            rssItemId = item.id,
            originalTitle = item.title,
            sourceLink = item.link
        )

        summary.isLinkedTo(item) shouldBe true
        batchSummary(
            categoryId = "cat-2",
            rssItemId = item.id,
            originalTitle = item.title,
            sourceLink = item.link
        ).isLinkedTo(item) shouldBe false
    }

    @Test
    fun `LLM 실행은 기사 기반일 때만 기사 카테고리와 연결을 강제한다`() {
        val item = rssItem(categoryId = "cat-1")

        LlmRun(
            id = "run-free",
            categoryId = "cat-free",
            rssItemId = null,
            model = "gpt",
            promptVersion = "screening.v1",
            inputHash = "hash-free",
            inputChars = 10,
            outputChars = 5,
            status = "SUCCEEDED",
            durationMs = 1
        ).isLinkedTo(item) shouldBe true

        LlmRun(
            id = "run-bound",
            categoryId = "cat-2",
            rssItemId = item.id,
            model = "gpt",
            promptVersion = "article.v3",
            inputHash = "hash-bound",
            inputChars = 10,
            outputChars = 5,
            status = "FAILED",
            durationMs = 1
        ).isLinkedTo(item) shouldBe false
    }

    private fun pendingRequest(
        reviewNote: String? = null,
        reviewedAt: Instant? = null,
        createdAt: Instant = Instant.parse("2026-03-29T00:00:00Z")
    ) = UserClippingRequest(
        id = "req-1",
        requesterUserId = "user-1",
        requestName = "AI 뉴스",
        sourceName = "TechCrunch",
        sourceUrl = "https://example.com/rss",
        slackChannelId = "C-DEFAULT",
        personaName = "분석가",
        personaPrompt = "prompt",
        reviewNote = reviewNote,
        reviewedAt = reviewedAt,
        createdAt = createdAt,
        updatedAt = createdAt
    )

    private fun approvedRequest(
        reviewNote: String? = null,
        reviewedAt: Instant? = Instant.parse("2026-03-30T00:00:00Z")
    ) = pendingRequest(reviewNote = reviewNote, reviewedAt = reviewedAt).copy(
        status = UserClippingRequestStatus.APPROVED,
        approvedCategoryId = "cat-1"
    )

    private fun rejectedRequest(
        reviewNote: String? = "부적합",
        reviewedAt: Instant? = Instant.parse("2026-03-30T00:00:00Z")
    ) = pendingRequest(reviewNote = reviewNote, reviewedAt = reviewedAt).copy(
        status = UserClippingRequestStatus.REJECTED
    )

    private fun withdrawnRequest(
        reviewedAt: Instant? = null
    ) = pendingRequest(reviewedAt = reviewedAt).copy(
        status = UserClippingRequestStatus.WITHDRAWN
    )

    private fun rssItem(categoryId: String) = RssItem(
        id = "item-1",
        title = "AI headline",
        link = "https://example.com/article",
        categoryId = categoryId
    )

    private fun batchSummary(
        categoryId: String,
        rssItemId: String,
        originalTitle: String,
        sourceLink: String
    ) = BatchSummary(
        id = "summary-1",
        originalTitle = originalTitle,
        summary = "요약",
        importanceScore = 0.7f,
        sourceLink = sourceLink,
        categoryId = categoryId,
        rssItemId = rssItemId
    )
}
