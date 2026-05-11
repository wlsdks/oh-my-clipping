package com.clipping.mcpserver.service

import com.clipping.mcpserver.service.source.SourceVerificationClient
import com.clipping.mcpserver.service.source.VerificationResult

import com.clipping.mcpserver.service.dto.SlackChannelDto
import com.clipping.mcpserver.error.AccessForbiddenException
import com.clipping.mcpserver.error.InvalidInputException
import com.clipping.mcpserver.error.NotFoundException
import com.clipping.mcpserver.model.AccountRole
import com.clipping.mcpserver.model.AdminUser
import com.clipping.mcpserver.model.Category
import com.clipping.mcpserver.service.dto.clipping.PipelineRunResult
import com.clipping.mcpserver.model.RssSource
import com.clipping.mcpserver.security.UrlSafetyValidator
import com.clipping.mcpserver.service.dto.UserSetupSourceUrlValidationView
import com.clipping.mcpserver.store.AdminUserStore
import com.clipping.mcpserver.store.BlockedSlackChannelStore
import com.clipping.mcpserver.store.CategoryStore
import com.clipping.mcpserver.store.KnownNewsSourceStore
import com.clipping.mcpserver.store.RssSourceStore
import com.clipping.mcpserver.store.UserOwnedCategoryStore
import com.clipping.mcpserver.store.UserOwnedSourceStore
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.cache.CacheManager
import java.net.URI
import java.time.Instant

class UserSetupResourceServiceTest {

    private val categoryStore = mockk<CategoryStore>()
    private val adminSourceService = mockk<AdminSourceService>()
    private val adminClippingService = mockk<AdminClippingService>()
    private val urlSafetyValidator = mockk<UrlSafetyValidator>()
    private val sourceVerificationClient = mockk<SourceVerificationClient>()
    private val runtimeSettingService = mockk<RuntimeSettingService>()
    private val slackMessageSender = mockk<SlackMessageSender>()
    private val userOwnedCategoryStore = mockk<UserOwnedCategoryStore>()
    private val userOwnedSourceStore = mockk<UserOwnedSourceStore>()
    private val userSetupOwnershipService = mockk<UserSetupOwnershipService>()
    private val rssSourceStore = mockk<RssSourceStore>()
    private val knownNewsSourceStore = mockk<KnownNewsSourceStore>()
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

    private val testUser = AdminUser(
        id = "user-1",
        username = "test@example.com",
        passwordHash = "hashed",
        role = AccountRole.USER
    )

    @Nested
    inner class `listCuratedSources` {

        @Test
        fun `curated 소스만 필터링하여 반환한다`() {
            val curated = rssSource(id = "s1", curated = true)
            val nonCurated = rssSource(id = "s2", curated = false)
            every { rssSourceStore.list() } returns listOf(curated, nonCurated)

            val result = service.listCuratedSources()

            result.size shouldBe 1
            result[0].id shouldBe "s1"
        }

        @Test
        fun `curated 소스가 없으면 빈 목록을 반환한다`() {
            every { rssSourceStore.list() } returns listOf(
                rssSource(id = "s1", curated = false),
                rssSource(id = "s2", curated = false)
            )

            service.listCuratedSources() shouldBe emptyList()
        }

        @Test
        fun `전체 목록이 비어 있으면 빈 목록을 반환한다`() {
            every { rssSourceStore.list() } returns emptyList()

            service.listCuratedSources() shouldBe emptyList()
        }
    }

