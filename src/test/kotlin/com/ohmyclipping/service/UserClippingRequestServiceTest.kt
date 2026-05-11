package com.ohmyclipping.service

import com.ohmyclipping.service.notification.OperationsNotificationService
import com.ohmyclipping.error.InvalidInputException
import com.ohmyclipping.error.NotFoundException
import com.ohmyclipping.model.*
import com.ohmyclipping.service.dto.clipping.*
import com.ohmyclipping.security.UrlSafetyValidator
import com.ohmyclipping.service.event.SubscriptionReviewNotificationEvent
import com.ohmyclipping.service.event.SubscriptionReviewNotificationType
import com.ohmyclipping.service.dto.ApproveClippingRequestCommand
import com.ohmyclipping.service.dto.UpdateUserSubscriptionPreferenceCommand
import com.ohmyclipping.service.dto.UserAdditionalRssSourcesSubmission
import com.ohmyclipping.service.dto.UserClippingRequestSubmission
import com.ohmyclipping.service.dto.UserRssSourceSubmission
import com.ohmyclipping.service.source.CategorySourceBuilder
import com.ohmyclipping.store.AdminUserStore
import com.ohmyclipping.store.AuditLogStore
import com.ohmyclipping.store.CategoryRuleStore
import com.ohmyclipping.store.CategoryStore
import com.ohmyclipping.store.CompetitorWatchlistStore
import com.ohmyclipping.store.RssSourceStore
import com.ohmyclipping.store.UserClippingRequestStore
import com.ohmyclipping.store.UserDeliveryScheduleStore
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.*
import org.springframework.context.ApplicationEventPublisher
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.net.URI
import java.time.Instant

/** 테스트에서 사용할 기본 승인 커맨드 */
private fun approveCommand(
    legalBasis: String = "QUOTATION_ONLY",
    summaryAllowed: Boolean = true,
    fulltextAllowed: Boolean = false,
    reviewNotes: String? = null
) = ApproveClippingRequestCommand(legalBasis, summaryAllowed, fulltextAllowed, reviewNotes)

class UserClippingRequestServiceTest {

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

    private val userAccount = AdminUser(
        id = "user-1",
        username = "requester",
        passwordHash = "hashed",
        role = AccountRole.USER,
        approvalStatus = AccountApprovalStatus.APPROVED,
        slackDmChannelId = "D_REQUESTER"
    )

    private val adminAccount = AdminUser(
        id = "admin-1",
        username = "admin",
        passwordHash = "hashed",
        role = AccountRole.ADMIN,
        approvalStatus = AccountApprovalStatus.APPROVED
    )

    private val validSubmission = UserClippingRequestSubmission(
        requestName = "AI 뉴스",
        sourceName = "TechCrunch",
        sourceUrl = "https://techcrunch.com/feed",
        slackChannelId = "C0123ABCD",
        personaName = "AI 분석가",
        personaPrompt = "최신 AI 뉴스를 요약합니다",
        summaryStyle = null,
        targetAudience = null,
        requestNote = null
    )

    private fun makePendingRequest(id: String = "req-1") = UserClippingRequest(
        id = id,
        requesterUserId = "user-1",
        requestName = "AI 뉴스",
        sourceName = "TechCrunch",
        sourceUrl = "https://techcrunch.com/feed",
        slackChannelId = "C0123ABCD",
        personaName = "AI 분석가",
        personaPrompt = "prompt",
        status = UserClippingRequestStatus.PENDING
    )

