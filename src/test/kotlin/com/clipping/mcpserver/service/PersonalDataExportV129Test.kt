package com.clipping.mcpserver.service

import com.clipping.mcpserver.config.RedisRateLimitService
import com.clipping.mcpserver.model.AccountApprovalStatus
import com.clipping.mcpserver.model.AccountRole
import com.clipping.mcpserver.model.AdminUser
import com.clipping.mcpserver.model.Department
import com.clipping.mcpserver.model.SummaryFeedback
import com.clipping.mcpserver.model.Team
import com.clipping.mcpserver.store.AdminUserStore
import com.clipping.mcpserver.store.AuditLogStore
import com.clipping.mcpserver.store.BookmarkedArticleStore
import com.clipping.mcpserver.store.DeliveryLogStore
import com.clipping.mcpserver.store.DepartmentStore
import com.clipping.mcpserver.store.SummaryFeedbackStore
import com.clipping.mcpserver.store.TeamStore
import com.clipping.mcpserver.store.UserClippingRequestStore
import com.clipping.mcpserver.store.UserDeliveryScheduleStore
import com.clipping.mcpserver.store.UserEventStore
import com.clipping.mcpserver.store.UserOwnedCategoryStore
import com.clipping.mcpserver.store.UserOwnedPersonaStore
import com.clipping.mcpserver.store.UserOwnedSourceStore
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * V129 신규 필드 회귀 테스트 — [UserDataExportService] 가
 * FK 이름/승인/활성 필드와 feedback 섹션을 올바르게 노출하고,
 * 스펙상 삭제된 organizationId 는 포함하지 않는지 확인한다.
 */
class PersonalDataExportV129Test {

    private val adminUserStore = mockk<AdminUserStore>()
    private val userClippingRequestStore = mockk<UserClippingRequestStore>(relaxed = true)
    private val userOwnedCategoryStore = mockk<UserOwnedCategoryStore>(relaxed = true)
    private val userOwnedPersonaStore = mockk<UserOwnedPersonaStore>(relaxed = true)
    private val userOwnedSourceStore = mockk<UserOwnedSourceStore>(relaxed = true)
    private val userDeliveryScheduleStore = mockk<UserDeliveryScheduleStore>(relaxed = true)
    private val userEventStore = mockk<UserEventStore>(relaxed = true)
    private val bookmarkedArticleStore = mockk<BookmarkedArticleStore>(relaxed = true)
    private val deliveryLogStore = mockk<DeliveryLogStore>(relaxed = true)
    private val auditLogStore = mockk<AuditLogStore>(relaxed = true)
    private val rateLimitService = mockk<RedisRateLimitService>(relaxed = true)
    private val departmentStore = mockk<DepartmentStore>()
    private val teamStore = mockk<TeamStore>()
    private val summaryFeedbackStore = mockk<SummaryFeedbackStore>()

    private val service = UserDataExportService(
        adminUserStore,
        userClippingRequestStore,
        userOwnedCategoryStore,
        userOwnedPersonaStore,
        userOwnedSourceStore,
        userDeliveryScheduleStore,
        userEventStore,
        bookmarkedArticleStore,
        deliveryLogStore,
        auditLogStore,
        rateLimitService,
        departmentStore,
        teamStore,
        summaryFeedbackStore
    )

    private val targetUser = AdminUser(
        id = "user-1",
        username = "alice",
        passwordHash = "HASH-DO-NOT-LEAK",
        role = AccountRole.USER,
        displayName = "앨리스",
        department = "legacy-name",
        departmentId = "dept-1",
        team = "legacy-team",
        teamId = "team-1",
        approvalStatus = AccountApprovalStatus.APPROVED,
        approvalNote = "초기 승인 완료",
        approvedAt = Instant.parse("2026-02-01T00:00:00Z"),
        mustChangePassword = false,
        isActive = true,
        lastLoginAt = Instant.parse("2026-04-01T09:00:00Z"),
        createdAt = Instant.parse("2025-10-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-04-10T00:00:00Z")
    )

    private fun stubEmptyStores() {
        every { adminUserStore.findByUsername("alice") } returns targetUser
        every { userDeliveryScheduleStore.findByUserId("user-1") } returns null
        every { userOwnedCategoryStore.listCategoryIds("user-1") } returns emptyList()
        every { userOwnedPersonaStore.listPersonaIds("user-1") } returns emptyList()
        every { userOwnedSourceStore.listSourceIds("user-1") } returns emptyList()
        every { userClippingRequestStore.listByRequesterUserId("user-1") } returns emptyList()
        every { bookmarkedArticleStore.listAllForUser("user-1") } returns emptyList()
        every { userEventStore.findByUserAndDateRange("user-1", any(), any(), any()) } returns emptyList()
    }