    @Nested
    inner class `createOwnCategory` {

        @BeforeEach
        fun setUp() {
            every { userSetupOwnershipService.requireUserRequester(any(), any()) } returns testUser
            every { userOwnedCategoryStore.save(any(), any()) } returns Unit
        }

        @Test
        fun `정상 입력으로 카테고리를 생성하고 owner 매핑을 저장한다`() {
            val savedCategory = category(id = "cat-new", name = "테스트 카테고리")
            val categorySlot = slot<Category>()
            every { categoryStore.save(capture(categorySlot)) } returns savedCategory

            val result = service.createOwnCategory(
                requesterUsername = "test@example.com",
                name = "테스트 카테고리",
                description = "설명",
                slackChannelId = "C0123456789",
                maxItems = 3,
                personaId = null
            )

            result.id shouldBe "cat-new"
            categorySlot.captured.name shouldBe "테스트 카테고리"
            categorySlot.captured.description shouldBe "설명"
            verify(exactly = 1) { userOwnedCategoryStore.save("user-1", "cat-new") }
        }

        @Test
        fun `이름이 빈 문자열이면 InvalidInputException이 발생한다`() {
            val exception = shouldThrow<InvalidInputException> {
                service.createOwnCategory(
                    requesterUsername = "test@example.com",
                    name = "   ",
                    description = null,
                    slackChannelId = null,
                    maxItems = 5,
                    personaId = null
                )
            }
            exception.message shouldContain "Name is required"
        }

        @Test
        fun `maxItems가 0이면 InvalidInputException이 발생한다`() {
            val exception = shouldThrow<InvalidInputException> {
                service.createOwnCategory(
                    requesterUsername = "test@example.com",
                    name = "카테고리",
                    description = null,
                    slackChannelId = null,
                    maxItems = 0,
                    personaId = null
                )
            }
            exception.message shouldContain "maxItems는 1, 3, 5 중 하나여야 합니다."
        }

        @Test
        fun `maxItems가 51이면 InvalidInputException이 발생한다`() {
            shouldThrow<InvalidInputException> {
                service.createOwnCategory(
                    requesterUsername = "test@example.com",
                    name = "카테고리",
                    description = null,
                    slackChannelId = null,
                    maxItems = 51,
                    personaId = null
                )
            }
        }

        @Test
        fun `personaId가 주어지면 소유권을 확인한다`() {
            val savedCategory = category(id = "cat-new", name = "카테고리")
            every { categoryStore.save(any()) } returns savedCategory
            every { userSetupOwnershipService.ensureOwnsPersona("user-1", "persona-1") } returns Unit

            service.createOwnCategory(
                requesterUsername = "test@example.com",
                name = "카테고리",
                description = null,
                slackChannelId = null,
                maxItems = 5,
                personaId = "persona-1"
            )

            verify(exactly = 1) { userSetupOwnershipService.ensureOwnsPersona("user-1", "persona-1") }
        }

        @Test
        fun `personaId가 빈 문자열이면 소유권 확인을 건너뛴다`() {
            val savedCategory = category(id = "cat-new", name = "카테고리")
            every { categoryStore.save(any()) } returns savedCategory

            service.createOwnCategory(
                requesterUsername = "test@example.com",
                name = "카테고리",
                description = null,
                slackChannelId = null,
                maxItems = 5,
                personaId = "  "
            )

            verify(exactly = 0) { userSetupOwnershipService.ensureOwnsPersona(any(), any()) }
        }

        @Test
        fun `description이 빈 문자열이면 null로 정규화된다`() {
            val savedCategory = category(id = "cat-new", name = "카테고리")
            val categorySlot = slot<Category>()
            every { categoryStore.save(capture(categorySlot)) } returns savedCategory

            service.createOwnCategory(
                requesterUsername = "test@example.com",
                name = "카테고리",
                description = "  ",
                slackChannelId = null,
                maxItems = 5,
                personaId = null
            )

            categorySlot.captured.description shouldBe null
        }
    }

    @Nested
    inner class `createOwnSource` {

        @BeforeEach
        fun setUp() {
            every { userSetupOwnershipService.requireUserRequester(any(), any()) } returns testUser
            every { userSetupOwnershipService.ensureOwnsCategory(any(), any()) } returns Unit
            every { userOwnedSourceStore.save(any(), any()) } returns Unit
        }

        @Test
        fun `정상 입력으로 소스를 생성하고 owner 매핑을 저장한다`() {
            val createdSource = rssSource(id = "src-new")
            every {
                adminSourceService.createSource(
                    name = any(),
                    url = any(),
                    sourceRegionRaw = any(),
                    emoji = any(),
                    categoryId = any(),
                    legalBasisRaw = any(),
                    summaryAllowed = any(),
                    fulltextAllowed = any(),
                    reviewNotes = any()
                )
            } returns createdSource

            val result = service.createOwnSource(
                requesterUsername = "test@example.com",
                name = "RSS 소스",
                url = "https://example.com/rss",
                sourceRegionRaw = "DOMESTIC",
                emoji = null,
                categoryId = "cat-1",
                legalBasisRaw = null,
                summaryAllowed = true,
                fulltextAllowed = false,
                reviewNotes = null
            )

            result.id shouldBe "src-new"
            verify(exactly = 1) { userSetupOwnershipService.ensureOwnsCategory("user-1", "cat-1") }
            verify(exactly = 1) { userOwnedSourceStore.save("user-1", "src-new") }
        }

        @Test
        fun `타인의 카테고리에 소스를 붙이면 NotFoundException이 발생한다`() {
            every {
                userSetupOwnershipService.ensureOwnsCategory("user-1", "other-cat")
            } throws NotFoundException("Category not found: other-cat")

            shouldThrow<NotFoundException> {
                service.createOwnSource(
                    requesterUsername = "test@example.com",
                    name = "소스",
                    url = "https://example.com/rss",
                    sourceRegionRaw = null,
                    emoji = null,
                    categoryId = "other-cat",
                    legalBasisRaw = null,
                    summaryAllowed = null,
                    fulltextAllowed = null,
                    reviewNotes = null
                )
            }
        }
    }

