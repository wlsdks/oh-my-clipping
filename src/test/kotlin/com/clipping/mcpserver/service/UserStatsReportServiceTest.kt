package com.clipping.mcpserver.service

import com.clipping.mcpserver.model.Category
import com.clipping.mcpserver.model.ClippingStat
import com.clipping.mcpserver.model.UserClippingRequest
import com.clipping.mcpserver.model.UserClippingRequestStatus
import com.clipping.mcpserver.store.CategoryStore
import com.clipping.mcpserver.store.DeliveryLogStore
import com.clipping.mcpserver.store.StatsStore
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.YearMonth

class UserStatsReportServiceTest {

    private val requestService = mockk<UserClippingRequestService>()
    private val statsStore = mockk<StatsStore>()
    private val categoryStore = mockk<CategoryStore>()
    private val deliveryLogStore = mockk<DeliveryLogStore>(relaxed = true)

    private val service = UserStatsReportService(requestService, statsStore, categoryStore, deliveryLogStore)

    private fun stubApproved(username: String, vararg categoryIds: String) {
        every { requestService.listOwnRequests(username) } returns categoryIds.mapIndexed { i, id ->
            UserClippingRequest(
                id = "req-$i",
                requesterUserId = "user-id",
                requestName = "요청 $i",
                sourceName = "소스",
                sourceUrl = "https://example.com/rss",
                slackChannelId = "C0123ABCD",
                personaName = "요약",
                personaPrompt = "prompt",
                summaryStyle = null,
                targetAudience = null,
                requestNote = null,
                status = UserClippingRequestStatus.APPROVED,
                approvedCategoryId = id
            )
        }
    }

    private fun stubCategory(id: String, name: String = "카테고리-$id") {
        every { categoryStore.findById(id) } returns Category(id = id, name = name)
    }

    private fun clippingStat(
        categoryId: String,
        date: LocalDate,
        collected: Int = 0,
        summarized: Int = 0,
        legacySent: Int = 0
    ) = ClippingStat(
        id = "stat-$categoryId-$date",
        categoryId = categoryId,
        statDate = date,
        itemsCollected = collected,
        itemsSummarized = summarized,
        itemsSent = legacySent,
        topKeywords = emptyList(),
        avgImportanceScore = 0f
    )

