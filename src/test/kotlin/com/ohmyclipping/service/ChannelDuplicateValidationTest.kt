package com.ohmyclipping.service

import com.ohmyclipping.service.notification.OperationsNotificationService
import com.ohmyclipping.error.ConflictException
import com.ohmyclipping.model.*
import com.ohmyclipping.service.dto.clipping.*
import com.ohmyclipping.security.UrlSafetyValidator
import com.ohmyclipping.service.dto.user.UserClippingRequestSubmission
import com.ohmyclipping.service.source.CategorySourceBuilder
import com.ohmyclipping.store.AdminUserStore
import com.ohmyclipping.store.AuditLogStore
import com.ohmyclipping.store.CategoryRuleStore
import com.ohmyclipping.store.CategoryStore
import com.ohmyclipping.store.CompetitorWatchlistStore
import com.ohmyclipping.store.RssSourceStore
import com.ohmyclipping.store.UserClippingRequestStore
import com.ohmyclipping.store.UserDeliveryScheduleStore
import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.string.shouldContain
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import java.net.URI

/**
 * 공개 채널 중복 구독 방지 검증 테스트.
 * validateChannelNotOccupied 로직이 submitRequest 흐름에 올바르게 통합되는지 확인한다.
 */
class ChannelDuplicateValidationTest {

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
        slackDmChannelId = "D_USER_CHANNEL"
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
        every { adminUserStore.findByUsername("requester") } returns userAccount
        every { adminUserStore.findById("user-1") } returns userAccount
        every { requestStore.listByRequesterUserId("user-1") } returns emptyList()
        every { requestStore.countActiveSubscriptionsByRequesterUserId("user-1") } returns 0
        every { requestStore.countCreatedSinceByRequesterUserId("user-1", any()) } returns 0
        every { categoryStore.list() } returns emptyList()
        every { sourceStore.findByUrlAndCategoryId(any(), any()) } returns null
        every { userDeliveryScheduleStore.findByUserId(any()) } returns null
        every { urlSafetyValidator.validatePublicHttpUrl(any()) } returns URI("https://techcrunch.com/feed")
        every { requestStore.save(any()) } answers { firstArg() }
    }

    private fun submission(channelId: String) = UserClippingRequestSubmission(
        requestName = "AI 뉴스",
        sourceName = "TechCrunch",
        sourceUrl = "https://techcrunch.com/feed",
        slackChannelId = channelId,
        personaName = "AI 분석가",
        personaPrompt = "최신 AI 뉴스를 요약합니다",
        summaryStyle = null,
        targetAudience = null,
        requestNote = null
    )

    // ─────────────────────────────────────────────────────────────────────────
    // 공개 채널 중복 검증
    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    inner class PublicChannelOccupancyCheck {

        @Test
        fun `APPROVED 상태 구독이 있는 채널로 요청 시 ConflictException을 발생시킨다`() {
            val channelId = "C_PUBLIC_01"
            every {
                requestStore.existsBySlackChannelIdAndStatusIn(
                    slackChannelId = channelId,
                    statuses = listOf(UserClippingRequestStatus.PENDING, UserClippingRequestStatus.APPROVED)
                )
            } returns true

            shouldThrow<ConflictException> {
                service.submitRequest("requester", submission(channelId))
            }.message shouldContain "이미 다른 구독이 연결된 채널입니다"
        }

        @Test
        fun `PENDING 상태 구독이 있는 채널로 요청 시 ConflictException을 발생시킨다`() {
            val channelId = "C_PUBLIC_02"
            every {
                requestStore.existsBySlackChannelIdAndStatusIn(
                    slackChannelId = channelId,
                    statuses = listOf(UserClippingRequestStatus.PENDING, UserClippingRequestStatus.APPROVED)
                )
            } returns true

            shouldThrow<ConflictException> {
                service.submitRequest("requester", submission(channelId))
            }.message shouldContain "이미 다른 구독이 연결된 채널입니다"
        }

        @Test
        fun `WITHDRAWN 상태 구독만 있는 채널로 요청 시 정상 등록에 성공한다`() {
            val channelId = "C_PUBLIC_03"
            // WITHDRAWN만 있으면 existsBySlackChannelIdAndStatusIn은 false를 반환한다
            every {
                requestStore.existsBySlackChannelIdAndStatusIn(
                    slackChannelId = channelId,
                    statuses = listOf(UserClippingRequestStatus.PENDING, UserClippingRequestStatus.APPROVED)
                )
            } returns false

            shouldNotThrow<ConflictException> {
                service.submitRequest("requester", submission(channelId))
            }

            verify(exactly = 1) { requestStore.save(any()) }
        }

        @Test
        fun `해당 채널에 구독이 없으면 정상 등록에 성공한다`() {
            val channelId = "C_PUBLIC_EMPTY"
            every {
                requestStore.existsBySlackChannelIdAndStatusIn(
                    slackChannelId = channelId,
                    statuses = listOf(UserClippingRequestStatus.PENDING, UserClippingRequestStatus.APPROVED)
                )
            } returns false

            shouldNotThrow<ConflictException> {
                service.submitRequest("requester", submission(channelId))
            }

            verify(exactly = 1) { requestStore.save(any()) }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DM 채널 예외 처리
    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    inner class DmChannelSkipsOccupancyCheck {

        @Test
        fun `D 접두어 DM 채널은 중복 검사를 건너뛰고 정상 등록에 성공한다`() {
            val dmChannelId = "D_USER_CHANNEL"
            // DM 채널은 existsBySlackChannelIdAndStatusIn 호출 자체를 하지 않는다
            every {
                requestStore.existsBySlackChannelIdAndStatusIn(any(), any())
            } returns true // 혹시라도 호출되면 fail 유도

            shouldNotThrow<ConflictException> {
                service.submitRequest("requester", submission(dmChannelId))
            }

            // DM이므로 occupancy 체크가 호출되지 않아야 한다
            verify(exactly = 0) { requestStore.existsBySlackChannelIdAndStatusIn(any(), any()) }
        }

        @Test
        fun `U 접두어 사용자 ID DM 채널은 중복 검사를 건너뛰고 정상 등록에 성공한다`() {
            val userChannelId = "U_MEMBER_ID"
            every {
                requestStore.existsBySlackChannelIdAndStatusIn(any(), any())
            } returns true // 혹시라도 호출되면 fail 유도

            shouldNotThrow<ConflictException> {
                service.submitRequest("requester", submission(userChannelId))
            }

            verify(exactly = 0) { requestStore.existsBySlackChannelIdAndStatusIn(any(), any()) }
        }
    }
}
