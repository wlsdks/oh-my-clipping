package com.ohmyclipping.service

import com.ohmyclipping.service.source.SourceVerificationClient
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
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.cache.Cache
import org.springframework.cache.CacheManager

/**
 * listOwnSlackChannels의 refresh 캐시 우회 동작을 검증한다.
 *
 * refresh=true이면 slack-channels 캐시를 clear()한 뒤 Slack API를 호출한다.
 * refresh=false(기본값)이면 캐시를 건드리지 않는다.
 */
class UserSetupResourceServiceCacheBypassTest {

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
    private val slackChannelsCache = mockk<Cache>(relaxed = true)

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
    private val testUser = AdminUser(
        id = "user-1",
        username = "test@example.com",
        passwordHash = "hashed",
        role = AccountRole.USER
    )

    @BeforeEach
    fun setUp() {
        every { runtime.slackBotToken } returns "xoxb-test-token"
        every { runtimeSettingService.current() } returns runtime
        every { userSetupOwnershipService.requireUserRequester(any(), any()) } returns testUser
        every { slackMessageSender.listChannels(any(), any()) } returns emptyList()
        every { blockedSlackChannelStore.listBlockedChannelIds() } returns emptySet()
        // cacheManager.getCache("slack-channels")가 Cache 객체를 반환하도록 설정한다.
        every { cacheManager.getCache("slack-channels") } returns slackChannelsCache
    }

    @Nested
    inner class `refresh=true이면 캐시를 clear한다` {

        @Test
        fun `public_channel 요청 시 slack-channels 캐시 전체를 무효화한다`() {
            service.listOwnSlackChannels(
                requesterUsername = "test@example.com",
                channelType = "public_channel",
                refresh = true
            )

            // 캐시 조회가 1번, clear가 1번 호출되어야 한다.
            verify(exactly = 1) { cacheManager.getCache("slack-channels") }
            verify(exactly = 1) { slackChannelsCache.clear() }
        }

        @Test
        fun `private_channel 요청 시 slack-channels 캐시 전체를 무효화한다`() {
            service.listOwnSlackChannels(
                requesterUsername = "test@example.com",
                channelType = "private_channel",
                refresh = true
            )

            verify(exactly = 1) { cacheManager.getCache("slack-channels") }
            verify(exactly = 1) { slackChannelsCache.clear() }
        }

        @Test
        fun `캐시 무효화 후 Slack API 채널 목록 조회를 호출한다`() {
            service.listOwnSlackChannels(
                requesterUsername = "test@example.com",
                channelType = "public_channel",
                refresh = true
            )

            // 캐시 clear 이후 listChannels가 호출되어야 한다.
            verify(exactly = 1) { slackMessageSender.listChannels("xoxb-test-token", "public_channel") }
        }

        @Test
        fun `getCache가 null을 반환해도 예외 없이 정상 처리한다`() {
            // 캐시 설정이 없거나 초기화 전인 경우 null이 반환될 수 있다.
            every { cacheManager.getCache("slack-channels") } returns null

            service.listOwnSlackChannels(
                requesterUsername = "test@example.com",
                channelType = "public_channel",
                refresh = true
            )

            // null 캐시에 대한 clear 호출이 없어야 한다.
            verify(exactly = 0) { slackChannelsCache.clear() }
            // 정상 흐름으로 listChannels는 호출되어야 한다.
            verify(exactly = 1) { slackMessageSender.listChannels(any(), any()) }
        }
    }

    @Nested
    inner class `refresh=false이면 캐시를 건드리지 않는다` {

        @Test
        fun `refresh 파라미터 없이 호출하면 cacheManager를 조회하지 않는다`() {
            service.listOwnSlackChannels(
                requesterUsername = "test@example.com",
                channelType = "public_channel"
            )

            // refresh 기본값은 false이므로 캐시를 건드리지 않아야 한다.
            verify(exactly = 0) { cacheManager.getCache(any()) }
            verify(exactly = 0) { slackChannelsCache.clear() }
        }

        @Test
        fun `refresh=false로 명시하면 캐시를 건드리지 않는다`() {
            service.listOwnSlackChannels(
                requesterUsername = "test@example.com",
                channelType = "public_channel",
                refresh = false
            )

            verify(exactly = 0) { cacheManager.getCache(any()) }
            verify(exactly = 0) { slackChannelsCache.clear() }
        }

        @Test
        fun `refresh=false여도 Slack API 채널 목록 조회는 정상 호출된다`() {
            service.listOwnSlackChannels(
                requesterUsername = "test@example.com",
                channelType = "public_channel",
                refresh = false
            )

            verify(exactly = 1) { slackMessageSender.listChannels("xoxb-test-token", "public_channel") }
        }
    }
}