    @Nested
    inner class `verifyOwnSource` {

        @Test
        fun `본인 소유 소스 검증을 위임한다`() {
            every { userSetupOwnershipService.requireUserRequester(any(), any()) } returns testUser
            every { userSetupOwnershipService.ensureOwnsSource("user-1", "src-1") } returns Unit
            every { adminSourceService.verifySource("src-1") } returns "VERIFIED"

            val result = service.verifyOwnSource("test@example.com", "src-1")

            result shouldBe "VERIFIED"
            verify(exactly = 1) { userSetupOwnershipService.ensureOwnsSource("user-1", "src-1") }
        }

        @Test
        fun `타인 소스 검증 시 NotFoundException이 발생한다`() {
            every { userSetupOwnershipService.requireUserRequester(any(), any()) } returns testUser
            every {
                userSetupOwnershipService.ensureOwnsSource("user-1", "other-src")
            } throws NotFoundException("Source not found: other-src")

            shouldThrow<NotFoundException> {
                service.verifyOwnSource("test@example.com", "other-src")
            }
        }
    }

    @Nested
    inner class `approveOwnSource` {

        @Test
        fun `본인 소유 소스 승인을 위임한다`() {
            every { userSetupOwnershipService.requireUserRequester(any(), any()) } returns testUser
            every { userSetupOwnershipService.ensureOwnsSource("user-1", "src-1") } returns Unit
            val approvedSource = rssSource(id = "src-1", crawlApproved = true)
            every {
                adminSourceService.approveSource(
                    id = "src-1",
                    approved = true,
                    approvedBy = "test@example.com",
                    legalBasisRaw = null,
                    summaryAllowed = null,
                    fulltextAllowed = null,
                    reviewNotes = null,
                    expectedUpdatedAt = null
                )
            } returns approvedSource

            val result = service.approveOwnSource(
                requesterUsername = "test@example.com",
                sourceId = "src-1",
                approved = true,
                legalBasisRaw = null,
                summaryAllowed = null,
                fulltextAllowed = null,
                reviewNotes = null,
                expectedUpdatedAt = null
            )

            result.id shouldBe "src-1"
        }
    }

    @Nested
    inner class `runOwnPipeline` {

        @Test
        fun `본인 소유 카테고리의 파이프라인 실행을 위임한다`() {
            every { userSetupOwnershipService.requireUserRequester(any(), any()) } returns testUser
            every { userSetupOwnershipService.ensureOwnsCategory("user-1", "cat-1") } returns Unit
            val pipelineResult = mockk<PipelineRunResult>()
            every {
                adminClippingService.runPipeline(
                    categoryId = "cat-1",
                    hoursBack = 24,
                    maxItems = 5,
                    unsentOnly = true,
                    sendToSlack = true,
                    slackChannelId = null,
                    ralphLoopEnabledOverride = null,
                    ralphLoopMaxIterationsOverride = null,
                    ralphLoopStopPhraseOverride = null
                )
            } returns pipelineResult

            val result = service.runOwnPipeline(
                requesterUsername = "test@example.com",
                categoryId = "cat-1",
                hoursBack = 24,
                maxItems = 5,
                unsentOnly = true,
                sendToSlack = true,
                slackChannelId = null,
                ralphLoopEnabled = null,
                ralphLoopMaxIterations = null,
                ralphLoopStopPhrase = null
            )

            result shouldBe pipelineResult
            verify(exactly = 1) { userSetupOwnershipService.ensureOwnsCategory("user-1", "cat-1") }
        }

        @Test
        fun `타인 카테고리 파이프라인 실행 시 NotFoundException이 발생한다`() {
            every { userSetupOwnershipService.requireUserRequester(any(), any()) } returns testUser
            every {
                userSetupOwnershipService.ensureOwnsCategory("user-1", "other-cat")
            } throws NotFoundException("Category not found: other-cat")

            shouldThrow<NotFoundException> {
                service.runOwnPipeline(
                    requesterUsername = "test@example.com",
                    categoryId = "other-cat",
                    hoursBack = null,
                    maxItems = null,
                    unsentOnly = null,
                    sendToSlack = null,
                    slackChannelId = null,
                    ralphLoopEnabled = null,
                    ralphLoopMaxIterations = null,
                    ralphLoopStopPhrase = null
                )
            }
        }
    }