    private fun makeApprovedRequest(
        id: String = "req-1",
        categoryId: String = "cat-1",
        personaId: String? = "persona-1",
        sourceId: String? = "source-1"
    ) = UserClippingRequest(
        id = id,
        requesterUserId = "user-1",
        requestName = "AI 뉴스",
        sourceName = "TechCrunch",
        sourceUrl = "https://techcrunch.com/feed",
        slackChannelId = "C0123ABCD",
        personaName = "AI 분석가",
        personaPrompt = "prompt",
        status = UserClippingRequestStatus.APPROVED,
        approvedCategoryId = categoryId,
        approvedPersonaId = personaId,
        approvedSourceId = sourceId
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
        // 기본: 발송 스케줄 조회 — 스케줄 미설정 사용자 처리 (null 반환)
        every { userDeliveryScheduleStore.findByUserId(any()) } returns null
        // 기본: 카테고리 규칙 생성 — 테스트 기본값으로 허용
        every { adminCategoryRuleService.updateCategoryRule(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            CategoryRule(categoryId = "cat-1")
        // 기본: 구독 한도 체크에 사용되는 기존 요청 목록 (빈 목록)
        every { requestStore.listByRequesterUserId(any()) } returns emptyList()
        // 기본: 구독/월간 생성 한도 count 조회 (한도 미도달)
        every { requestStore.countActiveSubscriptionsByRequesterUserId(any()) } returns 0
        every { requestStore.countCreatedSinceByRequesterUserId(any(), any()) } returns 0
        // 기본: form_entries 없음 (레거시 경로로 라우팅되도록). wizard 요청 테스트는 override 한다.
        every { requestStore.findFormEntries(any()) } returns null
        // 기본: 카테고리 중복 체크용 기존 카테고리 목록 (빈 목록)
        every { categoryStore.findOperational() } returns emptyList()
        // 기본: 승인 시 요청자의 DM 채널 정규화에 사용되는 사용자 조회
        every { adminUserStore.findById("user-1") } returns userAccount
        // 기본: URL 재사용 체크는 기본적으로 기존 소스 없음 처리
        every { sourceStore.findByUrlAndCategoryId(any(), any()) } returns null
        // 기본: 채널 중복 구독 검사 — 기본적으로 채널이 비어 있는 것으로 처리
        every { requestStore.existsBySlackChannelIdAndStatusIn(any(), any()) } returns false
    }

    // ─────────────────────────────────────────────────────────────────────────
    // listOwnRequests
    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    inner class ListOwnRequests {

        @Test
        fun `USER 역할이면 본인 요청 목록을 반환한다`() {
            every { adminUserStore.findByUsername("requester") } returns userAccount
            every { requestStore.listByRequesterUserId("user-1") } returns listOf(
                makePendingRequest("req-1"),
                makeApprovedRequest("req-2")
            )

            val result = service.listOwnRequests("requester")

            result shouldHaveSize 2
        }

        @Test
        fun `ADMIN 역할이면 목록 조회를 거부한다`() {
            every { adminUserStore.findByUsername("admin") } returns adminAccount

            shouldThrow<InvalidInputException> {
                service.listOwnRequests("admin")
            }.message shouldContain "Only USER accounts"
        }

        @Test
        fun `요청이 없으면 빈 리스트를 반환한다`() {
            every { adminUserStore.findByUsername("requester") } returns userAccount
            every { requestStore.listByRequesterUserId("user-1") } returns emptyList()

            val result = service.listOwnRequests("requester")

            result shouldHaveSize 0
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // submitAdditionalRssSources
    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    inner class SubmitAdditionalRssSources {

        private val baseApprovedRequest = makeApprovedRequest("base-req-1")

        @Test
        fun `USER가 복수 소스를 추가 등록하면 각각 저장한다`() {
            every { adminUserStore.findByUsername("requester") } returns userAccount
            every { requestStore.findById("base-req-1") } returns baseApprovedRequest
            every { urlSafetyValidator.validatePublicHttpUrl("https://feed1.com/rss") } returns URI("https://feed1.com/rss")
            every { urlSafetyValidator.validatePublicHttpUrl("https://feed2.com/rss") } returns URI("https://feed2.com/rss")
            every { requestStore.save(any()) } answers { firstArg() }

            val submission = UserAdditionalRssSourcesSubmission(
                baseRequestId = "base-req-1",
                sources = listOf(
                    UserRssSourceSubmission("Feed1", "https://feed1.com/rss"),
                    UserRssSourceSubmission("Feed2", "https://feed2.com/rss")
                ),
                requestNote = null
            )

            val result = service.submitAdditionalRssSources("requester", submission)

            result shouldHaveSize 2
            verify(exactly = 2) { requestStore.save(any()) }
        }

        @Test
        fun `ADMIN 역할이면 추가 등록을 거부한다`() {
            every { adminUserStore.findByUsername("admin") } returns adminAccount

            val submission = UserAdditionalRssSourcesSubmission(
                baseRequestId = "base-req-1",
                sources = listOf(UserRssSourceSubmission("Feed", "https://feed.com/rss")),
                requestNote = null
            )

            shouldThrow<InvalidInputException> {
                service.submitAdditionalRssSources("admin", submission)
            }.message shouldContain "Only USER accounts"
        }

        @Test
        fun `소스 목록이 비어 있으면 예외를 발생시킨다`() {
            every { adminUserStore.findByUsername("requester") } returns userAccount

            val submission = UserAdditionalRssSourcesSubmission(
                baseRequestId = "base-req-1",
                sources = emptyList(),
                requestNote = null
            )

            shouldThrow<InvalidInputException> {
                service.submitAdditionalRssSources("requester", submission)
            }.message shouldContain "sources must not be empty"
        }

        @Test
        fun `baseRequestId가 공백이면 예외를 발생시킨다`() {
            every { adminUserStore.findByUsername("requester") } returns userAccount

            val submission = UserAdditionalRssSourcesSubmission(
                baseRequestId = "  ",
                sources = listOf(UserRssSourceSubmission("Feed", "https://feed.com/rss")),
                requestNote = null
            )

            shouldThrow<InvalidInputException> {
                service.submitAdditionalRssSources("requester", submission)
            }.message shouldContain "baseRequestId is required"
        }

        @Test
        fun `소스 URL 안전 검증 실패 시 예외를 발생시킨다`() {
            every { adminUserStore.findByUsername("requester") } returns userAccount
            every { requestStore.findById("base-req-1") } returns baseApprovedRequest
            every { urlSafetyValidator.validatePublicHttpUrl(any()) } throws InvalidInputException("Unsafe URL")

            val submission = UserAdditionalRssSourcesSubmission(
                baseRequestId = "base-req-1",
                sources = listOf(UserRssSourceSubmission("Feed", "http://192.168.0.1/rss")),
                requestNote = null
            )

            shouldThrow<InvalidInputException> {
                service.submitAdditionalRssSources("requester", submission)
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getImmediateSubscriptionPreference
    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    inner class GetImmediateSubscriptionPreference {

        @Test
        fun `본인 승인 구독이면 설정 조회를 반환한다`() {
            every { adminUserStore.findByUsername("requester") } returns userAccount
            every { requestStore.findById("req-1") } returns makeApprovedRequest()
            val category = Category(id = "cat-1", name = "AI 뉴스", isActive = true, maxItems = 5)
            every { adminCategoryService.getCategory("cat-1") } returns category
            val rule = CategoryRule(
                categoryId = "cat-1",
                excludeKeywords = listOf("스팸"),
                includeThreshold = 0.6,
                deliveryDays = listOf("MON"),
                deliveryHour = 9
            )
            every { adminCategoryRuleService.getCategoryRule("cat-1") } returns rule

            val result = service.getImmediateSubscriptionPreference("requester", "req-1")

            result.categoryId shouldBe "cat-1"
            result.isActive shouldBe true
            result.includeThreshold shouldBe 0.6
        }

        @Test
        fun `다른 사용자의 요청에 접근하면 예외를 발생시킨다`() {
            val otherUser = userAccount.copy(id = "other-user-id")
            every { adminUserStore.findByUsername("other") } returns otherUser
            every { requestStore.findById("req-1") } returns makeApprovedRequest()

            shouldThrow<InvalidInputException> {
                service.getImmediateSubscriptionPreference("other", "req-1")
            }.message shouldContain "본인 구독만"
        }

        @Test
        fun `승인되지 않은 요청에는 설정 조회가 불가하다`() {
            every { adminUserStore.findByUsername("requester") } returns userAccount
            every { requestStore.findById("req-1") } returns makePendingRequest()

            shouldThrow<InvalidInputException> {
                service.getImmediateSubscriptionPreference("requester", "req-1")
            }.message shouldContain "승인된 구독만"
        }

        @Test
        fun `approvedCategoryId가 null인 APPROVED 요청은 예외를 발생시킨다`() {
            every { adminUserStore.findByUsername("requester") } returns userAccount
            val noCategoryRequest = makeApprovedRequest().copy(approvedCategoryId = null)
            every { requestStore.findById("req-1") } returns noCategoryRequest

            shouldThrow<InvalidInputException> {
                service.getImmediateSubscriptionPreference("requester", "req-1")
            }
        }

        @Test
        fun `rule에 발송 설정이 없으면 사용자 글로벌 스케줄을 폴백으로 merge해 응답한다`() {
            // 배경: PR #492 이전 승인건이나 rule 미생성 카테고리에서는
            // rule.delivery_* 가 모두 null. 위자드에서 고른 값은 user_delivery_schedules 에 살아있다.
            every { adminUserStore.findByUsername("requester") } returns userAccount
            every { requestStore.findById("req-1") } returns makeApprovedRequest()
            val category = Category(id = "cat-1", name = "AI 뉴스", isActive = true, maxItems = 5)
            every { adminCategoryService.getCategory("cat-1") } returns category
            val ruleWithoutSchedule = CategoryRule(
                categoryId = "cat-1",
                deliveryDays = null,
                deliveryHour = null,
                deliveryPreset = null
            )
            every { adminCategoryRuleService.getCategoryRule("cat-1") } returns ruleWithoutSchedule
            every { userDeliveryScheduleStore.findByUserId("user-1") } returns UserDeliverySchedule(
                userId = "user-1",
                deliveryDays = listOf("MON", "WED", "FRI"),
                deliveryHour = 18,
                preset = DeliveryPreset.CUSTOM
            )

            val result = service.getImmediateSubscriptionPreference("requester", "req-1")

            result.deliveryDays shouldBe listOf("MON", "WED", "FRI")
            result.deliveryHour shouldBe 18
            result.deliveryPreset shouldBe "CUSTOM"
            verify(exactly = 1) { userDeliveryScheduleStore.findByUserId("user-1") }
        }

        @Test
        fun `rule에 발송 설정이 모두 있으면 글로벌 스케줄을 조회하지 않는다`() {
            // 최적화 회귀 방지: 이미 카테고리별로 확정된 스케줄이 있는데 DB 를 한 번 더 때리면 낭비.
            every { adminUserStore.findByUsername("requester") } returns userAccount
            every { requestStore.findById("req-1") } returns makeApprovedRequest()
            val category = Category(id = "cat-1", name = "AI 뉴스", isActive = true, maxItems = 5)
            every { adminCategoryService.getCategory("cat-1") } returns category
            val fullyPopulatedRule = CategoryRule(
                categoryId = "cat-1",
                deliveryDays = listOf("TUE", "THU"),
                deliveryHour = 12,
                deliveryPreset = DeliveryPreset.CUSTOM
            )
            every { adminCategoryRuleService.getCategoryRule("cat-1") } returns fullyPopulatedRule

            val result = service.getImmediateSubscriptionPreference("requester", "req-1")

            result.deliveryDays shouldBe listOf("TUE", "THU")
            result.deliveryHour shouldBe 12
            result.deliveryPreset shouldBe "CUSTOM"
            verify(exactly = 0) { userDeliveryScheduleStore.findByUserId(any()) }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // updateImmediateSubscriptionPreference
    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    inner class UpdateImmediateSubscriptionPreference {

        @Test
        fun `카테고리와 규칙 필드가 모두 있으면 둘 다 갱신한다`() {
            every { adminUserStore.findByUsername("requester") } returns userAccount
            every { requestStore.findById("req-1") } returns makeApprovedRequest()
            val category = Category(id = "cat-1", name = "AI 뉴스", isActive = true, maxItems = 5)
            every { adminCategoryService.getCategory("cat-1") } returns category
            every { adminCategoryService.updateCategory(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns category
            val rule = CategoryRule(categoryId = "cat-1")
            every { adminCategoryRuleService.getCategoryRule("cat-1") } returns rule
            every {
                adminCategoryRuleService.updateCategoryRule(
                    any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
                )
            } returns rule

            val command = UpdateUserSubscriptionPreferenceCommand(
                isActive = false,
                maxItems = 10,
                excludeKeywords = listOf("광고"),
                includeThreshold = 0.7,
                deliveryDays = listOf("MON"),
                deliveryHour = 8
            )

            val result = service.updateImmediateSubscriptionPreference("requester", "req-1", command)

            result.shouldNotBeNull()
            verify(exactly = 1) { adminCategoryService.updateCategory(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
            verify(exactly = 1) {
                adminCategoryRuleService.updateCategoryRule(
                    any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
                )
            }
        }

        @Test
        fun `null 필드만 있으면 카테고리 update와 rule update를 모두 건너뛴다`() {
            every { adminUserStore.findByUsername("requester") } returns userAccount
            every { requestStore.findById("req-1") } returns makeApprovedRequest()
            val category = Category(id = "cat-1", name = "AI 뉴스", isActive = true, maxItems = 5)
            every { adminCategoryService.getCategory("cat-1") } returns category
            val rule = CategoryRule(categoryId = "cat-1")
            every { adminCategoryRuleService.getCategoryRule("cat-1") } returns rule

            val command = UpdateUserSubscriptionPreferenceCommand(
                isActive = null,
                maxItems = null,
                excludeKeywords = null,
                includeThreshold = null,
                deliveryDays = null,
                deliveryHour = null,
                deliveryPreset = null
            )

            service.updateImmediateSubscriptionPreference("requester", "req-1", command)

            verify(exactly = 0) { adminCategoryService.updateCategory(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
            verify(exactly = 0) {
                adminCategoryRuleService.updateCategoryRule(
                    any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
                )
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // registerWizardOwnership
    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    inner class RegisterWizardOwnership {

        @Test
        fun `위자드 등록이 성공하면 PENDING 상태의 요청을 저장한다`() {
            every { adminUserStore.findByUsername("requester") } returns userAccount
            every { userSetupOwnershipService.registerOwnedResources(any(), any(), any(), any()) } just runs
            every { requestStore.listByRequesterUserId("user-1") } returns emptyList()
            every { requestStore.save(any()) } answers { firstArg() }

            val result = service.registerWizardOwnership(
                requesterUsername = "requester",
                requestName = "테스트 구독",
                sourceName = "RSS 소스",
                sourceUrl = "https://example.com/rss",
                slackChannelId = "C0123",
                personaName = "분석가",
                personaPrompt = "뉴스를 요약합니다",
                summaryStyle = null,
                targetAudience = null,
                selectedPresetId = null,
                categoryId = "cat-new",
                personaId = "persona-new",
                sourceId = "source-new"
            )

            result.status shouldBe UserClippingRequestStatus.PENDING
            result.approvedCategoryId shouldBe "cat-new"
            verify(exactly = 1) { requestStore.save(any()) }
        }

        @Test
        fun `위자드 등록에서 빈 slackChannelId는 사용자의 DM 채널로 저장한다`() {
            every { adminUserStore.findByUsername("requester") } returns userAccount
            every { userSetupOwnershipService.registerOwnedResources(any(), any(), any(), any()) } just runs
            every { requestStore.listByRequesterUserId("user-1") } returns emptyList()
            val savedSlot = slot<UserClippingRequest>()
            every { requestStore.save(capture(savedSlot)) } answers { firstArg() }

            service.registerWizardOwnership(
                requesterUsername = "requester",
                requestName = "DM 등록",
                sourceName = "RSS 소스",
                sourceUrl = "https://example.com/rss",
                slackChannelId = "",
                personaName = "분석가",
                personaPrompt = "뉴스를 요약합니다",
                summaryStyle = null,
                targetAudience = null,
                selectedPresetId = null,
                categoryId = "cat-new",
                personaId = "persona-new",
                sourceId = "source-new"
            )

            savedSlot.captured.slackChannelId shouldBe "D_REQUESTER"
        }

        @Test
        fun `이미 동일 카테고리에 APPROVED 요청이 있으면 기존 레코드를 반환한다`() {
            every { adminUserStore.findByUsername("requester") } returns userAccount
            every { userSetupOwnershipService.registerOwnedResources(any(), any(), any(), any()) } just runs
            val existingApproved = makeApprovedRequest(categoryId = "cat-existing")
            every { requestStore.listByRequesterUserId("user-1") } returns listOf(existingApproved)

            val result = service.registerWizardOwnership(
                requesterUsername = "requester",
                requestName = "중복 구독",
                sourceName = "소스",
                sourceUrl = "https://example.com/rss",
                slackChannelId = "C0123",
                personaName = "분석가",
                personaPrompt = "prompt",
                summaryStyle = null,
                targetAudience = null,
                selectedPresetId = null,
                categoryId = "cat-existing",
                personaId = null,
                sourceId = null
            )

            result.id shouldBe existingApproved.id
            verify(exactly = 0) { requestStore.save(any()) }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // resolveCategoryName
    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    inner class ResolveCategoryName {

        @Test
        fun `카테고리가 존재하면 이름을 반환한다`() {
            every { categoryStore.findById("cat-1") } returns Category(id = "cat-1", name = "AI 뉴스")

            val result = service.resolveCategoryName("cat-1")

            result shouldBe "AI 뉴스"
        }

        @Test
        fun `카테고리가 존재하지 않으면 null을 반환한다`() {
            every { categoryStore.findById("unknown") } returns null

            val result = service.resolveCategoryName("unknown")

            result.shouldBeNull()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // listAllRequests
    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    inner class ListAllRequests {

        @Test
        fun `status가 null이면 전체 목록을 반환한다`() {
            val allRequests = listOf(makePendingRequest("r-1"), makeApprovedRequest("r-2"))
            every { requestStore.listAll(null) } returns allRequests

            val result = service.listAllRequests(null)

            result shouldHaveSize 2
            verify(exactly = 1) { requestStore.listAll(null) }
        }

        @Test
        fun `특정 상태로 필터링하면 해당 status를 스토어에 전달한다`() {
            every { requestStore.listAll(UserClippingRequestStatus.PENDING) } returns listOf(makePendingRequest())

            val result = service.listAllRequests(UserClippingRequestStatus.PENDING)

            result shouldHaveSize 1
            verify(exactly = 1) { requestStore.listAll(UserClippingRequestStatus.PENDING) }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // submitRequest (edge cases)
    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    inner class SubmitRequest {

        @Test
        fun `USER 역할이면 요청을 정상 등록한다`() {
            every { adminUserStore.findByUsername("requester") } returns userAccount
            every { urlSafetyValidator.validatePublicHttpUrl(any()) } returns URI("https://techcrunch.com/feed")
            every { requestStore.save(any()) } answers { firstArg() }

            val result = service.submitRequest("requester", validSubmission)

            result.requestName shouldBe "AI 뉴스"
            result.status shouldBe UserClippingRequestStatus.PENDING
            verify(exactly = 1) { requestStore.save(any()) }
        }

        @Test
        fun `ADMIN 역할이면 요청 등록을 거부한다`() {
            every { adminUserStore.findByUsername("admin") } returns adminAccount

            shouldThrow<InvalidInputException> {
                service.submitRequest("admin", validSubmission)
            }.message shouldContain "Only USER accounts"
        }

        @Test
        fun `URL 안전 검증에 실패하면 예외를 발생시킨다`() {
            every { adminUserStore.findByUsername("requester") } returns userAccount
            every { urlSafetyValidator.validatePublicHttpUrl(any()) } throws
                InvalidInputException("Unsafe URL")

            shouldThrow<InvalidInputException> {
                service.submitRequest("requester", validSubmission)
            }
        }

        @Test
        fun `필수 필드가 비어 있으면 예외를 발생시킨다`() {
            every { adminUserStore.findByUsername("requester") } returns userAccount

            shouldThrow<InvalidInputException> {
                service.submitRequest(
                    "requester",
                    validSubmission.copy(requestName = "")
                )
            }.message shouldContain "요청 이름"
        }

        @Test
        fun `sourceName이 비어 있으면 예외를 발생시킨다`() {
            every { adminUserStore.findByUsername("requester") } returns userAccount

            shouldThrow<InvalidInputException> {
                service.submitRequest(
                    "requester",
                    validSubmission.copy(sourceName = "  ")
                )
            }.message shouldContain "소스 이름"
        }

        @Test
        fun `sourceUrl이 공백이면 예외를 발생시킨다`() {
            every { adminUserStore.findByUsername("requester") } returns userAccount

            shouldThrow<InvalidInputException> {
                service.submitRequest(
                    "requester",
                    validSubmission.copy(sourceUrl = "   ")
                )
            }.message shouldContain "sourceUrl is required"
        }

        @Test
        fun `slackChannelId가 공백이면 사용자의 DM 채널로 정규화한다`() {
            every { adminUserStore.findByUsername("requester") } returns userAccount
            val savedSlot = slot<UserClippingRequest>()
            every { urlSafetyValidator.validatePublicHttpUrl(any()) } returns URI("https://techcrunch.com/feed")
            every { requestStore.save(capture(savedSlot)) } answers { firstArg() }

            service.submitRequest(
                "requester",
                validSubmission.copy(slackChannelId = "  ")
            )

            savedSlot.captured.slackChannelId shouldBe "D_REQUESTER"
        }

        @Test
        fun `slackChannelId가 DM 문자열이면 사용자의 DM 채널로 정규화한다`() {
            every { adminUserStore.findByUsername("requester") } returns userAccount
            val savedSlot = slot<UserClippingRequest>()
            every { urlSafetyValidator.validatePublicHttpUrl(any()) } returns URI("https://techcrunch.com/feed")
            every { requestStore.save(capture(savedSlot)) } answers { firstArg() }

            service.submitRequest(
                "requester",
                validSubmission.copy(slackChannelId = "DM")
            )

            savedSlot.captured.slackChannelId shouldBe "D_REQUESTER"
        }

        @Test
        fun `DM 채널이 설정되지 않은 사용자가 공백 slackChannelId를 보내면 예외를 발생시킨다`() {
            val userWithoutDm = userAccount.copy(slackDmChannelId = null)
            every { adminUserStore.findByUsername("requester") } returns userWithoutDm

            shouldThrow<InvalidInputException> {
                service.submitRequest(
                    "requester",
                    validSubmission.copy(slackChannelId = "  ")
                )
            }.message shouldContain "Slack DM 채널 ID가 설정되지 않았습니다"
        }

        @Test
        fun `personaName이 공백이면 예외를 발생시킨다`() {
            every { adminUserStore.findByUsername("requester") } returns userAccount

            shouldThrow<InvalidInputException> {
                service.submitRequest(
                    "requester",
                    validSubmission.copy(personaName = "")
                )
            }.message shouldContain "페르소나 이름"
        }

        @Test
        fun `personaPrompt가 공백이면 예외를 발생시킨다`() {
            every { adminUserStore.findByUsername("requester") } returns userAccount

            shouldThrow<InvalidInputException> {
                service.submitRequest(
                    "requester",
                    validSubmission.copy(personaPrompt = "  ")
                )
            }.message shouldContain "페르소나 프롬프트"
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // approveRequest (edge cases)
    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    inner class ApproveRequest {

        @Test
        fun `ADMIN이 PENDING 요청을 승인하면 페르소나, 카테고리, 소스가 생성된다`() {
            every { adminUserStore.findByUsername("admin") } returns adminAccount
            every { requestStore.findById("req-1") } returns makePendingRequest()
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
                Persona(id = "persona-1", name = "AI 분석가", systemPrompt = "prompt")
            every { categoryStore.save(any()) } returns
                Category(id = "cat-1", name = "AI 뉴스")
            every {
                adminSourceService.createSource(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
            } returns RssSource(id = "source-1", name = "TechCrunch", url = "https://techcrunch.com/feed", categoryId = "cat-1")
            every { adminSourceService.approveSource(any(), any(), any(), any(), any(), any(), any(), any()) } returns
                RssSource(id = "s-approved", name = "approved", url = "u", categoryId = "cat-1")
            every { userSetupOwnershipService.registerOwnedResources(any(), any(), any(), any()) } just runs

            val result = service.approveRequest("req-1", "admin", approveCommand(reviewNotes = "승인합니다"))

            result.status shouldBe UserClippingRequestStatus.APPROVED
            result.approvedCategoryId shouldBe "cat-1"
            result.approvedPersonaId shouldBe "persona-1"
            result.approvedSourceId shouldBe "source-1"
            verify(exactly = 1) {
                applicationEventPublisher.publishEvent(
                    match<SubscriptionReviewNotificationEvent> {
                        it.userId == "user-1" &&
                            it.requestName == "AI 뉴스" &&
                            it.reviewType == SubscriptionReviewNotificationType.APPROVED &&
                            it.reviewNote == null
                    }
                )
            }
        }

        @Test
        fun `ADMIN이 아니면 승인을 거부한다`() {
            every { adminUserStore.findByUsername("requester") } returns userAccount

            shouldThrow<InvalidInputException> {
                service.approveRequest("req-1", "requester", approveCommand())
            }.message shouldContain "Only ADMIN accounts"
        }

        @Test
        fun `이미 승인된 요청은 재승인할 수 없다`() {
            every { adminUserStore.findByUsername("admin") } returns adminAccount
            every { requestStore.findById("req-1") } returns makeApprovedRequest()

            shouldThrow<InvalidInputException> {
                service.approveRequest("req-1", "admin", approveCommand())
            }.message shouldContain "already reviewed"
        }

        @Test
        fun `REJECTED 상태 요청은 승인할 수 없다`() {
            every { adminUserStore.findByUsername("admin") } returns adminAccount
            val rejectedRequest = makePendingRequest().copy(status = UserClippingRequestStatus.REJECTED)
            every { requestStore.findById("req-1") } returns rejectedRequest

            shouldThrow<InvalidInputException> {
                service.approveRequest("req-1", "admin", approveCommand())
            }.message shouldContain "already reviewed"
        }

        @Test
        fun `Slack testConnection이 ok=false를 반환하면 승인을 거부한다`() {
            every { adminUserStore.findByUsername("admin") } returns adminAccount
            every { requestStore.findById("req-1") } returns makePendingRequest()
            every { runtimeSettingService.current() } returns mockk {
                every { slackBotToken } returns "xoxb-test"
            }
            every { slackMessageSender.testConnection(any(), any()) } returns
                SlackMessageSender.SlackConnectionTestResult(
                    ok = false, botUser = null, team = null,
                    channelId = null, channelName = null,
                    neededScopes = null, providedScopes = null, rawError = "channel_not_found"
                )
            every { adminPersonaService.createPersona(any(), any(), any(), any(), any(), any(), any()) } returns
                Persona(id = "p-1", name = "AI 분석가", systemPrompt = "prompt")

            shouldThrow<InvalidInputException> {
                service.approveRequest("req-1", "admin", approveCommand())
            }.message shouldContain "Slack 채널 검증 실패"
        }

        @Test
        fun `DM 채널은 Slack 검증을 건너뛴다`() {
            val dmRequest = makePendingRequest().copy(slackChannelId = "D0123ABCD")
            every { adminUserStore.findByUsername("admin") } returns adminAccount
            every { requestStore.findById("req-1") } returns dmRequest
            every { requestStore.update(any()) } answers { firstArg() }
            every { adminPersonaService.createPersona(any(), any(), any(), any(), any(), any(), any()) } returns
                Persona(id = "p-1", name = "test", systemPrompt = "p")
            every { categoryStore.save(any()) } returns Category(id = "c-1", name = "test")
            every {
                adminSourceService.createSource(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
            } returns RssSource(id = "s-1", name = "s", url = "u", categoryId = "c-1")
            every { adminSourceService.approveSource(any(), any(), any(), any(), any(), any(), any(), any()) } returns
                RssSource(id = "s-approved", name = "approved", url = "u", categoryId = "cat-1")
            every { userSetupOwnershipService.registerOwnedResources(any(), any(), any(), any()) } just runs

            service.approveRequest("req-1", "admin", approveCommand())

            verify(exactly = 0) { slackMessageSender.testConnection(any(), any()) }
        }

        @Test
        fun `공유 채널 요청 승인은 requester DM을 다시 조회하지 않는다`() {
            val sharedChannelRequest = makePendingRequest().copy(slackChannelId = "CSHARED01")
            every { adminUserStore.findByUsername("admin") } returns adminAccount
            every { requestStore.findById("req-1") } returns sharedChannelRequest
            val updatedSlot = slot<UserClippingRequest>()
            every { requestStore.update(capture(updatedSlot)) } answers { firstArg() }
            every { runtimeSettingService.current() } returns mockk {
                every { slackBotToken } returns "xoxb-test"
            }
            every { slackMessageSender.testConnection(any(), any()) } returns
                SlackMessageSender.SlackConnectionTestResult(
                    ok = true, botUser = "bot", team = "team",
                    channelId = "CSHARED01", channelName = "shared",
                    neededScopes = null, providedScopes = null, rawError = null
                )
            every { adminPersonaService.createPersona(any(), any(), any(), any(), any(), any(), any()) } returns
                Persona(id = "p-1", name = "test", systemPrompt = "p")
            every { categoryStore.save(any()) } returns Category(id = "c-1", name = "test")
            every {
                adminSourceService.createSource(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
            } returns RssSource(id = "s-1", name = "s", url = "u", categoryId = "c-1")
            every { adminSourceService.approveSource(any(), any(), any(), any(), any(), any(), any(), any()) } returns
                RssSource(id = "s-approved", name = "approved", url = "u", categoryId = "c-1")
            every { userSetupOwnershipService.registerOwnedResources(any(), any(), any(), any()) } just runs

            service.approveRequest("req-1", "admin", approveCommand())

            updatedSlot.captured.slackChannelId shouldBe "CSHARED01"
            // 운영 알림에서 username 조회를 위해 findById가 호출될 수 있으므로 DM 조회 목적의 호출만 검증한다
        }

        @Test
        fun `빈 slackChannelId는 사용자 DM 채널로 정규화한 뒤 Slack 검증을 건너뛴다`() {
            val emptyChannelRequest = makePendingRequest().copy(slackChannelId = "")
            every { adminUserStore.findByUsername("admin") } returns adminAccount
            every { requestStore.findById("req-1") } returns emptyChannelRequest
            val updatedSlot = slot<UserClippingRequest>()
            every { requestStore.update(capture(updatedSlot)) } answers { firstArg() }
            every { adminPersonaService.createPersona(any(), any(), any(), any(), any(), any(), any()) } returns
                Persona(id = "p-1", name = "test", systemPrompt = "p")
            every { categoryStore.save(any()) } returns Category(id = "c-1", name = "test")
            every {
                adminSourceService.createSource(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
            } returns RssSource(id = "s-1", name = "s", url = "u", categoryId = "c-1")
            every { adminSourceService.approveSource(any(), any(), any(), any(), any(), any(), any(), any()) } returns
                RssSource(id = "s-approved", name = "approved", url = "u", categoryId = "c-1")
            every { userSetupOwnershipService.registerOwnedResources(any(), any(), any(), any()) } just runs

            service.approveRequest("req-1", "admin", approveCommand())

            updatedSlot.captured.slackChannelId shouldBe "D_REQUESTER"
            verify(exactly = 0) { slackMessageSender.testConnection(any(), any()) }
        }

        @Test
        fun `DM 채널이 없는 사용자의 blank DM 요청은 승인할 수 없다`() {
            val requesterWithoutDm = userAccount.copy(slackDmChannelId = null)
            val emptyChannelRequest = makePendingRequest().copy(slackChannelId = "")
            every { adminUserStore.findByUsername("admin") } returns adminAccount
            every { adminUserStore.findById("user-1") } returns requesterWithoutDm
            every { requestStore.findById("req-1") } returns emptyChannelRequest

            shouldThrow<InvalidInputException> {
                service.approveRequest("req-1", "admin", approveCommand())
            }.message shouldContain "Slack DM 채널 ID가 설정되지 않았습니다"
        }

        @Test
        fun `잘못된 법적 근거 enum이면 InvalidInputException`() {
            every { adminUserStore.findByUsername("admin") } returns adminAccount

            shouldThrow<InvalidInputException> {
                service.approveRequest("req-1", "admin", approveCommand(legalBasis = "BOGUS"))
            }.message shouldContain "올바르지 않은 법적 근거"
        }

        @Test
        fun `검토 메모가 200자 초과면 InvalidInputException`() {
            every { adminUserStore.findByUsername("admin") } returns adminAccount
            val longNote = "x".repeat(201)

            shouldThrow<InvalidInputException> {
                service.approveRequest("req-1", "admin", approveCommand(reviewNotes = longNote))
            }.message shouldContain "200자 이내여야"
        }

        @Test
        fun `command의 정책이 RssSource 생성에 그대로 전달된다`() {
            every { adminUserStore.findByUsername("admin") } returns adminAccount
            every { requestStore.findById("req-1") } returns makePendingRequest()
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
            every { categoryStore.save(any()) } returns Category(id = "cat-1", name = "AI 뉴스")
            every {
                adminSourceService.createSource(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
            } returns RssSource(id = "source-1", name = "x", url = "u", categoryId = "cat-1")
            every { userSetupOwnershipService.registerOwnedResources(any(), any(), any(), any()) } just runs

            service.approveRequest("req-1", "admin", approveCommand(
                legalBasis = "OPEN_LICENSE",
                summaryAllowed = true,
                fulltextAllowed = true,
                reviewNotes = "CC 라이선스 확인"
            ))

            verify(exactly = 1) {
                adminSourceService.createSource(
                    any(), any(), any(), any(), any(),
                    legalBasisRaw = "OPEN_LICENSE",
                    summaryAllowed = true,
                    fulltextAllowed = true,
                    reviewNotes = "CC 라이선스 확인",
                    crawlApproved = true,
                    approvedBy = "admin"
                )
            }
        }

        @Test
        fun `URL 재사용 케이스에서는 새 소스를 생성하지 않는다`() {
            val existing = RssSource(
                id = "existing-1",
                name = "TechCrunch",
                url = "https://techcrunch.com/feed",
                categoryId = "cat-1",
                crawlApproved = true
            )
            every { adminUserStore.findByUsername("admin") } returns adminAccount
            every { requestStore.findById("req-1") } returns makePendingRequest()
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
            every { categoryStore.save(any()) } returns Category(id = "cat-1", name = "AI 뉴스")
            // 같은 URL+카테고리 기존 소스 발견
            every { sourceStore.findByUrlAndCategoryId(any(), any()) } returns existing
            every { userSetupOwnershipService.registerOwnedResources(any(), any(), any(), any()) } just runs

            val result = service.approveRequest("req-1", "admin", approveCommand())

            result.approvedSourceId shouldBe "existing-1"
            // 새 소스 생성 절대 호출 없음
            verify(exactly = 0) {
                adminSourceService.createSource(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
            }
        }

        @Test
        fun `승인 시 사용자 발송 스케줄이 있으면 카테고리 규칙에 복사된다`() {
            val userSchedule = UserDeliverySchedule(
                userId = "user-1",
                deliveryDays = listOf("MON", "WED", "FRI"),
                deliveryHour = 12,
                preset = DeliveryPreset.CUSTOM
            )
            every { userDeliveryScheduleStore.findByUserId("user-1") } returns userSchedule
            every { adminUserStore.findByUsername("admin") } returns adminAccount
            every { requestStore.findById("req-1") } returns makePendingRequest()
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
                Persona(id = "persona-1", name = "AI 분석가", systemPrompt = "prompt")
            every { categoryStore.save(any()) } returns Category(id = "cat-1", name = "AI 뉴스")
            every {
                adminSourceService.createSource(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
            } returns RssSource(id = "source-1", name = "TechCrunch", url = "https://techcrunch.com/feed", categoryId = "cat-1")
            every { userSetupOwnershipService.registerOwnedResources(any(), any(), any(), any()) } just runs
            val ruleSlot = slot<String>()
            every {
                adminCategoryRuleService.updateCategoryRule(
                    categoryId = capture(ruleSlot),
                    any(), any(), any(), any(), any(), any(), any(), any(),
                    any(), any(), any()
                )
            } returns CategoryRule(categoryId = "cat-1")

            service.approveRequest("req-1", "admin", approveCommand())

            // 카테고리 규칙 생성 시 사용자 스케줄이 반영되었는지 확인
            verify(atLeast = 1) {
                adminCategoryRuleService.updateCategoryRule(
                    categoryId = "cat-1",
                    includeKeywords = null,
                    excludeKeywords = null,
                    riskTags = null,
                    includeThreshold = null,
                    reviewThreshold = null,
                    uncertainToReview = null,
                    autoExcludeEnabled = null,
                    updatedBy = "admin",
                    deliveryDays = listOf("MON", "WED", "FRI"),
                    deliveryHour = 12,
                    deliveryPreset = "CUSTOM"
                )
            }
        }

        @Test
        fun `승인 시 사용자 발송 스케줄이 없으면 기본 평일 오전 8시 규칙을 생성한다`() {
            every { userDeliveryScheduleStore.findByUserId("user-1") } returns null
            every { adminUserStore.findByUsername("admin") } returns adminAccount
            every { requestStore.findById("req-1") } returns makePendingRequest()
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
                Persona(id = "persona-1", name = "AI 분석가", systemPrompt = "prompt")
            every { categoryStore.save(any()) } returns Category(id = "cat-1", name = "AI 뉴스")
            every {
                adminSourceService.createSource(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
            } returns RssSource(id = "source-1", name = "TechCrunch", url = "https://techcrunch.com/feed", categoryId = "cat-1")
            every { userSetupOwnershipService.registerOwnedResources(any(), any(), any(), any()) } just runs

            service.approveRequest("req-1", "admin", approveCommand())

            // 사용자 스케줄 없으면 기본값(평일, 8시) 사용 여부 확인
            verify(atLeast = 1) {
                adminCategoryRuleService.updateCategoryRule(
                    categoryId = "cat-1",
                    includeKeywords = null,
                    excludeKeywords = null,
                    riskTags = null,
                    includeThreshold = null,
                    reviewThreshold = null,
                    uncertainToReview = null,
                    autoExcludeEnabled = null,
                    updatedBy = "admin",
                    deliveryDays = listOf("MON", "TUE", "WED", "THU", "FRI"),
                    deliveryHour = 8,
                    deliveryPreset = "WEEKDAYS"
                )
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // rejectRequest (edge cases)
    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    inner class RejectRequest {

        @Test
        fun `ADMIN이 반려 사유와 함께 요청을 반려한다`() {
            every { adminUserStore.findByUsername("admin") } returns adminAccount
            every { requestStore.findById("req-1") } returns makePendingRequest()
            every { requestStore.update(any()) } answers { firstArg() }

            val result = service.rejectRequest("req-1", "admin", "내용 보완 필요")

            result.status shouldBe UserClippingRequestStatus.REJECTED
            result.reviewNote shouldBe "내용 보완 필요"
            verify(exactly = 1) {
                applicationEventPublisher.publishEvent(
                    match<SubscriptionReviewNotificationEvent> {
                        it.userId == "user-1" &&
                            it.requestName == "AI 뉴스" &&
                            it.reviewType == SubscriptionReviewNotificationType.REJECTED &&
                            it.reviewNote == "내용 보완 필요"
                    }
                )
            }
        }

        @Test
        fun `ADMIN이 아닌 사용자는 반려할 수 없다`() {
            every { adminUserStore.findByUsername("requester") } returns userAccount

            shouldThrow<InvalidInputException> {
                service.rejectRequest("req-1", "requester", "이유")
            }.message shouldContain "Only ADMIN accounts"
        }

        @Test
        fun `반려 사유가 비어 있으면 예외를 발생시킨다`() {
            every { adminUserStore.findByUsername("admin") } returns adminAccount
            every { requestStore.findById("req-1") } returns makePendingRequest()

            shouldThrow<InvalidInputException> {
                service.rejectRequest("req-1", "admin", "  ")
            }.message shouldContain "reviewNote is required"
        }

        @Test
        fun `반려 사유가 null이면 예외를 발생시킨다`() {
            every { adminUserStore.findByUsername("admin") } returns adminAccount
            every { requestStore.findById("req-1") } returns makePendingRequest()

            shouldThrow<InvalidInputException> {
                service.rejectRequest("req-1", "admin", null)
            }.message shouldContain "reviewNote is required"
        }

        @Test
        fun `이미 APPROVED된 요청은 반려할 수 없다`() {
            every { adminUserStore.findByUsername("admin") } returns adminAccount
            every { requestStore.findById("req-1") } returns makeApprovedRequest()

            shouldThrow<InvalidInputException> {
                service.rejectRequest("req-1", "admin", "이유")
            }.message shouldContain "already reviewed"
        }

        @Test
        fun `이미 WITHDRAWN된 요청은 반려할 수 없다`() {
            every { adminUserStore.findByUsername("admin") } returns adminAccount
            val withdrawnRequest = makePendingRequest().copy(status = UserClippingRequestStatus.WITHDRAWN)
            every { requestStore.findById("req-1") } returns withdrawnRequest

            shouldThrow<InvalidInputException> {
                service.rejectRequest("req-1", "admin", "이유")
            }.message shouldContain "already reviewed"
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getDeliveryStatus (edge cases)
    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    inner class GetDeliveryStatus {

        @Test
        fun `PENDING 상태면 PENDING_REVIEW를 반환한다`() {
            val result = service.getDeliveryStatus(makePendingRequest())
            result.deliveryState shouldBe "PENDING_REVIEW"
        }

        @Test
        fun `REJECTED 상태면 REJECTED를 반환한다`() {
            val request = makePendingRequest().copy(status = UserClippingRequestStatus.REJECTED)
            val result = service.getDeliveryStatus(request)
            result.deliveryState shouldBe "REJECTED"
        }

        @Test
        fun `WITHDRAWN 상태면 WITHDRAWN을 반환한다`() {
            val request = makePendingRequest().copy(status = UserClippingRequestStatus.WITHDRAWN)
            val result = service.getDeliveryStatus(request)
            result.deliveryState shouldBe "WITHDRAWN"
        }

        @Test
        fun `APPROVED + 활성 카테고리 + 검증 소스가 있으면 ACTIVE를 반환한다`() {
            val request = makeApprovedRequest()
            every { categoryStore.findById("cat-1") } returns Category(id = "cat-1", name = "test", isActive = true)
            every { sourceStore.listByCategoryId("cat-1") } returns listOf(
                RssSource(
                    id = "s-1", name = "s", url = "u", categoryId = "cat-1",
                    isActive = true, crawlApproved = true, summaryAllowed = true,
                    legalBasis = SourceLegalBasis.QUOTATION_ONLY,
                    verificationStatus = "VERIFIED"
                )
            )

            val result = service.getDeliveryStatus(request)

            result.deliveryState shouldBe "ACTIVE"
            result.collectingReady shouldBe true
            result.representativeSourceVerificationStatus shouldBe "VERIFIED"
        }

        @Test
        fun `APPROVED + 비활성 카테고리이면 PAUSED를 반환한다`() {
            val request = makeApprovedRequest()
            every { categoryStore.findById("cat-1") } returns Category(id = "cat-1", name = "test", isActive = false, status = CategoryStatus.PAUSED)
            every { sourceStore.listByCategoryId("cat-1") } returns emptyList()

            val result = service.getDeliveryStatus(request)

            result.deliveryState shouldBe "PAUSED"
        }

        @Test
        fun `APPROVED + 미검증 소스만 있으면 VERIFYING_SOURCE를 반환한다`() {
            val request = makeApprovedRequest()
            every { categoryStore.findById("cat-1") } returns Category(id = "cat-1", name = "test", isActive = true)
            every { sourceStore.listByCategoryId("cat-1") } returns listOf(
                RssSource(
                    id = "s-1", name = "s", url = "u", categoryId = "cat-1",
                    isActive = true, crawlApproved = false, summaryAllowed = true,
                    verificationStatus = "PENDING"
                )
            )

            val result = service.getDeliveryStatus(request)

            result.deliveryState shouldBe "VERIFYING_SOURCE"
        }

        @Test
        fun `APPROVED + 카테고리 없으면 ACTION_REQUIRED를 반환한다`() {
            val request = makeApprovedRequest().copy(approvedCategoryId = null)

            val result = service.getDeliveryStatus(request)

            result.deliveryState shouldBe "ACTION_REQUIRED"
        }

        @Test
        fun `APPROVED + categoryStore에서 카테고리를 찾지 못하면 ACTION_REQUIRED를 반환한다`() {
            val request = makeApprovedRequest()
            every { categoryStore.findById("cat-1") } returns null

            val result = service.getDeliveryStatus(request)

            result.deliveryState shouldBe "ACTION_REQUIRED"
        }

        @Test
        fun `APPROVED + 모든 소스가 FAILED 상태이면 ACTION_REQUIRED를 반환한다`() {
            val request = makeApprovedRequest()
            every { categoryStore.findById("cat-1") } returns Category(id = "cat-1", name = "test", isActive = true)
            every { sourceStore.listByCategoryId("cat-1") } returns listOf(
                RssSource(
                    id = "s-1", name = "s", url = "u", categoryId = "cat-1",
                    isActive = true, crawlApproved = true, summaryAllowed = true,
                    legalBasis = SourceLegalBasis.QUOTATION_ONLY,
                    verificationStatus = "FAILED"
                )
            )

            val result = service.getDeliveryStatus(request)

            result.deliveryState shouldBe "ACTION_REQUIRED"
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // withdrawRequest (edge cases)
    // ─────────────────────────────────────────────────────────────────────────
    // unsubscribeRequest
    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    inner class UnsubscribeRequest {

        @Test
        fun `APPROVED 상태의 본인 구독을 정상 해제한다`() {
            every { adminUserStore.findByUsername("requester") } returns userAccount
            every { requestStore.findById("req-1") } returns makeApprovedRequest()
            every { requestStore.update(any()) } answers { firstArg() }

            val result = service.unsubscribeRequest("req-1", "requester")

            result.status shouldBe UserClippingRequestStatus.WITHDRAWN
            verify(exactly = 0) { adminCategoryService.updateCategory(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
            verify(exactly = 1) { auditLogStore.log(any(), any(), "UNSUBSCRIBE", any(), any(), any()) }
        }

        @Test
        fun `PENDING 상태의 요청은 구독 해제할 수 없다`() {
            every { adminUserStore.findByUsername("requester") } returns userAccount
            every { requestStore.findById("req-1") } returns makePendingRequest()

            shouldThrow<InvalidInputException> {
                service.unsubscribeRequest("req-1", "requester")
            }.message shouldContain "활성 구독만"
        }

        @Test
        fun `다른 사용자의 구독은 해제할 수 없다`() {
            val otherUser = userAccount.copy(id = "other-user")
            every { adminUserStore.findByUsername("other") } returns otherUser
            every { requestStore.findById("req-1") } returns makeApprovedRequest()

            shouldThrow<InvalidInputException> {
                service.unsubscribeRequest("req-1", "other")
            }.message shouldContain "본인의 구독만"
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    inner class WithdrawRequest {

        @Test
        fun `PENDING 상태의 본인 요청을 정상 철회한다`() {
            every { adminUserStore.findByUsername("requester") } returns userAccount
            every { requestStore.findById("req-1") } returns makePendingRequest()
            every { requestStore.update(any()) } answers { firstArg() }

            val result = service.withdrawRequest("req-1", "requester")

            result.status shouldBe UserClippingRequestStatus.WITHDRAWN
        }

        @Test
        fun `APPROVED 상태의 요청은 철회할 수 없다`() {
            every { adminUserStore.findByUsername("requester") } returns userAccount
            every { requestStore.findById("req-1") } returns makeApprovedRequest()

            shouldThrow<InvalidInputException> {
                service.withdrawRequest("req-1", "requester")
            }.message shouldContain "검토 전 요청만"
        }

        @Test
        fun `다른 사용자의 요청은 철회할 수 없다`() {
            val otherUser = userAccount.copy(id = "other-user")
            every { adminUserStore.findByUsername("other") } returns otherUser
            every { requestStore.findById("req-1") } returns makePendingRequest()

            shouldThrow<InvalidInputException> {
                service.withdrawRequest("req-1", "other")
            }.message shouldContain "본인의 요청만"
        }

        @Test
        fun `존재하지 않는 requestId를 철회하면 NotFoundException을 발생시킨다`() {
            every { adminUserStore.findByUsername("requester") } returns userAccount
            every { requestStore.findById("nonexistent") } returns null

            shouldThrow<NotFoundException> {
                service.withdrawRequest("nonexistent", "requester")
            }.message shouldContain "Request not found"
        }

        @Test
        fun `REJECTED 상태의 요청은 철회할 수 없다`() {
            every { adminUserStore.findByUsername("requester") } returns userAccount
            val rejectedRequest = makePendingRequest().copy(status = UserClippingRequestStatus.REJECTED)
            every { requestStore.findById("req-1") } returns rejectedRequest

            shouldThrow<InvalidInputException> {
                service.withdrawRequest("req-1", "requester")
            }.message shouldContain "검토 전 요청만"
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // deleteRequest (edge cases)
    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    inner class DeleteRequest {

        @Test
        fun `REJECTED 상태의 본인 요청을 삭제한다`() {
            val rejectedRequest = makePendingRequest().copy(status = UserClippingRequestStatus.REJECTED)
            every { adminUserStore.findByUsername("requester") } returns userAccount
            every { requestStore.findById("req-1") } returns rejectedRequest
            every { requestStore.delete("req-1") } just runs

            service.deleteRequest("req-1", "requester")

            verify(exactly = 1) { requestStore.delete("req-1") }
        }

        @Test
        fun `WITHDRAWN 상태의 본인 요청을 삭제한다`() {
            val withdrawnRequest = makePendingRequest().copy(status = UserClippingRequestStatus.WITHDRAWN)
            every { adminUserStore.findByUsername("requester") } returns userAccount
            every { requestStore.findById("req-1") } returns withdrawnRequest
            every { requestStore.delete("req-1") } just runs

            service.deleteRequest("req-1", "requester")

            verify(exactly = 1) { requestStore.delete("req-1") }
        }

        @Test
        fun `PENDING 상태의 요청은 삭제할 수 없다`() {
            every { adminUserStore.findByUsername("requester") } returns userAccount
            every { requestStore.findById("req-1") } returns makePendingRequest()

            shouldThrow<InvalidInputException> {
                service.deleteRequest("req-1", "requester")
            }.message shouldContain "반려 또는 철회된 요청만"
        }

        @Test
        fun `APPROVED 상태의 요청은 삭제할 수 없다`() {
            every { adminUserStore.findByUsername("requester") } returns userAccount
            every { requestStore.findById("req-1") } returns makeApprovedRequest()

            shouldThrow<InvalidInputException> {
                service.deleteRequest("req-1", "requester")
            }.message shouldContain "반려 또는 철회된 요청만"
        }

        @Test
        fun `다른 사용자의 요청은 삭제할 수 없다`() {
            val otherUser = userAccount.copy(id = "other-user")
            val rejectedRequest = makePendingRequest().copy(status = UserClippingRequestStatus.REJECTED)
            every { adminUserStore.findByUsername("other") } returns otherUser
            every { requestStore.findById("req-1") } returns rejectedRequest

            shouldThrow<InvalidInputException> {
                service.deleteRequest("req-1", "other")
            }.message shouldContain "본인의 요청만"
        }

        @Test
        fun `존재하지 않는 requestId를 삭제하면 NotFoundException을 발생시킨다`() {
            every { adminUserStore.findByUsername("requester") } returns userAccount
            every { requestStore.findById("nonexistent") } returns null

            shouldThrow<NotFoundException> {
                service.deleteRequest("nonexistent", "requester")
            }.message shouldContain "Request not found"
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // renameRequest
    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    inner class RenameRequest {

        @Test
        fun `승인된 본인 구독의 이름을 변경한다`() {
            every { adminUserStore.findByUsername("requester") } returns userAccount
            every { requestStore.findById("req-1") } returns makeApprovedRequest()
            every { requestStore.update(any()) } answers { firstArg() }

            val result = service.renameRequest("req-1", "requester", "핀테크 뉴스")

            result.requestName shouldBe "핀테크 뉴스"
            verify(exactly = 1) { requestStore.update(match { it.requestName == "핀테크 뉴스" }) }
        }

        @Test
        fun `이름 앞뒤 공백을 자동 제거한다`() {
            every { adminUserStore.findByUsername("requester") } returns userAccount
            every { requestStore.findById("req-1") } returns makeApprovedRequest()
            every { requestStore.update(any()) } answers { firstArg() }

            val result = service.renameRequest("req-1", "requester", "  핀테크 뉴스  ")

            result.requestName shouldBe "핀테크 뉴스"
        }

        @Test
        fun `빈 이름은 거부한다`() {
            every { adminUserStore.findByUsername("requester") } returns userAccount

            shouldThrow<InvalidInputException> {
                service.renameRequest("req-1", "requester", "   ")
            }.message shouldContain "1~60자"
        }

        @Test
        fun `60자 초과 이름은 거부한다`() {
            every { adminUserStore.findByUsername("requester") } returns userAccount

            shouldThrow<InvalidInputException> {
                service.renameRequest("req-1", "requester", "가".repeat(61))
            }.message shouldContain "1~60자"
        }

        @Test
        fun `PENDING 상태의 요청은 이름을 변경할 수 없다`() {
            every { adminUserStore.findByUsername("requester") } returns userAccount
            every { requestStore.findById("req-1") } returns makePendingRequest()

            shouldThrow<InvalidInputException> {
                service.renameRequest("req-1", "requester", "새 이름")
            }.message shouldContain "승인된 구독만"
        }

        @Test
        fun `다른 사용자의 구독 이름은 변경할 수 없다`() {
            val otherUser = userAccount.copy(id = "other-user")
            every { adminUserStore.findByUsername("other") } returns otherUser
            every { requestStore.findById("req-1") } returns makeApprovedRequest()

            shouldThrow<InvalidInputException> {
                service.renameRequest("req-1", "other", "새 이름")
            }.message shouldContain "본인의 구독만"
        }

        @Test
        fun `존재하지 않는 requestId는 NotFoundException을 발생시킨다`() {
            every { adminUserStore.findByUsername("requester") } returns userAccount
            every { requestStore.findById("nonexistent") } returns null

            shouldThrow<NotFoundException> {
                service.renameRequest("nonexistent", "requester", "새 이름")
            }.message shouldContain "Request not found"
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 프리셋 경로 검증
    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    inner class `프리셋 경로 검증` {

        @Test
        fun `selectedPresetId가 있으면 personaName이 비어도 통과한다`() {
            val presetSubmission = validSubmission.copy(
                personaName = "",
                personaPrompt = "",
                selectedPresetId = "preset-ai-1"
            )
            every { adminUserStore.findByUsername("requester") } returns userAccount
            every { urlSafetyValidator.validatePublicHttpUrl(any()) } returns URI("https://techcrunch.com/feed")
            every { requestStore.save(any()) } answers { firstArg() }

            val result = service.submitRequest("requester", presetSubmission)

            result.selectedPresetId shouldBe "preset-ai-1"
            result.status shouldBe UserClippingRequestStatus.PENDING
            verify(exactly = 1) { requestStore.save(any()) }
        }

        @Test
        fun `selectedPresetId가 없으면 personaName이 필수이다`() {
            every { adminUserStore.findByUsername("requester") } returns userAccount

            shouldThrow<InvalidInputException> {
                service.submitRequest(
                    "requester",
                    validSubmission.copy(personaName = "", selectedPresetId = null)
                )
            }.message shouldContain "페르소나 이름"
        }

        @Test
        fun `selectedPresetId가 없으면 personaPrompt가 필수이다`() {
            every { adminUserStore.findByUsername("requester") } returns userAccount

            shouldThrow<InvalidInputException> {
                service.submitRequest(
                    "requester",
                    validSubmission.copy(personaPrompt = "  ", selectedPresetId = null)
                )
            }.message shouldContain "페르소나 프롬프트"
        }

        @Test
        fun `selectedPresetId가 있으면 승인 시 기존 프리셋 ID를 personaId로 사용한다`() {
            val presetRequest = makePendingRequest().copy(selectedPresetId = "preset-ai-1")
            every { adminUserStore.findByUsername("admin") } returns adminAccount
            every { requestStore.findById("req-1") } returns presetRequest
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
            // 프리셋 경로에서는 createPersona가 호출되지 않아야 한다.
            every { categoryStore.save(any()) } returns Category(id = "cat-1", name = "AI 뉴스")
            every {
                adminSourceService.createSource(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
            } returns RssSource(id = "source-1", name = "TechCrunch", url = "https://techcrunch.com/feed", categoryId = "cat-1")
            every { adminSourceService.approveSource(any(), any(), any(), any(), any(), any(), any(), any()) } returns
                RssSource(id = "s-approved", name = "approved", url = "u", categoryId = "cat-1")
            every { userSetupOwnershipService.registerOwnedResources(any(), any(), any(), any()) } just runs

            val result = service.approveRequest("req-1", "admin", approveCommand(reviewNotes = "승인"))

            result.approvedPersonaId shouldBe "preset-ai-1"
            // 프리셋 경로에서는 createPersona 호출이 없어야 한다.
            verify(exactly = 0) { adminPersonaService.createPersona(any(), any(), any(), any(), any(), any(), any()) }
        }

        @Test
        fun `승인 시 selectedPresetId가 있으면 persona 소유권 등록을 건너뛴다`() {
            val presetRequest = makePendingRequest().copy(selectedPresetId = "preset-ai-1")
            every { adminUserStore.findByUsername("admin") } returns adminAccount
            every { requestStore.findById("req-1") } returns presetRequest
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
            every { categoryStore.save(any()) } returns Category(id = "cat-1", name = "AI 뉴스")
            every {
                adminSourceService.createSource(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
            } returns RssSource(id = "source-1", name = "TechCrunch", url = "https://techcrunch.com/feed", categoryId = "cat-1")
            every { adminSourceService.approveSource(any(), any(), any(), any(), any(), any(), any(), any()) } returns
                RssSource(id = "s-approved", name = "approved", url = "u", categoryId = "cat-1")
            every { userSetupOwnershipService.registerOwnedResources(any(), any(), any(), any()) } just runs

            service.approveRequest("req-1", "admin", approveCommand())

            // 프리셋 페르소나는 공유 리소스이므로 personaId=null로 전달해 개인 소유 등록을 막는다.
            verify(exactly = 1) {
                userSetupOwnershipService.registerOwnedResources(
                    userId = "user-1",
                    categoryId = "cat-1",
                    personaId = null,
                    sourceId = "source-1"
                )
            }
        }

        @Test
        fun `승인 시 커스텀 페르소나면 persona 소유권을 등록한다`() {
            every { adminUserStore.findByUsername("admin") } returns adminAccount
            every { requestStore.findById("req-1") } returns makePendingRequest()
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
                Persona(id = "persona-custom", name = "AI 분석가", systemPrompt = "prompt")
            every { categoryStore.save(any()) } returns Category(id = "cat-1", name = "AI 뉴스")
            every {
                adminSourceService.createSource(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
            } returns RssSource(id = "source-1", name = "TechCrunch", url = "https://techcrunch.com/feed", categoryId = "cat-1")
            every { adminSourceService.approveSource(any(), any(), any(), any(), any(), any(), any(), any()) } returns
                RssSource(id = "s-approved", name = "approved", url = "u", categoryId = "cat-1")
            every { userSetupOwnershipService.registerOwnedResources(any(), any(), any(), any()) } just runs

            service.approveRequest("req-1", "admin", approveCommand())

            // 커스텀 페르소나는 개인 소유로 등록해야 한다.
            verify(exactly = 1) {
                userSetupOwnershipService.registerOwnedResources(
                    userId = "user-1",
                    categoryId = "cat-1",
                    personaId = "persona-custom",
                    sourceId = "source-1"
                )
            }
        }

        @Test
        fun `위자드 소유권 등록 시 selectedPresetId가 있으면 persona 소유권 등록을 건너뛴다`() {
            every { adminUserStore.findByUsername("requester") } returns userAccount
            // 프리셋 경로에서는 personaId=null로 registerOwnedResources가 호출되어야 한다.
            every {
                userSetupOwnershipService.registerOwnedResources(
                    userId = "user-1",
                    categoryId = "cat-preset",
                    personaId = null,
                    sourceId = "src-1"
                )
            } just runs
            every { requestStore.listByRequesterUserId("user-1") } returns emptyList()
            every { requestStore.save(any()) } answers { firstArg() }

            service.registerWizardOwnership(
                requesterUsername = "requester",
                requestName = "프리셋 구독",
                sourceName = "소스",
                sourceUrl = "https://example.com/rss",
                slackChannelId = "C0123",
                personaName = "",
                personaPrompt = "",
                summaryStyle = null,
                targetAudience = null,
                selectedPresetId = "preset-ai-1",
                categoryId = "cat-preset",
                personaId = "preset-ai-1",
                sourceId = "src-1"
            )

            // 프리셋 경로에서는 personaId를 null로 전달해 개인 소유 등록을 막는다.
            verify(exactly = 1) {
                userSetupOwnershipService.registerOwnedResources(
                    userId = "user-1",
                    categoryId = "cat-preset",
                    personaId = null,
                    sourceId = "src-1"
                )
            }
        }
    }
}
