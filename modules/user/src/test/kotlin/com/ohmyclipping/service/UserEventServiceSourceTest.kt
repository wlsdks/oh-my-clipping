package com.ohmyclipping.service

import com.ohmyclipping.model.UserEvent
import com.ohmyclipping.store.AdminUserStore
import com.ohmyclipping.store.UserEventStore
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class UserEventServiceSourceTest {

    private val userEventStore = mockk<UserEventStore>(relaxed = true)
    private val adminUserStore = mockk<AdminUserStore>(relaxed = true)
    private val objectMapper = ObjectMapper()
    private val service = UserEventService(userEventStore, adminUserStore, objectMapper)

    private fun parsePayload(event: UserEvent): Map<String, Any> {
        val data = event.eventData ?: error("eventData must not be null")
        return objectMapper.readValue(data, object : TypeReference<Map<String, Any>>() {})
    }

    @Nested
    @DisplayName("saveClick source 태깅")
    inner class SaveClickWithSource {

        @Test
        fun `source 전달 시 event_data 에 source 키 포함`() {
            val captured = slot<List<UserEvent>>()

            service.saveClick("u1", "s1", "https://example.com/a", source = "slack")

            verify(exactly = 1) { userEventStore.saveBatch(capture(captured)) }
            val payload = parsePayload(captured.captured.single())
            payload["source"] shouldBe "slack"
            payload["summaryId"] shouldBe "s1"
            payload["url"] shouldBe "https://example.com/a"
        }

        @Test
        fun `source 없음 (기존 호출자) — source 키 미포함`() {
            val captured = slot<List<UserEvent>>()

            service.saveClick("u1", "s1", "https://example.com/a")

            verify(exactly = 1) { userEventStore.saveBatch(capture(captured)) }
            val payload = parsePayload(captured.captured.single())
            payload shouldNotContainKey "source"
            payload shouldContainKey "summaryId"
            payload shouldContainKey "url"
        }

        @Test
        fun `source 공백 또는 빈 문자열은 정규화되어 제거`() {
            val captured = slot<List<UserEvent>>()

            service.saveClick("u1", "s1", "https://example.com/a", source = "   ")

            verify(exactly = 1) { userEventStore.saveBatch(capture(captured)) }
            val payload = parsePayload(captured.captured.single())
            payload shouldNotContainKey "source"
        }

        @Test
        fun `source 대소문자 정규화 — SLACK 도 slack 으로 저장`() {
            val captured = slot<List<UserEvent>>()

            service.saveClick("u1", "s1", "https://example.com/a", source = "SLACK")

            verify(exactly = 1) { userEventStore.saveBatch(capture(captured)) }
            val payload = parsePayload(captured.captured.single())
            payload["source"] shouldBe "slack"
        }

        @Test
        fun `허용 목록 밖 source 는 제거 (unknown 값 방어)`() {
            val captured = slot<List<UserEvent>>()

            service.saveClick("u1", "s1", "https://example.com/a", source = "malicious")

            verify(exactly = 1) { userEventStore.saveBatch(capture(captured)) }
            val payload = parsePayload(captured.captured.single())
            payload shouldNotContainKey "source"
        }
    }
}