    @Nested
    inner class `validateOwnSourceUrl` {

        @BeforeEach
        fun setUp() {
            every { userSetupOwnershipService.requireUserRequester(any(), any()) } returns testUser
        }

        @Test
        fun `유효한 RSS URL이면 valid=true를 반환한다`() {
            val uri = URI("https://example.com/rss")
            every { urlSafetyValidator.validatePublicHttpUrl("https://example.com/rss") } returns uri
            every { sourceVerificationClient.verify(uri) } returns VerificationResult.VERIFIED

            val result = service.validateOwnSourceUrl("test@example.com", "https://example.com/rss")

            result.valid shouldBe true
            result.status shouldBe "VERIFIED"
            result.reason shouldContain "정상적으로 확인"
        }

        @Test
        fun `FEED_ERROR이면 valid=false와 안내 메시지를 반환한다`() {
            val uri = URI("https://example.com/bad")
            every { urlSafetyValidator.validatePublicHttpUrl("https://example.com/bad") } returns uri
            every { sourceVerificationClient.verify(uri) } returns VerificationResult.FEED_ERROR

            val result = service.validateOwnSourceUrl("test@example.com", "https://example.com/bad")

            result.valid shouldBe false
            result.status shouldBe "FEED_ERROR"
            result.reason shouldContain "RSS 피드를 찾지 못했어요"
        }

        @Test
        fun `ROBOTS_BLOCKED이면 차단 안내를 반환한다`() {
            val uri = URI("https://blocked.com/rss")
            every { urlSafetyValidator.validatePublicHttpUrl("https://blocked.com/rss") } returns uri
            every { sourceVerificationClient.verify(uri) } returns VerificationResult.ROBOTS_BLOCKED

            val result = service.validateOwnSourceUrl("test@example.com", "https://blocked.com/rss")

            result.valid shouldBe false
            result.reason shouldContain "자동 수집을 차단"
        }

        @Test
        fun `TIMEOUT이면 재시도 안내를 반환한다`() {
            val uri = URI("https://slow.com/rss")
            every { urlSafetyValidator.validatePublicHttpUrl("https://slow.com/rss") } returns uri
            every { sourceVerificationClient.verify(uri) } returns VerificationResult.TIMEOUT

            val result = service.validateOwnSourceUrl("test@example.com", "https://slow.com/rss")

            result.valid shouldBe false
            result.reason shouldContain "너무 오래 걸려요"
        }

        @Test
        fun `BLOCKED_URL이면 허용 불가 안내를 반환한다`() {
            val uri = URI("https://blocked.com/rss")
            every { urlSafetyValidator.validatePublicHttpUrl("https://blocked.com/rss") } returns uri
            every { sourceVerificationClient.verify(uri) } returns VerificationResult.BLOCKED_URL

            val result = service.validateOwnSourceUrl("test@example.com", "https://blocked.com/rss")

            result.valid shouldBe false
            result.reason shouldContain "허용되지 않는 URL"
        }

        @Test
        fun `잘못된 URL 형식이면 IllegalArgumentException 메시지를 반환한다`() {
            every {
                urlSafetyValidator.validatePublicHttpUrl("not-a-url")
            } throws IllegalArgumentException("Invalid URL format")

            val result = service.validateOwnSourceUrl("test@example.com", "not-a-url")

            result.valid shouldBe false
            result.reason shouldBe "Invalid URL format"
        }

        @Test
        fun `예기치 않은 예외 발생 시 일반 오류 메시지를 반환한다`() {
            every {
                urlSafetyValidator.validatePublicHttpUrl("https://error.com")
            } throws RuntimeException("unexpected")

            val result = service.validateOwnSourceUrl("test@example.com", "https://error.com")

            result.valid shouldBe false
            result.reason shouldContain "오류가 발생했어요"
        }
    }

