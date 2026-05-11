package com.ohmyclipping.service

import com.ohmyclipping.model.UserClippingRequest
import com.ohmyclipping.model.UserClippingRequestStatus
import com.ohmyclipping.store.UserClippingRequestCountRow
import com.ohmyclipping.store.UserClippingRequestStore
import com.ohmyclipping.store.UserClippingRequestStatsSnapshot
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * UserClippingRequestStatsService 통계 집계 검증.
 */
class UserClippingRequestStatsServiceTest {

    private val requestStore = mockk<UserClippingRequestStore>()
    private val now: Instant = Instant.parse("2026-05-01T00:00:00Z")
    private val clock: Clock = Clock.fixed(now, ZoneId.of("UTC"))

    private lateinit var service: UserClippingRequestStatsService

    @BeforeEach
    fun setUp() {
        service = UserClippingRequestStatsService(requestStore, clock)
    }

    /** 테스트 픽스처: PENDING 상태 요청 생성 */
    private fun pending(
        id: String,
        requestName: String = "주제-$id",
        createdAt: Instant = now.minus(1, ChronoUnit.DAYS)
    ) = UserClippingRequest(
        id = id,
        requesterUserId = "user-$id",
        requestName = requestName,
        sourceName = "source-$id",
        sourceUrl = "https://example.com/$id",
        slackChannelId = "C_$id",
        personaName = "persona-$id",
        personaPrompt = "prompt",
        status = UserClippingRequestStatus.PENDING,
        createdAt = createdAt,
        updatedAt = createdAt
    )

    /** 테스트 픽스처: APPROVED 상태 요청 생성 (leadTime 은 createdAt → reviewedAt 차이) */
    private fun approved(
        id: String,
        requestName: String = "주제-$id",
        createdAt: Instant = now.minus(3, ChronoUnit.DAYS),
        reviewedAt: Instant = now.minus(1, ChronoUnit.DAYS)
    ) = UserClippingRequest(
        id = id,
        requesterUserId = "user-$id",
        requestName = requestName,
        sourceName = "source-$id",
        sourceUrl = "https://example.com/$id",
        slackChannelId = "C_$id",
        personaName = "persona-$id",
        personaPrompt = "prompt",
        status = UserClippingRequestStatus.APPROVED,
        approvedCategoryId = "cat-$id",
        reviewedByUserId = "admin",
        reviewedAt = reviewedAt,
        createdAt = createdAt,
        updatedAt = reviewedAt
    )

    /** 테스트 픽스처: REJECTED 상태 요청 생성 (reviewNote 가 반려 사유) */
    private fun rejected(
        id: String,
        reason: String,
        reviewedAt: Instant = now.minus(2, ChronoUnit.DAYS),
        requestName: String = "주제-$id"
    ) = UserClippingRequest(
        id = id,
        requesterUserId = "user-$id",
        requestName = requestName,
        sourceName = "source-$id",
        sourceUrl = "https://example.com/$id",
        slackChannelId = "C_$id",
        personaName = "persona-$id",
        personaPrompt = "prompt",
        status = UserClippingRequestStatus.REJECTED,
        reviewNote = reason,
        reviewedByUserId = "admin",
        reviewedAt = reviewedAt,
        createdAt = reviewedAt.minus(1, ChronoUnit.DAYS),
        updatedAt = reviewedAt
    )

