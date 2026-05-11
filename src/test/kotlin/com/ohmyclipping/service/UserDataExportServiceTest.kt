package com.ohmyclipping.service

import com.ohmyclipping.config.RedisRateLimitService
import com.ohmyclipping.error.NotFoundException
import com.ohmyclipping.error.RateLimitExceededException
import com.ohmyclipping.model.AccountApprovalStatus
import com.ohmyclipping.model.AccountRole
import com.ohmyclipping.model.AdminUser
import com.ohmyclipping.model.BookmarkedArticle
import com.ohmyclipping.model.DeliveryLog
import com.ohmyclipping.model.DeliveryPreset
import com.ohmyclipping.model.UserClippingRequest
import com.ohmyclipping.model.UserClippingRequestStatus
import com.ohmyclipping.model.UserDeliverySchedule
import com.ohmyclipping.model.UserEvent
import com.ohmyclipping.service.dto.PersonalDataExportLimits
import com.ohmyclipping.store.AdminUserStore
import com.ohmyclipping.store.AuditLogStore
import com.ohmyclipping.store.BookmarkedArticleStore
import com.ohmyclipping.store.DeliveryLogStore
import com.ohmyclipping.store.DepartmentStore
import com.ohmyclipping.store.SummaryFeedbackStore
import com.ohmyclipping.store.TeamStore
import com.ohmyclipping.store.UserClippingRequestStore
import com.ohmyclipping.store.UserDeliveryScheduleStore
import com.ohmyclipping.store.UserEventStore
import com.ohmyclipping.store.UserOwnedCategoryStore
import com.ohmyclipping.store.UserOwnedPersonaStore
import com.ohmyclipping.store.UserOwnedSourceStore
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

/**
 * UserDataExportService 의 민감 필드 누수 방어, 타 사용자 격리, rate limit, audit log 을 검증한다.
 */
class UserDataExportServiceTest {

    private val adminUserStore = mockk<AdminUserStore>()
    private val userClippingRequestStore = mockk<UserClippingRequestStore>()
    private val userOwnedCategoryStore = mockk<UserOwnedCategoryStore>()
    private val userOwnedPersonaStore = mockk<UserOwnedPersonaStore>()
    private val userOwnedSourceStore = mockk<UserOwnedSourceStore>()
    private val userDeliveryScheduleStore = mockk<UserDeliveryScheduleStore>()
    private val userEventStore = mockk<UserEventStore>()
    private val bookmarkedArticleStore = mockk<BookmarkedArticleStore>()
    private val deliveryLogStore = mockk<DeliveryLogStore>()
    private val auditLogStore = mockk<AuditLogStore>(relaxed = true)
    private val rateLimitService = mockk<RedisRateLimitService>()
    private val departmentStore = mockk<DepartmentStore>(relaxed = true)
    private val teamStore = mockk<TeamStore>(relaxed = true)
    private val summaryFeedbackStore = mockk<SummaryFeedbackStore>(relaxed = true)

    private lateinit var service: UserDataExportService

    private val targetUser = AdminUser(
        id = "user-1",
        username = "alice",
        passwordHash = "super-secret-bcrypt-hash-SHOULD-NOT-LEAK",
        role = AccountRole.USER,
        displayName = "앨리스",
        department = "마케팅",
        approvalStatus = AccountApprovalStatus.APPROVED,
        slackMemberId = "U01AAAA",
        slackDmChannelId = "D01AAAA",
        lastLoginAt = Instant.parse("2026-04-01T09:00:00Z"),
        createdAt = Instant.parse("2025-10-01T00:00:00Z")
    )

    private val otherUser = AdminUser(
        id = "user-2",
        username = "bob",
        passwordHash = "other-secret",
        role = AccountRole.USER
    )