    @Nested
    inner class `verifyOwnSlackChannel` {

        @BeforeEach
        fun setUp() {
            every { userSetupOwnershipService.requireUserRequester(any(), any()) } returns testUser
        }

        @Test
        fun `정상 채널 ID로 연결 확인을 수행한다`() {
            val runtime = mockk<RuntimeSettingService.RuntimeSettings>()
            every { runtime.slackBotToken } returns "xoxb-test-token"
            every { runtimeSettingService.current() } returns runtime
            val expectedResult = SlackMessageSender.SlackConnectionTestResult(
                ok = true,
                botUser = "bot",
                team = "team",
                channelId = "C0123456789",
                channelName = "general",
                neededScopes = null,
                providedScopes = null,
                rawError = null
            )
            every { slackMessageSender.testConnection("xoxb-test-token", "C0123456789") } returns expectedResult

            val result = service.verifyOwnSlackChannel("test@example.com", "C0123456789")

            result.ok shouldBe true
            result.channelId shouldBe "C0123456789"
        }

        @Test
        fun `DM 채널 ID(D로 시작)는 정규화 없이 전달한다`() {
            val runtime = mockk<RuntimeSettingService.RuntimeSettings>()
            every { runtime.slackBotToken } returns "xoxb-test-token"
            every { runtimeSettingService.current() } returns runtime
            val expectedResult = SlackMessageSender.SlackConnectionTestResult(
                ok = true, botUser = "bot", team = "team",
                channelId = "D0123456789", channelName = null,
                neededScopes = null, providedScopes = null, rawError = null
            )
            every { slackMessageSender.testConnection("xoxb-test-token", "D0123456789") } returns expectedResult

            val result = service.verifyOwnSlackChannel("test@example.com", "D0123456789")

            result.ok shouldBe true
        }

        @Test
        fun `빈 채널 ID는 null로 정규화한다`() {
            val runtime = mockk<RuntimeSettingService.RuntimeSettings>()
            every { runtime.slackBotToken } returns "xoxb-test-token"
            every { runtimeSettingService.current() } returns runtime
            val expectedResult = SlackMessageSender.SlackConnectionTestResult(
                ok = true, botUser = "bot", team = "team",
                channelId = null, channelName = null,
                neededScopes = null, providedScopes = null, rawError = null
            )
            every { slackMessageSender.testConnection("xoxb-test-token", null) } returns expectedResult

            val result = service.verifyOwnSlackChannel("test@example.com", "  ")

            result.ok shouldBe true
        }

        @Test
        fun `올바르지 않은 채널 형식이면 InvalidInputException이 발생한다`() {
            val runtime = mockk<RuntimeSettingService.RuntimeSettings>()
            every { runtime.slackBotToken } returns "xoxb-test-token"
            every { runtimeSettingService.current() } returns runtime

            shouldThrow<InvalidInputException> {
                service.verifyOwnSlackChannel("test@example.com", "invalid-channel")
            }
        }

        @Test
        fun `빈 봇 토큰이면 빈 문자열을 null로 변환하여 전달한다`() {
            val runtime = mockk<RuntimeSettingService.RuntimeSettings>()
            every { runtime.slackBotToken } returns ""
            every { runtimeSettingService.current() } returns runtime
            val expectedResult = SlackMessageSender.SlackConnectionTestResult(
                ok = false, botUser = null, team = null,
                channelId = null, channelName = null,
                neededScopes = null, providedScopes = null, rawError = "not_authed"
            )
            every { slackMessageSender.testConnection(null, "C0123456789") } returns expectedResult

            val result = service.verifyOwnSlackChannel("test@example.com", "C0123456789")

            result.ok shouldBe false
        }
    }

