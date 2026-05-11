package com.clipping.mcpserver.service

import com.clipping.mcpserver.error.InvalidInputException
import com.clipping.mcpserver.model.AdminUser
import com.clipping.mcpserver.model.BlockedSlackChannel
import com.clipping.mcpserver.store.AdminUserStore
import com.clipping.mcpserver.store.AuditLogStore
import com.clipping.mcpserver.store.BlockedSlackChannelStore
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * [AdminBlockedSlackChannelService]의 차단 정책 검증 테스트.
 *
 * 채널 ID 정규화는 [com.clipping.mcpserver.support.SlackChannelIdNormalizer]로 위임되었고,
 * 차단 사유는 서비스 레이어에서 길이 제한을 강제한다.
 */
class AdminBlockedSlackChannelServiceTest {

    private val blockedStore = mockk<BlockedSlackChannelStore>()
    private val adminUserStore = mockk<AdminUserStore>()
    private val auditLogStore = mockk<AuditLogStore>(relaxed = true)
    private val slackMessageSender = mockk<SlackMessageSender>()
    private val runtimeSettingService = mockk<RuntimeSettingService>()

    private val service = AdminBlockedSlackChannelService(
        blockedSlackChannelStore = blockedStore,
        adminUserStore = adminUserStore,
        auditLogStore = auditLogStore,
        slackMessageSender = slackMessageSender,
        runtimeSettingService = runtimeSettingService
    )

    private val admin = AdminUser(
        id = "admin-1",
        username = "eddy",
        passwordHash = "hash"
    )

    @Nested
    inner class `block 채널 차단` {

        @Test
        fun `정상 채널 ID로 차단하면 정규화된 ID와 감사 로그가 기록된다`() {
            // given
            every { adminUserStore.findByUsername("eddy") } returns admin
            every { blockedStore.existsByChannelId("C0123456789") } returns false
            val saved = slot<BlockedSlackChannel>()
            every { blockedStore.save(capture(saved)) } answers { saved.captured.copy(id = "row-1") }

            // when: 공백/소문자 포함된 입력도 정규화되어야 한다
            val result = service.block(
                adminUsername = "eddy",
                channelId = " c0123456789 ",
                channelName = "general",
                isPrivate = false,
                reason = "노출 제한"
            )

            // then
            result.channelId shouldBe "C0123456789"
            saved.captured.reason shouldBe "노출 제한"
            verify(exactly = 1) {
                auditLogStore.log(
                    actorId = "admin-1",
                    actorName = "eddy",
                    action = "SLACK_CHANNEL_BLOCKED",
                    targetType = "SLACK_CHANNEL",
                    targetId = "C0123456789",
                    targetName = "general"
                )
            }
        }

        @Test
        fun `잘못된 채널 ID는 InvalidInputException을 던진다`() {
            // given: 'C1'은 SlackChannelIdNormalizer가 거부해야 한다.
            // when & then
            val thrown = shouldThrow<InvalidInputException> {
                service.block(
                    adminUsername = "eddy",
                    channelId = "C1",
                    channelName = "bad"
                )
            }
            thrown.message shouldContain "채널 ID 형식"
            verify(exactly = 0) { blockedStore.save(any()) }
            verify(exactly = 0) { auditLogStore.log(any(), any(), any(), any(), any(), any(), any()) }
        }

        @Test
        fun `차단 사유가 200자를 초과하면 InvalidInputException을 던진다`() {
            // given: 저장/조회가 호출되기 전에 검증이 먼저 실패해야 한다.
            val overLong = "가".repeat(201)

            // when & then
            val thrown = shouldThrow<InvalidInputException> {
                service.block(
                    adminUsername = "eddy",
                    channelId = "C0123456789",
                    channelName = "general",
                    reason = overLong
                )
            }
            thrown.message shouldContain "200자"
            verify(exactly = 0) { blockedStore.existsByChannelId(any()) }
            verify(exactly = 0) { blockedStore.save(any()) }
        }

        @Test
        fun `Slack archive URL 형태 입력도 채널 ID로 정규화된다`() {
            // given
            every { adminUserStore.findByUsername("eddy") } returns admin
            every { blockedStore.existsByChannelId("C0123456789") } returns false
            val saved = slot<BlockedSlackChannel>()
            every { blockedStore.save(capture(saved)) } answers { saved.captured.copy(id = "row-2") }

            // when
            val result = service.block(
                adminUsername = "eddy",
                channelId = "https://example.slack.com/archives/C0123456789",
                channelName = "announce"
            )

            // then
            result.channelId shouldBe "C0123456789"
            saved.captured.channelId shouldBe "C0123456789"
        }

        @Test
        fun `차단 사유가 정확히 200자이면 성공한다`() {
            // given: 경계값 — 정확히 200자는 허용.
            every { adminUserStore.findByUsername("eddy") } returns admin
            every { blockedStore.existsByChannelId("C0123456789") } returns false
            val saved = slot<BlockedSlackChannel>()
            every { blockedStore.save(capture(saved)) } answers { saved.captured.copy(id = "row-3") }
            val boundary = "a".repeat(200)

            // when
            service.block(
                adminUsername = "eddy",
                channelId = "C0123456789",
                channelName = "general",
                reason = boundary
            )

            // then
            saved.captured.reason shouldBe boundary
        }
    }
}