    @BeforeEach
    fun setUp() {
        clearAllMocks()
        service = UserDataExportService(
            adminUserStore = adminUserStore,
            userClippingRequestStore = userClippingRequestStore,
            userOwnedCategoryStore = userOwnedCategoryStore,
            userOwnedPersonaStore = userOwnedPersonaStore,
            userOwnedSourceStore = userOwnedSourceStore,
            userDeliveryScheduleStore = userDeliveryScheduleStore,
            userEventStore = userEventStore,
            bookmarkedArticleStore = bookmarkedArticleStore,
            deliveryLogStore = deliveryLogStore,
            auditLogStore = auditLogStore,
            rateLimitService = rateLimitService,
            departmentStore = departmentStore,
            teamStore = teamStore,
            summaryFeedbackStore = summaryFeedbackStore
        )
    }

    private fun stubEmptyStoresFor(user: AdminUser) {
        every { adminUserStore.findByUsername(user.username) } returns user
        every { userDeliveryScheduleStore.findByUserId(user.id) } returns null
        every { userOwnedCategoryStore.listCategoryIds(user.id) } returns emptyList()
        every { userOwnedPersonaStore.listPersonaIds(user.id) } returns emptyList()
        every { userOwnedSourceStore.listSourceIds(user.id) } returns emptyList()
        every { userClippingRequestStore.listByRequesterUserId(user.id) } returns emptyList()
        every { bookmarkedArticleStore.listAllForUser(user.id) } returns emptyList()
        every { userEventStore.findByUserAndDateRange(user.id, any(), any(), any()) } returns emptyList()
        every { summaryFeedbackStore.findByUserId(user.id, any()) } returns emptyList()
    }