    @Nested
    inner class GetOwnMonthlyStats {

        @Test
        fun `승인된 카테고리가 없으면 빈 목록을 반환하고 delivery_log를 조회하지 않는다`() {
            every { requestService.listOwnRequests("user-1") } returns emptyList()

            val rows = service.getOwnMonthlyStats("user-1", YearMonth.of(2026, 4))

            rows shouldBe emptyList()
            verify(exactly = 0) { deliveryLogStore.sumDeliveredItemsByCategoryDate(any(), any(), any()) }
        }

        @Test
        fun `itemsSent는 clipping_stats 값이 아니라 delivery_log 집계를 사용한다`() {
            val categoryId = "cat-1"
            val date = LocalDate.of(2026, 4, 3)
            stubApproved("user-1", categoryId)
            stubCategory(categoryId, "마케팅")

            // clipping_stats는 "12건 발송"이라고 적혀 있지만 실제 delivery_log에는 5건만 SENT.
            every { statsStore.findMonthly(categoryId, YearMonth.of(2026, 4)) } returns listOf(
                clippingStat(categoryId, date, collected = 100, summarized = 20, legacySent = 12)
            )
            every {
                deliveryLogStore.sumDeliveredItemsByCategoryDate(
                    listOf(categoryId),
                    LocalDate.of(2026, 4, 1),
                    LocalDate.of(2026, 4, 30)
                )
            } returns mapOf((categoryId to date) to 5)

            val rows = service.getOwnMonthlyStats("user-1", YearMonth.of(2026, 4))

            rows shouldHaveSize 1
            rows[0].itemsSent shouldBe 5            // delivery_log 집계
            rows[0].itemsCollected shouldBe 100     // clipping_stats 그대로
            rows[0].itemsSummarized shouldBe 20     // clipping_stats 그대로
        }

        @Test
        fun `stats 행이 없는 날짜에 발송 기록만 있으면 합성 행을 추가한다`() {
            val categoryId = "cat-1"
            val deliveredOnly = LocalDate.of(2026, 4, 10)
            stubApproved("user-1", categoryId)
            stubCategory(categoryId, "마케팅")

            every { statsStore.findMonthly(categoryId, YearMonth.of(2026, 4)) } returns emptyList()
            every {
                deliveryLogStore.sumDeliveredItemsByCategoryDate(any(), any(), any())
            } returns mapOf((categoryId to deliveredOnly) to 3)

            val rows = service.getOwnMonthlyStats("user-1", YearMonth.of(2026, 4))

            rows shouldHaveSize 1
            val row = rows.single()
            row.statDate shouldBe deliveredOnly
            row.itemsSent shouldBe 3
            row.itemsCollected shouldBe 0
            row.itemsSummarized shouldBe 0
            row.categoryName shouldBe "마케팅"
            row.id shouldContain "delivered-$categoryId"
        }

        @Test
        fun `stats 행은 있지만 delivery_log에 없는 날짜는 itemsSent가 0으로 조정된다`() {
            // 레거시 clipping_stats.itemsSent에 값이 있어도 실제 발송 성공이 없으면 0을 노출해
            // 사용자 화면에서 "받았다"고 오판되지 않도록 한다.
            val categoryId = "cat-1"
            val date = LocalDate.of(2026, 4, 3)
            stubApproved("user-1", categoryId)
            stubCategory(categoryId)

            every { statsStore.findMonthly(categoryId, YearMonth.of(2026, 4)) } returns listOf(
                clippingStat(categoryId, date, collected = 50, summarized = 10, legacySent = 7)
            )
            every {
                deliveryLogStore.sumDeliveredItemsByCategoryDate(any(), any(), any())
            } returns emptyMap()

            val rows = service.getOwnMonthlyStats("user-1", YearMonth.of(2026, 4))

            rows.single().itemsSent shouldBe 0
        }

        @Test
        fun `여러 카테고리와 여러 날짜가 있으면 (카테고리,일자) 키로 정확히 매칭된다`() {
            val catA = "cat-A"
            val catB = "cat-B"
            val d1 = LocalDate.of(2026, 4, 3)
            val d2 = LocalDate.of(2026, 4, 4)
            stubApproved("user-1", catA, catB)
            stubCategory(catA, "마케팅")
            stubCategory(catB, "경영")

            every { statsStore.findMonthly(catA, YearMonth.of(2026, 4)) } returns listOf(
                clippingStat(catA, d1, collected = 10),
                clippingStat(catA, d2, collected = 20)
            )
            every { statsStore.findMonthly(catB, YearMonth.of(2026, 4)) } returns listOf(
                clippingStat(catB, d1, collected = 30)
            )
            every {
                deliveryLogStore.sumDeliveredItemsByCategoryDate(any(), any(), any())
            } returns mapOf(
                (catA to d1) to 2,
                (catA to d2) to 4,
                (catB to d1) to 1
                // catB / d2는 없음 → 합성 행 없고 stats 행도 없으니 그냥 누락
            )

            val rows = service.getOwnMonthlyStats("user-1", YearMonth.of(2026, 4))

            rows shouldHaveSize 3
            rows.map { Triple(it.categoryId, it.statDate, it.itemsSent) }
                .shouldContainExactlyInAnyOrder(
                    Triple(catA, d1, 2),
                    Triple(catA, d2, 4),
                    Triple(catB, d1, 1)
                )
        }

        @Test
        fun `최신 일자 우선, 같은 일자면 카테고리명 오름차순으로 정렬한다`() {
            val catA = "cat-A"
            val catB = "cat-B"
            val d1 = LocalDate.of(2026, 4, 3)
            val d2 = LocalDate.of(2026, 4, 5)
            stubApproved("user-1", catA, catB)
            stubCategory(catA, "B-경영")
            stubCategory(catB, "A-마케팅")

            every { statsStore.findMonthly(catA, any()) } returns listOf(clippingStat(catA, d1), clippingStat(catA, d2))
            every { statsStore.findMonthly(catB, any()) } returns listOf(clippingStat(catB, d2))
            every {
                deliveryLogStore.sumDeliveredItemsByCategoryDate(any(), any(), any())
            } returns emptyMap()

            val rows = service.getOwnMonthlyStats("user-1", YearMonth.of(2026, 4))

            // d2가 먼저(내림차순), d2 내에선 "A-마케팅"이 "B-경영"보다 앞.
            rows.map { it.categoryName to it.statDate } shouldBe listOf(
                "A-마케팅" to d2,
                "B-경영" to d2,
                "B-경영" to d1
            )
        }
    }

    @Nested
    inner class ExportOwnMonthlyStatsCsv {

        @Test
        fun `스프레드시트 수식 인젝션을 무해화한다`() {
            val categoryId = "cat-1"
            val date = LocalDate.of(2026, 3, 3)
            stubApproved("user-1", categoryId)
            every { categoryStore.findById(categoryId) } returns Category(
                id = categoryId,
                name = "=HYPERLINK(\"https://evil\")"
            )
            every { statsStore.findMonthly(categoryId, YearMonth.of(2026, 3)) } returns listOf(
                ClippingStat(
                    id = "stat-1",
                    categoryId = categoryId,
                    statDate = date,
                    itemsCollected = 10,
                    itemsSummarized = 6,
                    itemsSent = 4,
                    topKeywords = listOf("=cmd", "normal"),
                    avgImportanceScore = 0.74f
                )
            )
            every {
                deliveryLogStore.sumDeliveredItemsByCategoryDate(any(), any(), any())
            } returns mapOf((categoryId to date) to 4)

            val csv = String(service.exportOwnMonthlyStatsCsv("user-1", YearMonth.of(2026, 3)), Charsets.UTF_8)

            csv.shouldContain("'=HYPERLINK(\"\"https://evil\"\")")
            csv.shouldContain("'=cmd|normal")
        }
    }
}
