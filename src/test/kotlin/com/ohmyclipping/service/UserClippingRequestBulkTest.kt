package com.ohmyclipping.service

import com.ohmyclipping.service.notification.OperationsNotificationService
import com.ohmyclipping.error.InvalidInputException
import com.ohmyclipping.error.NotFoundException
import com.ohmyclipping.model.*
import com.ohmyclipping.service.dto.clipping.*
import com.ohmyclipping.security.UrlSafetyValidator
import com.ohmyclipping.service.dto.admin.ApproveClippingRequestCommand
import com.ohmyclipping.service.source.CategorySourceBuilder
import com.ohmyclipping.store.AdminUserStore
import com.ohmyclipping.store.AuditLogStore
import com.ohmyclipping.store.CategoryRuleStore
import com.ohmyclipping.store.CompetitorWatchlistStore
import com.ohmyclipping.store.UserDeliveryScheduleStore
import com.ohmyclipping.store.CategoryStore
import com.ohmyclipping.store.RssSourceStore
import com.ohmyclipping.store.UserClippingRequestStore
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.*
import org.springframework.context.ApplicationEventPublisher
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * 사용자 요청 벌크 승인/반려 기능 테스트.
 */
class UserClippingRequestBulkTest {

    private val requestStore = mockk<UserClippingRequestStore>()
    private val adminUserStore = mockk<AdminUserStore>()
    private val adminPersonaService = mockk<AdminPersonaService>()
    private val adminCategoryService = mockk<AdminCategoryService>()
    private val categoryStore = mockk<CategoryStore>()
    private val adminCategoryRuleService = mockk<AdminCategoryRuleService>()
    private val adminSourceService = mockk<AdminSourceService>()
    private val sourceStore = mockk<RssSourceStore>()
    private val slackMessageSender = mockk<SlackMessageSender>()
    private val runtimeSettingService = mockk<RuntimeSettingService>()
    private val urlSafetyValidator = mockk<UrlSafetyValidator>()
    private val userSetupOwnershipService = mockk<UserSetupOwnershipService>()
    private val auditLogStore = mockk<AuditLogStore>(relaxed = true)
    private val applicationEventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
    private val userDeliveryScheduleStore = mockk<UserDeliveryScheduleStore>(relaxed = true)
    private val operationsNotificationService = mockk<OperationsNotificationService>(relaxed = true)
    private val competitorWatchlistStore = mockk<CompetitorWatchlistStore>(relaxed = true)
    private val organizationService = mockk<OrganizationService>(relaxed = true)
    private val categoryRuleStore = mockk<CategoryRuleStore>(relaxed = true)
    private val categorySourceBuilder = mockk<CategorySourceBuilder>(relaxed = true)

    private lateinit var service: UserClippingRequestService

    private val adminAccount = AdminUser(
        id = "admin-1",
        username = "admin",
        passwordHash = "hashed",
        role = AccountRole.ADMIN,
        approvalStatus = AccountApprovalStatus.APPROVED
    )

    private fun makePendingRequest(id: String) = UserClippingRequest(
        id = id,
        requesterUserId = "user-1",
        requestName = "테스트 요청 $id",
        sourceName = "Source",
        sourceUrl = "https://example.com/feed",
        slackChannelId = "C0123ABCD",
        personaName = "테스트 분석가",
        personaPrompt = "prompt",
        status = UserClippingRequestStatus.PENDING
    )

    @BeforeEach
    fun setUp() {
        clearAllMocks()
        service = UserClippingRequestService(
            requestStore, adminUserStore, adminPersonaService,
            adminCategoryService, categoryStore, adminCategoryRuleService,
            adminSourceService, sourceStore, slackMessageSender,
            runtimeSettingService, urlSafetyValidator, userSetupOwnershipService,
            auditLogStore, applicationEventPublisher, userDeliveryScheduleStore, operationsNotificationService,
            competitorWatchlistStore, organizationService, categoryRuleStore, categorySourceBuilder
        )
        // 기본: 카테고리 중복 체크용 기존 카테고리 목록 (빈 목록)
        every { categoryStore.findOperational() } returns emptyList()
        // 기본: URL 재사용 체크는 기본적으로 기존 소스 없음 처리
        every { sourceStore.findByUrlAndCategoryId(any(), any()) } returns null
        // 기본: form_entries 없음 — 레거시 approveRequest 경로로 라우팅된다. wizard 테스트는 override.
        every { requestStore.findFormEntries(any()) } returns null
    }

    private fun approveCommand(
        legalBasis: String = "QUOTATION_ONLY",
        summaryAllowed: Boolean = true,
        fulltextAllowed: Boolean = false,
        reviewNotes: String? = null
    ) = ApproveClippingRequestCommand(legalBasis, summaryAllowed, fulltextAllowed, reviewNotes)