    @Nested
    inner class HappyPath {

        @Test
        fun `상태별 건수와 전체 합계를 정확히 계산한다`() {
            // 2 pending, 3 approved, 1 rejected — 총 6건
            val requests = listOf(
                pending("p1"), pending("p2"),
                approved("a1"), approved("a2"), approved("a3"),
                rejected("r1", "부적절한 소스")
            )
            every { requestStore.getStatsSnapshot(any()) } returns statsSnapshotFrom(requests)

            val result = service.getRequestStats()

            result.pendingCount shouldBe 2
            result.approvedCount shouldBe 3
            result.rejectedCount shouldBe 1
            result.totalCount shouldBe 6
            verify(exactly = 0) { requestStore.listAll(any()) }
        }

        @Test
        fun `평균 승인 소요시간을 시간 단위로 소수 1자리까지 반환한다`() {
            // createdAt → reviewedAt 차이: 48h, 72h → 평균 60.0h
            val requests = listOf(
                approved("a1",
                    createdAt = now.minus(4, ChronoUnit.DAYS),
                    reviewedAt = now.minus(2, ChronoUnit.DAYS)),
                approved("a2",
                    createdAt = now.minus(5, ChronoUnit.DAYS),
                    reviewedAt = now.minus(2, ChronoUnit.DAYS))
            )
            every { requestStore.getStatsSnapshot(any()) } returns statsSnapshotFrom(requests)

            val result = service.getRequestStats()

            result.avgApprovalHours shouldBe 60.0
        }

        @Test
        fun `토픽 랭킹은 같은 requestName 으로 묶여 count 내림차순 TOP 5 를 반환한다`() {
            // "AI" 3건, "핀테크" 2건, "보안" 1건
            val requests = listOf(
                pending("p1", requestName = "AI"),
                pending("p2", requestName = "AI"),
                approved("a1", requestName = "AI"),
                pending("p3", requestName = "핀테크"),
                approved("a2", requestName = "핀테크"),
                pending("p4", requestName = "보안")
            )
            every { requestStore.getStatsSnapshot(any()) } returns statsSnapshotFrom(requests)

            val result = service.getRequestStats()

            result.topTopics shouldHaveSize 3
            result.topTopics[0].requestName shouldBe "AI"
            result.topTopics[0].count shouldBe 3
            result.topTopics[1].requestName shouldBe "핀테크"
            result.topTopics[1].count shouldBe 2
            result.topTopics[2].requestName shouldBe "보안"
            result.topTopics[2].count shouldBe 1
        }

        @Test
        fun `토픽 랭킹은 최대 5개까지만 반환한다`() {
            // 6개의 서로 다른 topic → TOP 5만 반환
            val requests = (1..6).map { pending("p$it", requestName = "topic$it") }
            every { requestStore.getStatsSnapshot(any()) } returns statsSnapshotFrom(requests)

            val result = service.getRequestStats()

            result.topTopics shouldHaveSize 5
        }

        @Test
        fun `반려 사유 분포는 count 내림차순 TOP 5 를 반환한다`() {
            // "부적절한 소스" 2건, "중복" 1건 — APPROVED 는 rejectionReason 없음
            val requests = listOf(
                rejected("r1", "부적절한 소스"),
                rejected("r2", "부적절한 소스"),
                rejected("r3", "중복"),
                approved("a1")
            )
            every { requestStore.getStatsSnapshot(any()) } returns statsSnapshotFrom(requests)

            val result = service.getRequestStats()

            result.rejectionReasons shouldHaveSize 2
            result.rejectionReasons[0].reason shouldBe "부적절한 소스"
            result.rejectionReasons[0].count shouldBe 2
            result.rejectionReasons[1].reason shouldBe "중복"
            result.rejectionReasons[1].count shouldBe 1
        }

        @Test
        fun `주간 처리량은 7일 이내 리뷰 완료 건수만 집계한다`() {
            // 7일 이내: approved (1일전), rejected (2일전) — 2건
            // 7일 초과: approved (10일전), pending (1일전 생성이지만 리뷰 없음) — 제외
            val requests = listOf(
                approved("a1", reviewedAt = now.minus(1, ChronoUnit.DAYS),
                    createdAt = now.minus(3, ChronoUnit.DAYS)),
                rejected("r1", "사유", reviewedAt = now.minus(2, ChronoUnit.DAYS)),
                approved("a2", reviewedAt = now.minus(10, ChronoUnit.DAYS),
                    createdAt = now.minus(15, ChronoUnit.DAYS)),
                pending("p1")
            )
            every { requestStore.getStatsSnapshot(any()) } returns statsSnapshotFrom(requests)

            val result = service.getRequestStats()

            result.weeklyProcessedCount shouldBe 2
        }
    }

