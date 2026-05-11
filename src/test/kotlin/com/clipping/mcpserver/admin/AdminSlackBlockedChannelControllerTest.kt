package com.clipping.mcpserver.admin

import com.clipping.mcpserver.admin.AdminSlackBlockedChannelController.Companion.ANONYMIZED_BLOCKED_BY
import com.clipping.mcpserver.admin.dto.BlockChannelRequest
import com.clipping.mcpserver.model.BlockedSlackChannel
import com.clipping.mcpserver.service.AdminBlockedSlackChannelService
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.security.core.Authentication
import java.time.Instant

/**
 * [AdminSlackBlockedChannelController]의 응답 익명화 정책 단위 테스트.
 *
 * DB에 저장된 `blockedByUserId`(= 관리자 username)는 다른 관리자가 조회할 때 신원이
 * 노출될 수 있어, API 응답 단계에서 상수 문자열로 치환된다.
 * 감사 용도로 DB에는 원본 username이 그대로 유지되어야 하므로 본 테스트는
 * "엔티티의 원본 username은 응답에 전파되지 않는다"는 점을 고정한다.
 */
class AdminSlackBlockedChannelControllerTest {

    private val service = mockk<AdminBlockedSlackChannelService>()
    private val controller = AdminSlackBlockedChannelController(service)

    @Nested
    inner class `list 응답 익명화` {

        @Test
        fun `blockedByUserId는 원본 username 대신 고정 익명 문자열로 반환된다`() {
            // given: 저장소 레이어에는 관리자 신원(username)이 그대로 있다고 가정한다.
            every { service.findAll() } returns listOf(
                sampleBlocked(id = "row-1", channelId = "C0123456789", blockedByUserId = "alice"),
                sampleBlocked(id = "row-2", channelId = "C0999999999", blockedByUserId = "bob")
            )

            // when
            val responses = controller.list()

            // then: 응답의 blockedByUserId는 모두 익명 상수로 치환되어야 한다.
            responses.size shouldBe 2
            responses[0].blockedByUserId shouldBe ANONYMIZED_BLOCKED_BY
            responses[1].blockedByUserId shouldBe ANONYMIZED_BLOCKED_BY
            // 나머지 식별자/메타는 원본 그대로 전달되어야 한다.
            responses[0].channelId shouldBe "C0123456789"
            responses[1].channelId shouldBe "C0999999999"
        }

        @Test
        fun `차단 목록이 비어 있으면 빈 리스트를 반환한다`() {
            // given
            every { service.findAll() } returns emptyList()

            // when
            val responses = controller.list()

            // then
            responses shouldBe emptyList()
        }
    }

    @Nested
    inner class `block 응답 익명화` {

        @Test
        fun `차단 직후 응답의 blockedByUserId도 익명 상수로 고정된다`() {
            // given
            val auth = mockk<Authentication>().also { every { it.name } returns "alice" }
            every {
                service.block(
                    adminUsername = "alice",
                    channelId = "C0123456789",
                    channelName = "general",
                    isPrivate = false,
                    reason = "노출 제한"
                )
            } returns sampleBlocked(
                id = "row-new",
                channelId = "C0123456789",
                blockedByUserId = "alice",
                reason = "노출 제한"
            )

            // when
            val response = controller.block(
                authentication = auth,
                request = BlockChannelRequest(
                    channelId = "C0123456789",
                    channelName = "general",
                    isPrivate = false,
                    reason = "노출 제한"
                )
            )

            // then: 다른 관리자가 응답을 본다면 누가 막았는지 특정할 수 없어야 한다.
            response.blockedByUserId shouldBe ANONYMIZED_BLOCKED_BY
            response.channelId shouldBe "C0123456789"
            response.reason shouldBe "노출 제한"
        }
    }

    private fun sampleBlocked(
        id: String,
        channelId: String,
        blockedByUserId: String,
        reason: String? = null
    ): BlockedSlackChannel = BlockedSlackChannel(
        id = id,
        channelId = channelId,
        channelName = "general",
        isPrivate = false,
        blockedByUserId = blockedByUserId,
        blockedAt = Instant.parse("2026-04-01T00:00:00Z"),
        reason = reason
    )
}