    @Nested
    inner class `개인정보 수집 정확성` {

        @Test
        fun `존재하지 않는 사용자이면 NotFoundException 을 던진다`() {
            every { adminUserStore.findByUsername("ghost") } returns null
            shouldThrow<NotFoundException> { service.gatherPersonalData("ghost") }
        }

        @Test
        fun `8개 섹션이 모두 채워져 반환된다`() {
            stubEmptyStoresFor(targetUser)
            val schedule = UserDeliverySchedule(
                userId = targetUser.id,
                deliveryDays = listOf("MON", "WED", "FRI"),
                deliveryHour = 9,
                preset = DeliveryPreset.CUSTOM,
                updatedAt = Instant.parse("2026-04-10T00:00:00Z")
            )
            every { userDeliveryScheduleStore.findByUserId(targetUser.id) } returns schedule
            every { userOwnedCategoryStore.listCategoryIds(targetUser.id) } returns listOf("cat-a")
            every { userOwnedPersonaStore.listPersonaIds(targetUser.id) } returns listOf("persona-a")
            every { userOwnedSourceStore.listSourceIds(targetUser.id) } returns listOf("src-a")

            val request = UserClippingRequest(
                id = "req-1",
                requesterUserId = targetUser.id,
                requestName = "기획 뉴스",
                sourceName = "Source",
                sourceUrl = "https://example.com/feed",
                slackChannelId = "C-USER-1",
                personaName = "기획자",
                personaPrompt = "요약",
                status = UserClippingRequestStatus.APPROVED,
                requestNote = "본문 메모",
                approvedCategoryId = "cat-a",
                createdAt = Instant.parse("2026-03-01T00:00:00Z")
            )
            every { userClippingRequestStore.listByRequesterUserId(targetUser.id) } returns listOf(request)

            val bookmark = BookmarkedArticle(
                id = "bm-1",
                userId = targetUser.id,
                summaryId = "sum-1",
                originalTitle = "title",
                translatedTitle = null,
                summary = "summary",
                insights = null,
                keywords = emptyList(),
                importanceScore = 0.5f,
                sourceLink = "https://example.com/a",
                categoryId = "cat-a",
                sentiment = null,
                eventType = null,
                articleCreatedAt = Instant.parse("2026-04-01T00:00:00Z"),
                bookmarkedAt = Instant.parse("2026-04-02T00:00:00Z")
            )
            every { bookmarkedArticleStore.listAllForUser(targetUser.id) } returns listOf(bookmark)

            val event = UserEvent(
                id = 1L,
                userId = targetUser.id,
                eventType = "article_view",
                eventData = """{"summaryId":"sum-1"}""",
                pagePath = "/user/articles",
                createdAt = Instant.parse("2026-04-03T00:00:00Z")
            )
            every { userEventStore.findByUserAndDateRange(targetUser.id, any(), any(), any()) } returns listOf(event)

            val deliveryLog = DeliveryLog(
                id = "dl-1",
                categoryId = "cat-a",
                channelId = "C-USER-1",
                deliveryDate = LocalDate.of(2026, 4, 1),
                deliveryHour = 9,
                status = "SENT",
                itemCount = 5
            )
            every { deliveryLogStore.findByChannelIds(setOf("C-USER-1")) } returns listOf(deliveryLog)

            val result = service.gatherPersonalData(targetUser.username)

            result.userId shouldBe targetUser.id
            result.username shouldBe targetUser.username
            result.account.displayName shouldBe "앨리스"
            result.preferences.deliverySchedule?.deliveryDays shouldBe listOf("MON", "WED", "FRI")
            result.preferences.ownedCategoryIds shouldBe listOf("cat-a")
            result.subscriptions shouldHaveSize 1
            result.subscriptions[0].requestName shouldBe "기획 뉴스"
            result.bookmarks shouldHaveSize 1
            result.recentEvents shouldHaveSize 1
            result.deliveryLogs shouldHaveSize 1
            result.deliveryLogs[0].channelId shouldBe "C-USER-1"
        }

        @Test
        fun `구독이 없으면 발송 이력 채널 집합이 비어 있고 deliveryLogs 는 빈 리스트를 반환한다`() {
            stubEmptyStoresFor(targetUser)
            val result = service.gatherPersonalData(targetUser.username)
            result.deliveryLogs shouldBe emptyList()
            // 비어 있을 때는 findByChannelIds 를 호출할 필요가 없어야 한다.
            verify(exactly = 0) { deliveryLogStore.findByChannelIds(any()) }
        }

        @Test
        fun `다른 사용자의 channelId 는 발송 이력 조회 집합에 포함되지 않는다`() {
            stubEmptyStoresFor(targetUser)
            val myRequest = UserClippingRequest(
                id = "req-mine",
                requesterUserId = targetUser.id,
                requestName = "내 구독",
                sourceName = "S",
                sourceUrl = "https://example.com/rss",
                slackChannelId = "C-MINE",
                personaName = "p",
                personaPrompt = "요약",
                status = UserClippingRequestStatus.APPROVED
            )
            every { userClippingRequestStore.listByRequesterUserId(targetUser.id) } returns listOf(myRequest)

            val channelSlot = slot<Collection<String>>()
            every { deliveryLogStore.findByChannelIds(capture(channelSlot)) } returns emptyList()

            service.gatherPersonalData(targetUser.username)

            channelSlot.captured.toSet() shouldBe setOf("C-MINE")
            channelSlot.captured.contains("C-OTHER") shouldBe false
        }
    }