    @Nested
    inner class `벌크 승인` {

        @Test
        fun `전체 성공 - 여러 PENDING 요청이 모두 승인된다`() {
            every { adminUserStore.findByUsername("admin") } returns adminAccount
            every { requestStore.findById("req-1") } returns makePendingRequest("req-1")
            every { requestStore.findById("req-2") } returns makePendingRequest("req-2")
            every { requestStore.update(any()) } answers { firstArg() }
            every { runtimeSettingService.current() } returns mockk {
                every { slackBotToken } returns "xoxb-test"
            }
            every { slackMessageSender.testConnection(any(), any()) } returns
                SlackMessageSender.SlackConnectionTestResult(
                    ok = true, botUser = "bot", team = "team",
                    channelId = "C0123ABCD", channelName = "channel",
                    neededScopes = null, providedScopes = null, rawError = null
                )
            every { adminPersonaService.createPersona(any(), any(), any(), any(), any(), any(), any()) } returns
                Persona(id = "persona-1", name = "분석가", systemPrompt = "prompt")
            every { categoryStore.save(any()) } returns
                Category(id = "cat-1", name = "테스트")
            every {
                adminSourceService.createSource(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
            } returns RssSource(id = "source-1", name = "Source", url = "https://example.com/feed", categoryId = "cat-1")
            every { adminSourceService.approveSource(any(), any(), any(), any(), any(), any(), any(), any()) } returns
                RssSource(id = "source-1", name = "Source", url = "https://example.com/feed", categoryId = "cat-1")
            every { userSetupOwnershipService.registerOwnedResources(any(), any(), any(), any()) } just runs

            val results = service.bulkApprove(listOf("req-1", "req-2"), "admin", approveCommand(reviewNotes = "일괄 승인"))

            results shouldHaveSize 2
            results.all { it.second.isSuccess } shouldBe true
            results.forEach { (_, result) ->
                result.getOrThrow().status shouldBe UserClippingRequestStatus.APPROVED
            }
        }

        @Test
        fun `부분 실패 - 존재하지 않는 ID는 실패하고 나머지는 성공한다`() {
            every { adminUserStore.findByUsername("admin") } returns adminAccount
            every { requestStore.findById("req-1") } returns makePendingRequest("req-1")
            // req-999는 존재하지 않는다.
            every { requestStore.findById("req-999") } returns null
            every { requestStore.update(any()) } answers { firstArg() }
            every { runtimeSettingService.current() } returns mockk {
                every { slackBotToken } returns "xoxb-test"
            }
            every { slackMessageSender.testConnection(any(), any()) } returns
                SlackMessageSender.SlackConnectionTestResult(
                    ok = true, botUser = "bot", team = "team",
                    channelId = "C0123ABCD", channelName = "channel",
                    neededScopes = null, providedScopes = null, rawError = null
                )
            every { adminPersonaService.createPersona(any(), any(), any(), any(), any(), any(), any()) } returns
                Persona(id = "persona-1", name = "분석가", systemPrompt = "prompt")
            every { categoryStore.save(any()) } returns
                Category(id = "cat-1", name = "테스트")
            every {
                adminSourceService.createSource(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
            } returns RssSource(id = "source-1", name = "Source", url = "https://example.com/feed", categoryId = "cat-1")
            every { adminSourceService.approveSource(any(), any(), any(), any(), any(), any(), any(), any()) } returns
                RssSource(id = "source-1", name = "Source", url = "https://example.com/feed", categoryId = "cat-1")
            every { userSetupOwnershipService.registerOwnedResources(any(), any(), any(), any()) } just runs

            val results = service.bulkApprove(listOf("req-1", "req-999"), "admin", approveCommand())

            results shouldHaveSize 2
            // 첫 번째는 성공
            results[0].second.isSuccess shouldBe true
            // 두 번째는 실패 (NotFoundException)
            results[1].second.isFailure shouldBe true
            results[1].second.exceptionOrNull() shouldBe io.kotest.matchers.types.beInstanceOf<NotFoundException>()
        }

        @Test
        fun `빈 목록은 InvalidInputException으로 거부한다`() {
            val exception = shouldThrow<InvalidInputException> {
                service.bulkApprove(emptyList(), "admin", approveCommand())
            }
            exception.message shouldContain "비어있습니다"
        }
    }

    @Nested
    inner class `벌크 반려` {

        @Test
        fun `전체 성공 - 여러 PENDING 요청이 모두 반려된다`() {
            every { adminUserStore.findByUsername("admin") } returns adminAccount
            every { requestStore.findById("req-1") } returns makePendingRequest("req-1")
            every { requestStore.findById("req-2") } returns makePendingRequest("req-2")
            every { requestStore.update(any()) } answers { firstArg() }

            val results = service.bulkReject(listOf("req-1", "req-2"), "admin", "운영 정책에 부적합")

            results shouldHaveSize 2
            results.all { it.second.isSuccess } shouldBe true
            results.forEach { (_, result) ->
                result.getOrThrow().status shouldBe UserClippingRequestStatus.REJECTED
            }
        }

        @Test
        fun `빈 목록은 InvalidInputException으로 거부한다`() {
            val exception = shouldThrow<InvalidInputException> {
                service.bulkReject(emptyList(), "admin", "반려 사유")
            }
            exception.message shouldContain "비어있습니다"
        }
    }
}