    @Nested
    inner class EdgeCases {

        @Test
        fun `요청이 없으면 모든 카운터가 0이고 평균은 null 이다`() {
            every { requestStore.getStatsSnapshot(any()) } returns statsSnapshotFrom(emptyList())

            val result = service.getRequestStats()

            result.pendingCount shouldBe 0
            result.approvedCount shouldBe 0
            result.rejectedCount shouldBe 0
            result.totalCount shouldBe 0
            result.avgApprovalHours.shouldBeNull()
            result.topTopics.shouldBeEmpty()
            result.rejectionReasons.shouldBeEmpty()
            result.weeklyProcessedCount shouldBe 0
        }

        @Test
        fun `승인된 요청이 없으면 평균 승인 소요시간은 null 이다`() {
            val requests = listOf(
                pending("p1"),
                rejected("r1", "사유")
            )
            every { requestStore.getStatsSnapshot(any()) } returns statsSnapshotFrom(requests)

            val result = service.getRequestStats()

            result.avgApprovalHours.shouldBeNull()
        }

        @Test
        fun `공백만 있는 반려 사유는 분포에서 제외된다`() {
            val requests = listOf(
                rejected("r1", "   "),
                rejected("r2", "정상 사유")
            )
            every { requestStore.getStatsSnapshot(any()) } returns statsSnapshotFrom(requests)

            val result = service.getRequestStats()

            // 공백만 있는 사유는 rejectionReason() 이 null 반환 — 제외
            result.rejectionReasons shouldHaveSize 1
            result.rejectionReasons[0].reason shouldBe "정상 사유"
        }

        @Test
        fun `requestName 의 양쪽 공백은 trim 되어 같은 토픽으로 묶인다`() {
            val requests = listOf(
                pending("p1", requestName = "AI"),
                pending("p2", requestName = "  AI  "),
                pending("p3", requestName = "AI ")
            )
            every { requestStore.getStatsSnapshot(any()) } returns statsSnapshotFrom(requests)

            val result = service.getRequestStats()

            result.topTopics shouldHaveSize 1
            result.topTopics[0].requestName shouldBe "AI"
            result.topTopics[0].count shouldBe 3
        }

        @Test
        fun `정확히 7일 경계에 있는 리뷰는 주간 처리량에서 제외된다`() {
            // cutoff = now - 7일. wasReviewedAfter 는 isAfter 엄격 비교이므로 7일 정각은 false
            val requests = listOf(
                approved("a1", reviewedAt = now.minus(7, ChronoUnit.DAYS),
                    createdAt = now.minus(10, ChronoUnit.DAYS))
            )
            every { requestStore.getStatsSnapshot(any()) } returns statsSnapshotFrom(requests)

            val result = service.getRequestStats()

            result.weeklyProcessedCount shouldBe 0
        }
    }

    @Nested
    inner class BuildStatsDirectly {

        @Test
        fun `buildStats 는 store 호출 없이 주어진 리스트로 통계를 계산한다`() {
            // buildStats 는 순수 계산 — store 를 stub 하지 않아도 동작한다
            val requests = listOf(pending("p1"), approved("a1"))

            val result = service.buildStats(requests)

            result.pendingCount shouldBe 1
            result.approvedCount shouldBe 1
            result.totalCount shouldBe 2
            result.avgApprovalHours.shouldNotBeNull()
        }
    }

    private fun statsSnapshotFrom(requests: List<UserClippingRequest>): UserClippingRequestStatsSnapshot {
        val weekAgo = now.minus(7, ChronoUnit.DAYS)
        val approvalDurations = requests.mapNotNull(UserClippingRequest::approvalLeadTimeHours)
        return UserClippingRequestStatsSnapshot(
            pendingCount = requests.count(UserClippingRequest::isPendingReview),
            approvedCount = requests.count(UserClippingRequest::isApproved),
            rejectedCount = requests.count(UserClippingRequest::isRejected),
            totalCount = requests.size,
            avgApprovalHours = approvalDurations.takeIf { it.isNotEmpty() }
                ?.average()
                ?.let { "%.1f".format(it).toDouble() },
            topTopics = requests
                .groupBy { it.requestName.trim() }
                .map { (name, rows) -> UserClippingRequestCountRow(name, rows.size) }
                .sortedByDescending { it.count }
                .take(5),
            rejectionReasons = requests
                .mapNotNull { request -> request.rejectionReason()?.let { it to request } }
                .groupBy({ it.first }, { it.second })
                .map { (reason, rows) -> UserClippingRequestCountRow(reason, rows.size) }
                .sortedByDescending { it.count }
                .take(5),
            weeklyProcessedCount = requests.count { it.wasReviewedAfter(weekAgo) }
        )
    }
}
