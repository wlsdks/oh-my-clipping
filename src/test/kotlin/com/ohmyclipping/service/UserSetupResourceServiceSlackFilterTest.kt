package com.ohmyclipping.service

import com.ohmyclipping.service.source.SourceVerificationClient
import com.ohmyclipping.service.dto.admin.SlackChannelDto
import com.ohmyclipping.model.AccountRole
import com.ohmyclipping.model.AdminUser
import com.ohmyclipping.security.UrlSafetyValidator
import com.ohmyclipping.store.AdminUserStore
import com.ohmyclipping.store.BlockedSlackChannelStore
import com.ohmyclipping.store.CategoryStore
import com.ohmyclipping.store.KnownNewsSourceStore
import com.ohmyclipping.store.RssSourceStore
import com.ohmyclipping.store.UserOwnedCategoryStore
import com.ohmyclipping.store.UserOwnedSourceStore
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.cache.CacheManager

/**
 * listOwnSlackChannels의 비공개 채널 멤버십 필터링 로직을 검증한다.
 */
class UserSetupResourceServiceSlackFilterTest {

    private val categoryStore = mockk<CategoryStore>(relaxed = true)
    private val adminSourceService = mockk<AdminSourceService>(relaxed = true)
    private val adminClippingService = mockk<AdminClippingService>(relaxed = true)
    private val urlSafetyValidator = mockk<UrlSafetyValidator>(relaxed = true)
    private val sourceVerificationClient = mockk<SourceVerificationClient>(relaxed = true)
    private val runtimeSettingService = mockk<RuntimeSettingService>()
    private val slackMessageSender = mockk<SlackMessageSender>()
    private val userOwnedCategoryStore = mockk<UserOwnedCategoryStore>(relaxed = true)
    private val userOwnedSourceStore = mockk<UserOwnedSourceStore>(relaxed = true)
    private val userSetupOwnershipService = mockk<UserSetupOwnershipService>()
    private val rssSourceStore = mockk<RssSourceStore>(relaxed = true)
    private val knownNewsSourceStore = mockk<KnownNewsSourceStore>(relaxed = true)
    private val adminUserStore = mockk<AdminUserStore>(relaxed = true)
    private val blockedSlackChannelStore = mockk<BlockedSlackChannelStore>(relaxed = true)
    private val cacheManager = mockk<CacheManager>(relaxed = true)

    private val service = UserSetupResourceService(
        categoryStore = categoryStore,
        adminSourceService = adminSourceService,
        adminClippingService = adminClippingService,
        urlSafetyValidator = urlSafetyValidator,
        sourceVerificationClient = sourceVerificationClient,
        runtimeSettingService = runtimeSettingService,
        slackMessageSender = slackMessageSender,
        userOwnedCategoryStore = userOwnedCategoryStore,
        userOwnedSourceStore = userOwnedSourceStore,
        userSetupOwnershipService = userSetupOwnershipService,
        rssSourceStore = rssSourceStore,
        knownNewsSourceStore = knownNewsSourceStore,
        adminUserStore = adminUserStore,
        blockedSlackChannelStore = blockedSlackChannelStore,
        cacheManager = cacheManager
    )

    private val runtime = mockk<RuntimeSettingService.RuntimeSettings>()

    @BeforeEach
    fun setUp() {
        every { runtime.slackBotToken } returns "xoxb-test-token"
        every { runtimeSettingService.current() } returns runtime
    }

    @Nested
    inner class `비공개 채널 멤버십 필터링` {

        @Test
        fun `Slack 멤버 ID가 미설정이면 빈 목록과 slackConnectRequired=true를 반환한다`() {
            // Slack 멤버 ID가 없는 사용자
            val user = testUser(slackMemberId = null)
            every { userSetupOwnershipService.requireUserRequester(any(), any()) } returns user
            val channels = listOf(
                SlackMessageSender.SlackChannel(id = "C001", name = "secret", isPrivate = true),
                SlackMessageSender.SlackChannel(id = "C002", name = "team", isPrivate = true)
            )
            every { slackMessageSender.listChannels("xoxb-test-token", "private_channel") } returns channels

            val result = service.listOwnSlackChannels("test@example.com", "private_channel")

            result.channels shouldBe emptyList()
            result.slackConnectRequired shouldBe true
            result.totalBeforeFilter shouldBe 2
            // 멤버십 확인 호출이 없어야 한다.
            verify(exactly = 0) { slackMessageSender.getChannelMembers(any(), any()) }
        }

        @Test
        fun `Slack 멤버 ID가 빈 문자열이면 미연동으로 처리한다`() {
            val user = testUser(slackMemberId = "  ")
            every { userSetupOwnershipService.requireUserRequester(any(), any()) } returns user
            every { slackMessageSender.listChannels("xoxb-test-token", "private_channel") } returns listOf(
                SlackMessageSender.SlackChannel(id = "C001", name = "secret", isPrivate = true)
            )

            val result = service.listOwnSlackChannels("test@example.com", "private_channel")

            result.channels shouldBe emptyList()
            result.slackConnectRequired shouldBe true
            result.totalBeforeFilter shouldBe 1
        }

        @Test
        fun `Slack 멤버 ID가 연동되어 있으면 멤버인 채널만 반환한다`() {
            val user = testUser(slackMemberId = "U12345")
            every { userSetupOwnershipService.requireUserRequester(any(), any()) } returns user
            val channels = listOf(
                SlackMessageSender.SlackChannel(id = "C001", name = "member-channel", isPrivate = true),
                SlackMessageSender.SlackChannel(id = "C002", name = "non-member-channel", isPrivate = true),
                SlackMessageSender.SlackChannel(id = "C003", name = "another-member", isPrivate = true)
            )
            every { slackMessageSender.listChannels("xoxb-test-token", "private_channel") } returns channels
            // C001과 C003에만 사용자가 멤버로 존재한다.
            every { slackMessageSender.getChannelMembers("xoxb-test-token", "C001") } returns setOf("U12345", "U99999")
            every { slackMessageSender.getChannelMembers("xoxb-test-token", "C002") } returns setOf("U99999")
            every { slackMessageSender.getChannelMembers("xoxb-test-token", "C003") } returns setOf("U12345")

            val result = service.listOwnSlackChannels("test@example.com", "private_channel")

            result.channels shouldBe listOf(
                SlackChannelDto(id = "C001", name = "member-channel", isPrivate = true),
                SlackChannelDto(id = "C003", name = "another-member", isPrivate = true)
            )
            result.slackConnectRequired shouldBe false
            result.totalBeforeFilter shouldBe 3
        }

        @Test
        fun `멤버인 채널이 하나도 없으면 빈 목록을 반환하되 slackConnectRequired는 false다`() {
            val user = testUser(slackMemberId = "U12345")
            every { userSetupOwnershipService.requireUserRequester(any(), any()) } returns user
            val channels = listOf(
                SlackMessageSender.SlackChannel(id = "C001", name = "no-member", isPrivate = true)
            )
            every { slackMessageSender.listChannels("xoxb-test-token", "private_channel") } returns channels
            every { slackMessageSender.getChannelMembers("xoxb-test-token", "C001") } returns setOf("U99999")

            val result = service.listOwnSlackChannels("test@example.com", "private_channel")

            result.channels shouldBe emptyList()
            result.slackConnectRequired shouldBe false
            result.totalBeforeFilter shouldBe 1
        }
    }