    @Nested
    inner class `민감 필드 누수 방어` {

        @Test
        fun `JSON export 에 password_hash 가 포함되지 않는다`() {
            stubEmptyStoresFor(targetUser)
            every { auditLogStore.log(any(), any(), any(), any(), any(), any(), any()) } returns Unit

            val json = service.exportAsJson(targetUser.username).toString(Charsets.UTF_8)

            json shouldNotContain "password_hash"
            json shouldNotContain "passwordHash"
            json shouldNotContain "super-secret-bcrypt-hash-SHOULD-NOT-LEAK"
            json shouldNotContain "totp_secret"
            json shouldNotContain "totpSecret"
            // 본인 식별 목적의 username 은 포함되어야 한다.
            json shouldContain "alice"
        }

        @Test
        fun `CSV export 에 비밀번호 해시와 토큰 류 키워드가 포함되지 않는다`() {
            stubEmptyStoresFor(targetUser)

            val csv = service.exportAsCsv(targetUser.username).toString(Charsets.UTF_8)

            csv shouldNotContain "password_hash"
            csv shouldNotContain "passwordHash"
            csv shouldNotContain "super-secret-bcrypt-hash"
            csv shouldNotContain "totp_secret"
            // CSV 는 BOM 으로 시작하므로 legalBasis 행을 포함해야 한다.
            csv shouldContain "legalBasis"
            csv shouldContain "alice"
        }

        @Test
        fun `내부 태그가 섞인 requestNote 는 sanitize 된다`() {
            stubEmptyStoresFor(targetUser)
            val dirtyNote = "본문[baseRequestId=abc-123] [설정 변경] 본문 끝"
            val request = UserClippingRequest(
                id = "req-dirty",
                requesterUserId = targetUser.id,
                requestName = "N",
                sourceName = "S",
                sourceUrl = "https://example.com",
                slackChannelId = "C1",
                personaName = "p",
                personaPrompt = "요약",
                status = UserClippingRequestStatus.APPROVED,
                requestNote = dirtyNote
            )
            every { userClippingRequestStore.listByRequesterUserId(targetUser.id) } returns listOf(request)
            every { deliveryLogStore.findByChannelIds(any()) } returns emptyList()

            val result = service.gatherPersonalData(targetUser.username)

            val sanitized = result.subscriptions.single().requestNote
            sanitized shouldNotContain "baseRequestId"
            sanitized shouldNotContain "설정 변경"
            sanitized shouldContain "본문"
        }
    }

    @Nested
    inner class `이메일 마스킹` {

        @Test
        fun `이메일 형식은 첫글자만 남기고 마스킹된다`() {
            service.maskEmail("john@example.com") shouldBe "j***@example.com"
        }

        @Test
        fun `로컬 파트가 없으면 원문을 그대로 반환한다`() {
            service.maskEmail("@example.com") shouldBe "@example.com"
        }

        @Test
        fun `이메일이 아닌 로그인 ID 는 마스킹하지 않는다`() {
            service.maskEmail("alice") shouldBe "alice"
        }

        @Test
        fun `null 또는 빈 문자열은 null 을 반환한다`() {
            service.maskEmail(null) shouldBe null
            service.maskEmail("") shouldBe null
            service.maskEmail("  ") shouldBe null
        }
    }

    @Nested
    inner class `sanitizeInternalTags` {

        @Test
        fun `여러 태그가 모두 제거된다`() {
            service.sanitizeInternalTags("[a=1] 본문 [b=2] 끝") shouldBe "본문  끝"
        }

        @Test
        fun `태그가 없는 문자열은 변형 없이 반환된다`() {
            service.sanitizeInternalTags("깨끗한 본문") shouldBe "깨끗한 본문"
        }

        @Test
        fun `null 또는 빈 문자열은 null 반환`() {
            service.sanitizeInternalTags(null) shouldBe null
            service.sanitizeInternalTags("") shouldBe null
            service.sanitizeInternalTags("[only-tag]") shouldBe null
        }
    }

    @Nested
    inner class `rate limit` {

        @Test
        fun `한도 미만이면 통과한다`() {
            every {
                rateLimitService.isRateLimited(match { it.startsWith("rl:data-export:") }, any(), any())
            } returns false

            service.consumeDailyRateLimit(targetUser.id)

            verify(exactly = 1) { rateLimitService.isRateLimited(any(), PersonalDataExportLimits.MAX_EXPORTS_PER_DAY, any()) }
        }

        @Test
        fun `한도 초과 시 RateLimitExceededException 이 던져진다`() {
            every {
                rateLimitService.isRateLimited(any(), any(), any())
            } returns true

            val exception = shouldThrow<RateLimitExceededException> {
                service.consumeDailyRateLimit(targetUser.id)
            }
            exception.message shouldContain "3회"
        }

        @Test
        fun `rate limit 키에 userId 와 날짜가 포함된다`() {
            val keySlot = slot<String>()
            every {
                rateLimitService.isRateLimited(capture(keySlot), any(), any())
            } returns false

            service.consumeDailyRateLimit(targetUser.id, LocalDate.of(2026, 4, 17))

            keySlot.captured shouldBe "rl:data-export:${targetUser.id}:2026-04-17"
        }
    }

