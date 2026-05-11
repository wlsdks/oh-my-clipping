package com.ohmyclipping.service

import com.ohmyclipping.service.notification.OperationsNotificationService
import com.ohmyclipping.service.dto.user.EntryDto
import com.ohmyclipping.service.dto.user.EntryErrorReason
import com.ohmyclipping.service.dto.user.SubmitWithEntriesRequest
import com.ohmyclipping.service.source.CategorySourceBuilder
import com.ohmyclipping.model.AccountApprovalStatus
import com.ohmyclipping.model.AccountRole
import com.ohmyclipping.model.AdminUser
import com.ohmyclipping.model.CompetitorWatchlist
import com.ohmyclipping.store.AdminUserStore
import com.ohmyclipping.store.AuditLogStore
import com.ohmyclipping.store.CategoryRuleStore
import com.ohmyclipping.store.CategoryStore
import com.ohmyclipping.store.CompetitorWatchlistStore
import com.ohmyclipping.store.RssSourceStore
import com.ohmyclipping.store.UserClippingRequestStore
import com.ohmyclipping.store.UserDeliveryScheduleStore
import com.ohmyclipping.security.UrlSafetyValidator
import com.ohmyclipping.error.InvalidInputException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant

class UserClippingRequestServiceSubmitWithEntriesTest {

    private val requestStore: UserClippingRequestStore = mockk(relaxed = true)
    private val adminUserStore: AdminUserStore = mockk(relaxed = true)
    private val adminPersonaService: AdminPersonaService = mockk(relaxed = true)
    private val adminCategoryService: AdminCategoryService = mockk(relaxed = true)
    private val categoryStore: CategoryStore = mockk(relaxed = true)
    private val adminCategoryRuleService: AdminCategoryRuleService = mockk(relaxed = true)
    private val adminSourceService: AdminSourceService = mockk(relaxed = true)
    private val sourceStore: RssSourceStore = mockk(relaxed = true)
    private val slackMessageSender: SlackMessageSender = mockk(relaxed = true)
    private val runtimeSettingService: RuntimeSettingService = mockk(relaxed = true)
    private val urlSafetyValidator: UrlSafetyValidator = mockk(relaxed = true)
    private val userSetupOwnershipService: UserSetupOwnershipService = mockk(relaxed = true)
    private val auditLogStore: AuditLogStore = mockk(relaxed = true)
    private val applicationEventPublisher: org.springframework.context.ApplicationEventPublisher = mockk(relaxed = true)
    private val userDeliveryScheduleStore: UserDeliveryScheduleStore = mockk(relaxed = true)
    private val operationsNotificationService: OperationsNotificationService = mockk(relaxed = true)
    private val competitorStore: CompetitorWatchlistStore = mockk(relaxed = true)
    private val organizationService: OrganizationService = mockk(relaxed = true)
    private val categoryRuleStore: CategoryRuleStore = mockk(relaxed = true)
    private val categorySourceBuilder: CategorySourceBuilder = mockk(relaxed = true)

    private val service = UserClippingRequestService(
        requestStore = requestStore,
        adminUserStore = adminUserStore,
        adminPersonaService = adminPersonaService,
        adminCategoryService = adminCategoryService,
        categoryStore = categoryStore,
        adminCategoryRuleService = adminCategoryRuleService,
        adminSourceService = adminSourceService,
        sourceStore = sourceStore,
        slackMessageSender = slackMessageSender,
        runtimeSettingService = runtimeSettingService,
        urlSafetyValidator = urlSafetyValidator,
        userSetupOwnershipService = userSetupOwnershipService,
        auditLogStore = auditLogStore,
        applicationEventPublisher = applicationEventPublisher,
        userDeliveryScheduleStore = userDeliveryScheduleStore,
        operationsNotificationService = operationsNotificationService,
        competitorWatchlistStore = competitorStore,
        organizationService = organizationService,
        categoryRuleStore = categoryRuleStore,
        categorySourceBuilder = categorySourceBuilder
    )

    /** 테스트에서 공통으로 사용하는 USER 역할 사용자 stub */
    private val testUser = AdminUser(
        id = "user-stub-id",
        username = "u1",
        passwordHash = "hash",
        role = AccountRole.USER,
        approvalStatus = AccountApprovalStatus.APPROVED
    )