    @Nested
    inner class `공개 채널은 멤버십 필터링을 수행하지 않는다` {

        @Test
        fun `public_channel이면 전체 채널 목록을 그대로 반환한다`() {
            val user = testUser(slackMemberId = null) // Slack 미연동이어도 공개 채널은 전부 반환
            every { userSetupOwnershipService.requireUserRequester(any(), any()) } returns user
            val channels = listOf(
                SlackMessageSender.SlackChannel(id = "C001", name = "general", isPrivate = false),
                SlackMessageSender.SlackChannel(id = "C002", name = "random", isPrivate = false)
            )
            every { slackMessageSender.listChannels("xoxb-test-token", "public_channel") } returns channels

            val result = service.listOwnSlackChannels("test@example.com", "public_channel")

            result.channels shouldBe listOf(
                SlackChannelDto(id = "C001", name = "general", isPrivate = false),
                SlackChannelDto(id = "C002", name = "random", isPrivate = false)
            )
            result.slackConnectRequired shouldBe false
            result.totalBeforeFilter shouldBe null
            // 멤버십 확인 호출이 없어야 한다.
            verify(exactly = 0) { slackMessageSender.getChannelMembers(any(), any()) }
        }
    }

    @Nested
    inner class `공개 채널 차단 필터링` {
        @Test
        fun `공개 채널은 차단 목록이 적용된다`() {
            val user = testUser(slackMemberId = null)
            every { userSetupOwnershipService.requireUserRequester(any(), any()) } returns user
            val channels = listOf(
                SlackMessageSender.SlackChannel("C1", "general", false),
                SlackMessageSender.SlackChannel("C2", "dev", false)
            )
            every { slackMessageSender.listChannels("xoxb-test-token", "public_channel") } returns channels
            // C1은 차단된 채널이다.
            every { blockedSlackChannelStore.listBlockedChannelIds() } returns setOf("C1")

            val result = service.listOwnSlackChannels("test@example.com", "public_channel")

            result.channels shouldBe listOf(
                SlackChannelDto(id = "C2", name = "dev", isPrivate = false)
            )
        }

        @Test
        fun `차단 목록이 비어 있으면 전체 채널을 반환한다`() {
            val user = testUser(slackMemberId = null)
            every { userSetupOwnershipService.requireUserRequester(any(), any()) } returns user
            val channels = listOf(
                SlackMessageSender.SlackChannel("C1", "general", false),
                SlackMessageSender.SlackChannel("C2", "dev", false)
            )
            every { slackMessageSender.listChannels("xoxb-test-token", "public_channel") } returns channels
            every { blockedSlackChannelStore.listBlockedChannelIds() } returns emptySet()

            val result = service.listOwnSlackChannels("test@example.com", "public_channel")

            result.channels shouldBe listOf(
                SlackChannelDto(id = "C1", name = "general", isPrivate = false),
                SlackChannelDto(id = "C2", name = "dev", isPrivate = false)
            )
        }

        @Test
        fun `모든 공개 채널이 차단되면 빈 목록을 반환한다`() {
            val user = testUser(slackMemberId = null)
            every { userSetupOwnershipService.requireUserRequester(any(), any()) } returns user
            val channels = listOf(
                SlackMessageSender.SlackChannel("C1", "general", false)
            )
            every { slackMessageSender.listChannels("xoxb-test-token", "public_channel") } returns channels
            every { blockedSlackChannelStore.listBlockedChannelIds() } returns setOf("C1")

            val result = service.listOwnSlackChannels("test@example.com", "public_channel")

            result.channels shouldBe emptyList()
        }
    }

    // ── 테스트 헬퍼 ──

    private fun testUser(slackMemberId: String? = null, slackDmChannelId: String? = null) = AdminUser(
        id = "user-1",
        username = "test@example.com",
        passwordHash = "hashed",
        role = AccountRole.USER,
        slackMemberId = slackMemberId,
        slackDmChannelId = slackDmChannelId
    )
}