    @Nested
    inner class `Account 섹션 V129 필드` {
        @Test
        fun `departmentId와 JOIN 된 departmentName 을 함께 노출한다`() {
            stubEmptyStores()
            every { departmentStore.findById("dept-1") } returns
                Department(id = "dept-1", name = "영업팀", nameNormalized = "영업팀")
            every { teamStore.findById("team-1") } returns
                Team(id = "team-1", departmentId = "dept-1", name = "퍼포먼스", nameNormalized = "퍼포먼스")
            every { summaryFeedbackStore.findByUserId("user-1", any()) } returns emptyList()

            val export = service.gatherPersonalData("alice")

            export.account.departmentId shouldBe "dept-1"
            export.account.departmentName shouldBe "영업팀"
            export.account.teamId shouldBe "team-1"
            export.account.teamName shouldBe "퍼포먼스"
            // Legacy name-cache 필드도 함께 유지된다.
            export.account.department shouldBe "legacy-name"
            export.account.team shouldBe "legacy-team"
        }

        @Test
        fun `approvalNote, approvedAt, mustChangePassword, isActive, updatedAt 필드가 포함된다`() {
            stubEmptyStores()
            every { departmentStore.findById(any()) } returns null
            every { teamStore.findById(any()) } returns null
            every { summaryFeedbackStore.findByUserId("user-1", any()) } returns emptyList()

            val export = service.gatherPersonalData("alice")

            export.account.approvalNote shouldBe "초기 승인 완료"
            export.account.approvedAt shouldBe Instant.parse("2026-02-01T00:00:00Z")
            export.account.mustChangePassword shouldBe false
            export.account.isActive shouldBe true
            export.account.updatedAt shouldBe Instant.parse("2026-04-10T00:00:00Z")
        }
    }

    @Nested
    inner class `feedback 섹션` {
        @Test
        fun `summary_feedback 이 존재하면 요약 id와 type 으로 직렬화된다`() {
            stubEmptyStores()
            every { departmentStore.findById(any()) } returns null
            every { teamStore.findById(any()) } returns null
            every { summaryFeedbackStore.findByUserId("user-1", any()) } returns listOf(
                SummaryFeedback(
                    id = "fb-1",
                    summaryId = "sum-1",
                    feedbackType = "LIKE",
                    userId = "user-1",
                    createdAt = Instant.parse("2026-04-10T01:00:00Z")
                ),
                SummaryFeedback(
                    id = "fb-2",
                    summaryId = "sum-2",
                    feedbackType = "DISLIKE",
                    userId = "user-1",
                    createdAt = Instant.parse("2026-04-10T02:00:00Z")
                )
            )

            val export = service.gatherPersonalData("alice")

            export.feedback shouldHaveSize 2
            export.feedback[0].summaryId shouldBe "sum-1"
            export.feedback[0].feedbackType shouldBe "LIKE"
            export.feedback[1].feedbackType shouldBe "DISLIKE"
        }
    }

    @Nested
    inner class `organizationId 부재와 민감 필드 방어` {
        @Test
        fun `organizationId 는 AccountExportSection 스키마에 없다`() {
            stubEmptyStores()
            every { departmentStore.findById(any()) } returns null
            every { teamStore.findById(any()) } returns null
            every { summaryFeedbackStore.findByUserId("user-1", any()) } returns emptyList()

            val export = service.gatherPersonalData("alice")

            // 리플렉션으로 필드 이름을 확인해 회귀 방지 — 스키마에 organizationId 가 추가되면 실패한다.
            val fieldNames = export.account::class.java.declaredFields.map { it.name }
            (fieldNames.contains("organizationId")) shouldBe false
        }

        @Test
        fun `JSON 출력에 password_hash 와 organizationId 가 포함되지 않는다`() {
            stubEmptyStores()
            every { departmentStore.findById(any()) } returns null
            every { teamStore.findById(any()) } returns null
            every { summaryFeedbackStore.findByUserId("user-1", any()) } returns emptyList()
            every { rateLimitService.isRateLimited(any(), any(), any()) } returns false

            val json = String(service.exportAsJson("alice"))

            json shouldNotContain "password_hash"
            json shouldNotContain "HASH-DO-NOT-LEAK"
            json shouldNotContain "organizationId"
            // V129 신규 필드가 노출되는지 함께 확인.
            json shouldContain "departmentId"
            json shouldContain "feedback"
        }
    }
}