    @Nested
    inner class `submitRequestWithEntries` {

        @Test
        fun `모든 entry 성공 시 submitted 반환 및 errors 빈 배열`() {
            every { adminUserStore.findByUsername("u1") } returns testUser
            every { competitorStore.findByNamesIgnoreCase(any()) } returns emptyList()
            val req = SubmitWithEntriesRequest(
                categoryName = "테스트 카테고리",
                entries = listOf(
                    EntryDto("리스킬링", "keyword"),
                    EntryDto("MegaCorp", "company", "999930")
                )
            )
            val res = service.submitRequestWithEntries(req, username = "u1")
            res.status shouldBe "submitted"
            res.errors.shouldBeEmpty()
        }

        @Test
        fun `위자드가 고른 발송 프리셋·요일·시각이 user_delivery_schedules 로 upsert 된다`() {
            every { adminUserStore.findByUsername("u1") } returns testUser
            every { competitorStore.findByNamesIgnoreCase(any()) } returns emptyList()
            every { userDeliveryScheduleStore.findByUserId(testUser.id) } returns null

            val captured = io.mockk.slot<com.ohmyclipping.model.UserDeliverySchedule>()
            every { userDeliveryScheduleStore.upsert(capture(captured)) } returns Unit

            val req = SubmitWithEntriesRequest(
                categoryName = "테스트",
                entries = listOf(EntryDto("리더십", "keyword")),
                deliveryPreset = "EVERYDAY",
                deliveryHour = 18
            )
            service.submitRequestWithEntries(req, username = "u1")

            captured.captured.userId shouldBe testUser.id
            captured.captured.preset shouldBe com.ohmyclipping.model.DeliveryPreset.EVERYDAY
            captured.captured.deliveryHour shouldBe 18
            captured.captured.deliveryDays shouldBe listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")
        }

        @Test
        fun `스케줄 정보가 누락되면 기본값 WEEKDAYS 9시로 upsert 된다`() {
            every { adminUserStore.findByUsername("u1") } returns testUser
            every { competitorStore.findByNamesIgnoreCase(any()) } returns emptyList()
            every { userDeliveryScheduleStore.findByUserId(testUser.id) } returns null

            val captured = io.mockk.slot<com.ohmyclipping.model.UserDeliverySchedule>()
            every { userDeliveryScheduleStore.upsert(capture(captured)) } returns Unit

            val req = SubmitWithEntriesRequest(
                categoryName = "테스트",
                entries = listOf(EntryDto("리더십", "keyword"))
            )
            service.submitRequestWithEntries(req, username = "u1")

            captured.captured.preset shouldBe com.ohmyclipping.model.DeliveryPreset.WEEKDAYS
            captured.captured.deliveryHour shouldBe 9
            captured.captured.deliveryDays shouldBe listOf("MON", "TUE", "WED", "THU", "FRI")
        }

        @Test
        fun `COMPETITOR 매칭 entry 는 errors 에 기록되고 나머지 저장 시 partial 반환`() {
            every { adminUserStore.findByUsername("u1") } returns testUser
            // 배치 조회: company 타입인 "Coursera" 만 전달되고 stubCompetitor 반환
            every { competitorStore.findByNamesIgnoreCase(listOf("Coursera")) } returns listOf(stubCompetitor("Coursera"))
            val req = SubmitWithEntriesRequest(
                categoryName = "테스트",
                entries = listOf(
                    EntryDto("리스킬링", "keyword"),
                    EntryDto("Coursera", "company")
                )
            )
            val res = service.submitRequestWithEntries(req, username = "u1")
            res.status shouldBe "partial"
            res.errors shouldHaveSize 1
            res.errors[0].reason shouldBe EntryErrorReason.COMPETITOR_WATCHLIST_CONFLICT
            res.errors[0].value shouldBe "Coursera"
        }

        @Test
        fun `잘못된 stockCode 형식 시 INVALID_STOCK_CODE 반환`() {
            every { adminUserStore.findByUsername("u1") } returns testUser
            every { competitorStore.findByNamesIgnoreCase(any()) } returns emptyList()
            val req = SubmitWithEntriesRequest(
                categoryName = "테스트",
                entries = listOf(EntryDto("MegaCorp", "company", stockCode = "invalid-xyz"))
            )
            val res = service.submitRequestWithEntries(req, username = "u1")
            // 유일한 entry 가 실패 → rejected
            res.status shouldBe "rejected"
            res.errors[0].reason shouldBe EntryErrorReason.INVALID_STOCK_CODE
        }

        @Test
        fun `중복 기업 entry 는 DUPLICATE_IN_REQUEST 반환`() {
            every { adminUserStore.findByUsername("u1") } returns testUser
            every { competitorStore.findByNamesIgnoreCase(any()) } returns emptyList()
            val req = SubmitWithEntriesRequest(
                categoryName = "테스트",
                entries = listOf(
                    EntryDto("MegaCorp", "company"),
                    EntryDto("MegaCorp", "company"),
                    EntryDto("리스킬링", "keyword")
                )
            )
            val res = service.submitRequestWithEntries(req, username = "u1")
            res.status shouldBe "partial"
            res.errors.any { it.reason == EntryErrorReason.DUPLICATE_IN_REQUEST } shouldBe true
        }

        @Test
        fun `모든 entry 실패 시 rejected 반환 및 DB 미저장`() {
            every { adminUserStore.findByUsername("u1") } returns testUser
            // 배치 조회: company 타입 2개 모두 경쟁사로 반환
            every { competitorStore.findByNamesIgnoreCase(any()) } returns
                listOf(stubCompetitor("Coursera"), stubCompetitor("Udemy"))
            val req = SubmitWithEntriesRequest(
                categoryName = "테스트",
                entries = listOf(
                    EntryDto("Coursera", "company"),
                    EntryDto("Udemy", "company")
                )
            )
            val res = service.submitRequestWithEntries(req, username = "u1")
            res.status shouldBe "rejected"
            res.errors shouldHaveSize 2
        }

        @Test
        fun `stockCode 빈 문자열은 optional 로 통과`() {
            every { adminUserStore.findByUsername("u1") } returns testUser
            every { competitorStore.findByNamesIgnoreCase(any()) } returns emptyList()
            val req = SubmitWithEntriesRequest(
                categoryName = "테스트",
                entries = listOf(EntryDto("MegaCorp", "company", stockCode = ""))
            )
            val res = service.submitRequestWithEntries(req, username = "u1")
            res.status shouldBe "submitted"
        }

        @Test
        fun `keyword entry 는 COMPETITOR 체크를 하지 않아야 한다`() {
            // keyword 타입은 company 가 아니므로 competitor_watchlist 검사를 건너뜀
            every { adminUserStore.findByUsername("u1") } returns testUser
            // keyword 타입만 있어서 company names 리스트가 비어 있고 emptyList 반환
            every { competitorStore.findByNamesIgnoreCase(emptyList()) } returns emptyList()
            val req = SubmitWithEntriesRequest(
                categoryName = "테스트",
                entries = listOf(EntryDto("Coursera", "keyword"))  // keyword 타입이므로 허용
            )
            val res = service.submitRequestWithEntries(req, username = "u1")
            res.status shouldBe "submitted"
        }

        @Test
        fun `이번 달 유효 요청이 5건 이상이면 월간 한도 초과 예외를 던진다`() {
            every { adminUserStore.findByUsername("u1") } returns testUser
            // 이번 달 생성된 유효 요청 count가 5건이면 6번째 submit은 거부돼야 한다.
            every { requestStore.countCreatedSinceByRequesterUserId(testUser.id, any()) } returns 5

            val req = SubmitWithEntriesRequest(
                categoryName = "초과 테스트",
                entries = listOf(EntryDto("리스킬링", "keyword"))
            )

            val ex = shouldThrow<InvalidInputException> {
                service.submitRequestWithEntries(req, username = "u1")
            }
            ex.message shouldBe "이번 달 신규 요청 한도(5건)에 도달했습니다. 다음 달에 다시 시도해 주세요."
        }
    }

    private fun stubCompetitor(name: String = "stub"): CompetitorWatchlist = CompetitorWatchlist(
        id = "stub-id",
        name = name,
        aliases = emptyList(),
        excludeKeywords = emptyList(),
        tier = "DIRECT",
        isActive = true,
        createdAt = Instant.now(),
        updatedAt = Instant.now()
    )
}