    @Nested
    inner class `listOwnSlackChannels` {

        @BeforeEach
        fun setUp() {
            every { userSetupOwnershipService.requireUserRequester(any(), any()) } returns testUser
        }

        @Test
        fun `public_channel 타입으로 채널 목록을 반환한다`() {
            val runtime = mockk<RuntimeSettingService.RuntimeSettings>()
            every { runtime.slackBotToken } returns "xoxb-token"
            every { runtimeSettingService.current() } returns runtime
            val channels = listOf(
                SlackMessageSender.SlackChannel(id = "C001", name = "general", isPrivate = false)
            )
            every { slackMessageSender.listChannels("xoxb-token", "public_channel") } returns channels

            val result = service.listOwnSlackChannels("test@example.com", "public_channel")

            result.channels shouldBe listOf(SlackChannelDto(id = "C001", name = "general", isPrivate = false))
            result.slackConnectRequired shouldBe false
        }

        @Test
        fun `허용되지 않는 channelType이면 InvalidInputException이 발생한다`() {
            val runtime = mockk<RuntimeSettingService.RuntimeSettings>()
            every { runtime.slackBotToken } returns "xoxb-token"
            every { runtimeSettingService.current() } returns runtime

            shouldThrow<InvalidInputException> {
                service.listOwnSlackChannels("test@example.com", "invalid_type")
            }
        }

        @Test
        fun `빈 봇 토큰이면 null로 변환해 전달한다`() {
            val runtime = mockk<RuntimeSettingService.RuntimeSettings>()
            every { runtime.slackBotToken } returns ""
            every { runtimeSettingService.current() } returns runtime
            every { slackMessageSender.listChannels(null, "public_channel") } returns emptyList()

            val result = service.listOwnSlackChannels("test@example.com", "public_channel")

            result.channels shouldBe emptyList()
            verify(exactly = 1) { slackMessageSender.listChannels(null, "public_channel") }
        }
    }

    @Nested
    inner class `getOwnSlackChannelInfo` {

        @BeforeEach
        fun setUp() {
            every { userSetupOwnershipService.requireUserRequester(any(), any()) } returns testUser
        }

        @Test
        fun `채널 ID로 채널 정보를 반환한다`() {
            val runtime = mockk<RuntimeSettingService.RuntimeSettings>()
            every { runtime.slackBotToken } returns "xoxb-token"
            every { runtimeSettingService.current() } returns runtime
            val expected = SlackMessageSender.SlackChannel(id = "C001", name = "general", isPrivate = false)
            every { slackMessageSender.getChannelInfo("xoxb-token", "C001") } returns expected

            val result = service.getOwnSlackChannelInfo("test@example.com", "C001")

            result shouldBe expected
        }

        @Test
        fun `channel_not_found이면 NotFoundException이 전파된다`() {
            val runtime = mockk<RuntimeSettingService.RuntimeSettings>()
            every { runtime.slackBotToken } returns "xoxb-token"
            every { runtimeSettingService.current() } returns runtime
            every { slackMessageSender.getChannelInfo("xoxb-token", "C999") } throws NotFoundException("채널을 찾을 수 없습니다: C999")

            shouldThrow<NotFoundException> {
                service.getOwnSlackChannelInfo("test@example.com", "C999")
            }
        }

        @Test
        fun `not_in_channel이면 AccessForbiddenException이 전파된다`() {
            val runtime = mockk<RuntimeSettingService.RuntimeSettings>()
            every { runtime.slackBotToken } returns "xoxb-token"
            every { runtimeSettingService.current() } returns runtime
            every { slackMessageSender.getChannelInfo("xoxb-token", "C001") } throws AccessForbiddenException()

            shouldThrow<AccessForbiddenException> {
                service.getOwnSlackChannelInfo("test@example.com", "C001")
            }
        }
    }

    // ── 테스트 헬퍼 ──

    private fun rssSource(
        id: String = "src-1",
        curated: Boolean = false,
        crawlApproved: Boolean = false
    ) = RssSource(
        id = id,
        name = "테스트 소스",
        url = "https://example.com/rss",
        categoryId = "cat-1",
        curated = curated,
        crawlApproved = crawlApproved,
        createdAt = Instant.parse("2026-03-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-03-01T00:00:00Z")
    )

    private fun category(id: String = "cat-1", name: String = "카테고리") = Category(
        id = id,
        name = name,
        createdAt = Instant.parse("2026-03-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-03-01T00:00:00Z")
    )
}
