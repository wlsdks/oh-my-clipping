package com.ohmyclipping.service

import com.ohmyclipping.error.InvalidInputException
import com.ohmyclipping.model.UserEvent
import com.ohmyclipping.service.dto.user.UserEventRequest
import com.ohmyclipping.store.AdminUserStore
import com.ohmyclipping.store.UserEventStore
import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class UserEventServiceTest {

    private val store = mockk<UserEventStore>(relaxed = true)
    private val adminUserStore = mockk<AdminUserStore>(relaxed = true)
    private val objectMapper = ObjectMapper()
    private val service = UserEventService(store, adminUserStore, objectMapper)

    private val userId = "user-123"

    @Nested
    inner class `이벤트 일괄 저장` {

        @Test
        fun `유효한 이벤트가 정상 저장된다`() {
            val request = UserEventRequest(
                eventType = "page_view",
                eventData = mapOf("path" to "/home"),
                pagePath = "/home",
                sessionId = "sess-1",
                timestamp = 1710000000000L
            )

            val result = service.saveBatch(userId, listOf(request))

            result.accepted shouldBe 1
            result.rejected shouldBe 0

            // 저장소에 1건이 전달되었는지 검증한다.
            val captured = slot<List<UserEvent>>()
            verify(exactly = 1) { store.saveBatch(capture(captured)) }
            captured.captured.size shouldBe 1
            captured.captured[0].eventType shouldBe "page_view"
            captured.captured[0].userId shouldBe userId
            captured.captured[0].pagePath shouldBe "/home"
            captured.captured[0].eventData.shouldBeNull()
        }

        @Test
        fun `허용되지 않은 이벤트 타입은 거부된다`() {
            val request = UserEventRequest(
                eventType = "invalid_type",
                eventData = null,
                pagePath = null,
                sessionId = "sess-1",
                timestamp = 1710000000000L
            )

            val result = service.saveBatch(userId, listOf(request))

            result.accepted shouldBe 0
            result.rejected shouldBe 1

            // 유효한 이벤트가 없으므로 저장소가 호출되지 않는다.
            verify(exactly = 0) { store.saveBatch(any()) }
        }

        @Test
        fun `빈 이벤트 목록은 InvalidInputException을 발생시킨다`() {
            val exception = shouldThrow<InvalidInputException> {
                service.saveBatch(userId, emptyList())
            }

            exception.message shouldBe "이벤트 목록이 비어있습니다."
            verify(exactly = 0) { store.saveBatch(any()) }
        }

        @Test
        fun `유효한 이벤트와 무효한 이벤트가 섞이면 유효한 것만 저장된다`() {
            val validEvent = UserEventRequest(
                eventType = "article_click",
                eventData = mapOf("summaryId" to "s-article-1"),
                pagePath = "/articles",
                sessionId = "sess-2",
                timestamp = 1710000000000L
            )
            val invalidEvent = UserEventRequest(
                eventType = "unknown_event",
                eventData = null,
                pagePath = null,
                sessionId = "sess-2",
                timestamp = 1710000000000L
            )
            val anotherValid = UserEventRequest(
                eventType = "bookmark_toggle",
                eventData = mapOf("summaryId" to "s-1"),
                pagePath = "/articles",
                sessionId = "sess-2",
                timestamp = 1710000001000L
            )

            val result = service.saveBatch(
                userId,
                listOf(validEvent, invalidEvent, anotherValid)
            )

            result.accepted shouldBe 2
            result.rejected shouldBe 1

            // 유효한 2건만 저장소에 전달된다.
            val captured = slot<List<UserEvent>>()
            verify(exactly = 1) { store.saveBatch(capture(captured)) }
            captured.captured.size shouldBe 2
        }

        @Test
        fun `eventData가 JSON 문자열로 직렬화된다`() {
            val request = UserEventRequest(
                eventType = "wizard_step",
                eventData = mapOf("step" to "select_topic", "action" to "enter"),
                pagePath = "/wizard",
                sessionId = "sess-3",
                timestamp = 1710000000000L
            )

            service.saveBatch(userId, listOf(request))

            val captured = slot<List<UserEvent>>()
            verify(exactly = 1) { store.saveBatch(capture(captured)) }
            val savedEvent = captured.captured[0]
            // eventData가 JSON 문자열로 저장되었는지 확인한다.
            savedEvent.eventData shouldBe
                """{"step":"select_topic","action":"enter"}"""
        }

        @Test
        fun `article 이벤트는 summaryId만 남기고 나머지 자유입력을 버린다`() {
            val request = UserEventRequest(
                eventType = "article_click",
                eventData = mapOf(
                    "summaryId" to "sum-1",
                    "title" to "민감할 수 있는 자유입력 제목",
                    "email" to "test@example.com"
                ),
                pagePath = "/articles",
                sessionId = "sess-4",
                timestamp = 1710000000000L
            )

            service.saveBatch(userId, listOf(request))

            val captured = slot<List<UserEvent>>()
            verify(exactly = 1) { store.saveBatch(capture(captured)) }
            captured.captured[0].eventData shouldBe """{"summaryId":"sum-1"}"""
        }

        @Test
        fun `summaryId 없는 article 이벤트는 거부된다`() {
            val request = UserEventRequest(
                eventType = "article_click",
                eventData = mapOf("title" to "뉴스A"),
                pagePath = "/articles",
                sessionId = "sess-5",
                timestamp = 1710000000000L
            )

            val result = service.saveBatch(userId, listOf(request))

            result.accepted shouldBe 0
            result.rejected shouldBe 1
            verify(exactly = 0) { store.saveBatch(any()) }
        }

        @Test
        fun `sessionId 길이가 저장 컬럼을 넘으면 거부된다`() {
            val request = UserEventRequest(
                eventType = "page_view",
                eventData = mapOf("path" to "/home"),
                pagePath = "/home",
                sessionId = "x".repeat(65),
                timestamp = 1710000000000L
            )

            val result = service.saveBatch(userId, listOf(request))

            result.accepted shouldBe 0
            result.rejected shouldBe 1
            verify(exactly = 0) { store.saveBatch(any()) }
        }

        @Test
        fun `모든 허용된 이벤트 타입이 계약을 만족하면 수락된다`() {
            val requests = listOf(
                UserEventRequest("page_view", mapOf("path" to "/home"), "/home", "sess-all", 1710000000002L),
                UserEventRequest(
                    "article_impression",
                    mapOf("summaryId" to "sum-1", "title" to "drop-me"),
                    "/articles",
                    "sess-all",
                    1710000000003L
                ),
                UserEventRequest(
                    "article_click",
                    mapOf("summaryId" to "sum-2"),
                    "/articles",
                    "sess-all",
                    1710000000004L
                ),
                UserEventRequest(
                    "wizard_step",
                    mapOf("step" to "topic", "action" to "complete"),
                    "/wizard",
                    "sess-all",
                    1710000000005L
                ),
                UserEventRequest(
                    "bookmark_toggle",
                    mapOf("summaryId" to "sum-3", "action" to "add"),
                    "/articles",
                    "sess-all",
                    1710000000006L
                ),
                UserEventRequest(
                    "article_share_passive",
                    mapOf("summaryId" to "sum-4"),
                    "/articles",
                    "sess-all",
                    1710000000007L
                )
            )

            val result = service.saveBatch(userId, requests)

            result.accepted shouldBe UserEventService.ALLOWED_EVENT_TYPES.size
            result.rejected shouldBe 0
        }
    }

    @Nested
    inner class `활동 요약 배치 조회` {
        @Test
        fun `빈 유저 목록이면 빈 맵을 반환한다`() {
            val result = service.buildRecentActivitySummaryBatch(emptyList())
            result shouldBe emptyMap()
            verify(exactly = 0) { store.countEventsByTypeForUsers(any(), any(), any()) }
        }

        @Test
        fun `이벤트가 없는 유저는 null을 반환한다`() {
            every { store.countEventsByTypeForUsers(any(), any(), any()) } returns emptyMap()
            val result = service.buildRecentActivitySummaryBatch(listOf("user-1"))
            result shouldBe mapOf("user-1" to null)
        }

        @Test
        fun `이벤트가 있는 유저는 한국어 요약 문자열을 반환한다`() {
            every { store.countEventsByTypeForUsers(any(), any(), any()) } returns mapOf(
                "user-1" to mapOf("article_click" to 5, "bookmark_toggle" to 2)
            )
            val result = service.buildRecentActivitySummaryBatch(listOf("user-1"))
            result["user-1"] shouldBe "최근 7일: 기사 5건 열람, 북마크 2건"
        }
    }

    @Nested
    inner class `최근 활동 요약` {

        private fun makeEvent(
            eventType: String,
            userId: String = "user-123"
        ) = UserEvent(
            userId = userId,
            eventType = eventType,
            createdAt = java.time.Instant.now()
        )

        @Test
        fun `다양한 이벤트 타입이 있으면 한국어 요약 문자열을 반환한다`() {
            // article_click 3건, article_impression 2건, bookmark_toggle 1건, page_view 4건
            val events = listOf(
                makeEvent("article_click"),
                makeEvent("article_click"),
                makeEvent("article_click"),
                makeEvent("article_impression"),
                makeEvent("article_impression"),
                makeEvent("bookmark_toggle"),
                makeEvent("page_view"),
                makeEvent("page_view"),
                makeEvent("page_view"),
                makeEvent("page_view")
            )
            every { store.findByUserAndDateRange(any(), any(), any(), any()) } returns events

            val result = service.buildRecentActivitySummary("user-123")

            result shouldBe "최근 7일: 기사 5건 열람, 북마크 1건, 페이지 4회 조회"
        }

        @Test
        fun `최근 7일간 이벤트가 없으면 null을 반환한다`() {
            every { store.findByUserAndDateRange(any(), any(), any(), any()) } returns emptyList()

            val result = service.buildRecentActivitySummary("user-123")

            result.shouldBeNull()
        }

        @Test
        fun `하나의 이벤트 타입만 있으면 해당 타입만 포함된 문자열을 반환한다`() {
            val events = listOf(
                makeEvent("bookmark_toggle"),
                makeEvent("bookmark_toggle"),
                makeEvent("bookmark_toggle")
            )
            every { store.findByUserAndDateRange(any(), any(), any(), any()) } returns events

            val result = service.buildRecentActivitySummary("user-123")

            result shouldBe "최근 7일: 북마크 3건"
        }

        @Test
        fun `요약 대상이 아닌 이벤트 타입만 있으면 null을 반환한다`() {
            // wizard_step 등 요약 대상이 아닌 타입만 있으면 null을 반환한다.
            val events = listOf(
                makeEvent("wizard_step"),
                makeEvent("wizard_step")
            )
            every { store.findByUserAndDateRange(any(), any(), any(), any()) } returns events

            val result = service.buildRecentActivitySummary("user-123")

            result.shouldBeNull()
        }
    }

    @Nested
    inner class `summary_id 컬럼 저장 (V75)` {

        @Test
        fun `article_click 이벤트는 summaryId 컬럼에도 값을 저장한다`() {
            val request = UserEventRequest(
                eventType = "article_click",
                eventData = mapOf("summaryId" to "summary-abc-123"),
                pagePath = null,
                sessionId = "sess-1",
                timestamp = 1710000000000L
            )

            service.saveBatch(userId, listOf(request))

            val captured = slot<List<UserEvent>>()
            verify(exactly = 1) { store.saveBatch(capture(captured)) }
            captured.captured.size shouldBe 1
            captured.captured[0].summaryId shouldBe "summary-abc-123"
            captured.captured[0].eventType shouldBe "article_click"
        }

        @Test
        fun `bookmark_toggle 이벤트는 summaryId 컬럼에도 값을 저장한다`() {
            val request = UserEventRequest(
                eventType = "bookmark_toggle",
                eventData = mapOf(
                    "summaryId" to "summary-xyz-789",
                    "action" to "add"
                ),
                pagePath = null,
                sessionId = "sess-1",
                timestamp = 1710000000000L
            )

            service.saveBatch(userId, listOf(request))

            val captured = slot<List<UserEvent>>()
            verify(exactly = 1) { store.saveBatch(capture(captured)) }
            captured.captured[0].summaryId shouldBe "summary-xyz-789"
        }

        @Test
        fun `page_view 이벤트는 summaryId 가 null 로 유지된다`() {
            val request = UserEventRequest(
                eventType = "page_view",
                eventData = null,
                pagePath = "/admin",
                sessionId = "sess-1",
                timestamp = 1710000000000L
            )

            service.saveBatch(userId, listOf(request))

            val captured = slot<List<UserEvent>>()
            verify(exactly = 1) { store.saveBatch(capture(captured)) }
            captured.captured[0].summaryId.shouldBeNull()
        }
    }

    @Nested
    inner class `savePassiveShare - 패시브 공유 기록` {

        @Test
        fun `dedup 컬럼과 payload 가 모두 채워져 저장된다`() {
            service.savePassiveShare(
                userId = "U123",
                summaryId = "sum-abc",
                targetChannelId = "C999",
                slackMessageTs = "1713400000.0001"
            )

            val captured = slot<List<UserEvent>>()
            verify(exactly = 1) { store.saveBatch(capture(captured)) }
            val saved = captured.captured[0]
            saved.eventType shouldBe "article_share_passive"
            saved.userId shouldBe "U123"
            saved.summaryId shouldBe "sum-abc"
            saved.targetChannelId shouldBe "C999"
            saved.slackMessageTs shouldBe "1713400000.0001"
            saved.sessionId shouldBe "slack-link-shared"
            // payload 는 dedup 컬럼과 동일 정보를 중복 기록한다
            saved.eventData shouldBe
                """{"summaryId":"sum-abc","targetChannelId":"C999","messageTs":"1713400000.0001"}"""
        }
    }
}