    @Nested
    inner class `audit log` {

        @Test
        fun `JSON export 성공 시 PERSONAL_DATA_EXPORT 감사 로그를 기록한다`() {
            stubEmptyStoresFor(targetUser)
            every { auditLogStore.log(any(), any(), any(), any(), any(), any(), any()) } returns Unit

            service.exportAsJson(targetUser.username)

            verify(exactly = 1) {
                auditLogStore.log(
                    actorId = targetUser.id,
                    actorName = targetUser.username,
                    action = "PERSONAL_DATA_EXPORT",
                    targetType = "USER_DATA",
                    targetId = targetUser.id,
                    targetName = targetUser.username,
                    detail = "format=json"
                )
            }
        }

        @Test
        fun `CSV export 성공 시 detail 필드에 format=csv 가 기록된다`() {
            stubEmptyStoresFor(targetUser)
            every { auditLogStore.log(any(), any(), any(), any(), any(), any(), any()) } returns Unit

            service.exportAsCsv(targetUser.username)

            verify(exactly = 1) {
                auditLogStore.log(
                    any(), any(), "PERSONAL_DATA_EXPORT", any(), any(), any(), "format=csv"
                )
            }
        }
    }

    @Nested
    inner class `다른 사용자 격리` {

        @Test
        fun `쿼리는 본인 userId 로만 제한된다`() {
            stubEmptyStoresFor(targetUser)

            service.gatherPersonalData(targetUser.username)

            // 다른 사용자 id로는 조회되지 않아야 한다.
            verify(exactly = 0) { userClippingRequestStore.listByRequesterUserId(otherUser.id) }
            verify(exactly = 0) { bookmarkedArticleStore.listAllForUser(otherUser.id) }
            verify(exactly = 0) { userOwnedCategoryStore.listCategoryIds(otherUser.id) }
            verify(exactly = 0) { userOwnedSourceStore.listSourceIds(otherUser.id) }

            // 본인 id 에 대해서만 정확히 호출되어야 한다.
            verify(exactly = 1) { userClippingRequestStore.listByRequesterUserId(targetUser.id) }
            verify(exactly = 1) { bookmarkedArticleStore.listAllForUser(targetUser.id) }
        }
    }

    @Nested
    inner class `상한 적용` {

        @Test
        fun `북마크는 MAX_BOOKMARKS 까지만 포함된다`() {
            stubEmptyStoresFor(targetUser)
            val oversize = (1..PersonalDataExportLimits.MAX_BOOKMARKS + 10).map { index ->
                BookmarkedArticle(
                    id = "bm-$index",
                    userId = targetUser.id,
                    summaryId = "sum-$index",
                    originalTitle = "t-$index",
                    translatedTitle = null,
                    summary = "s",
                    insights = null,
                    keywords = emptyList(),
                    importanceScore = 0f,
                    sourceLink = "https://example.com/$index",
                    categoryId = "cat-a",
                    sentiment = null,
                    eventType = null,
                    articleCreatedAt = Instant.EPOCH,
                    bookmarkedAt = Instant.EPOCH
                )
            }
            every { bookmarkedArticleStore.listAllForUser(targetUser.id) } returns oversize

            val result = service.gatherPersonalData(targetUser.username)
            result.bookmarks shouldHaveSize PersonalDataExportLimits.MAX_BOOKMARKS
        }

        @Test
        fun `userEventStore 는 limit 파라미터로 MAX_EVENTS 를 전달받는다`() {
            stubEmptyStoresFor(targetUser)
            val limitSlot = slot<Int>()
            every {
                userEventStore.findByUserAndDateRange(targetUser.id, any(), any(), capture(limitSlot))
            } returns emptyList()

            service.gatherPersonalData(targetUser.username)

            limitSlot.captured shouldBe PersonalDataExportLimits.MAX_EVENTS
        }
    }
}
