package com.ohmyclipping.admin.mcp

import com.ohmyclipping.error.AccessForbiddenException
import com.ohmyclipping.error.NotFoundException
import com.ohmyclipping.mcp.McpRateLimiter
import com.ohmyclipping.model.Category
import com.ohmyclipping.service.CategoryService
import com.ohmyclipping.service.SlackMessageSender
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * admin_slack_channel_diagnose 단위 테스트.
 *
 * 검증 포인트:
 *  - 해피패스: 채널 정상 조회 시 canPost=true, botJoined=true.
 *  - 채널 미설정: issues 에 설정 안내 메시지.
 *  - 봇 미참여: AccessForbiddenException → botJoined=false, canPost=false, 초대 안내 issue.
 *  - 채널 not_found: NotFoundException → canPost=false.
 */
class AdminSlackChannelDiagnoseToolTest {

    private val categoryService = mockk<CategoryService>()
    private val slackMessageSender = mockk<SlackMessageSender>()
    private val rateLimiter = mockk<McpRateLimiter>()
    private val tool = AdminSlackChannelDiagnoseTool(categoryService, slackMessageSender, rateLimiter)

    private val category = Category(
        id = "cat-1",
        name = "Tech",
        slackChannelId = "C0123ABC",
    )

    @Nested
    inner class `admin_slack_channel_diagnose` {

        @Test
        fun `해피패스 — 채널 정상이면 canPost true 를 반환한다`() {
            every { rateLimiter.checkOrThrow(any(), any(), any(), any(), any()) } just Runs
            every { categoryService.findById("cat-1") } returns category
            every {
                slackMessageSender.getChannelInfo(botToken = null, channelId = "C0123ABC")
            } returns SlackMessageSender.SlackChannel(
                id = "C0123ABC", name = "tech-news", isPrivate = false,
            )

            val json = tool.admin_slack_channel_diagnose(categoryId = "cat-1")

            json shouldContain "\"canPost\":true"
            json shouldContain "\"botJoined\":true"
            json shouldContain "\"channelName\":\"tech-news\""
            json shouldNotContain "\"error\""
        }

        @Test
        fun `categoryId 는 trim 해서 rate limit dimension 과 조회에 사용한다`() {
            every { rateLimiter.checkOrThrow(any(), any(), any(), any(), any()) } just Runs
            every { categoryService.findById("cat-1") } returns category
            every {
                slackMessageSender.getChannelInfo(botToken = null, channelId = "C0123ABC")
            } returns SlackMessageSender.SlackChannel(
                id = "C0123ABC", name = "tech-news", isPrivate = false,
            )

            val json = tool.admin_slack_channel_diagnose(categoryId = " cat-1 ")

            json shouldContain "\"categoryId\":\"cat-1\""
            verify(exactly = 1) {
                rateLimiter.checkOrThrow(
                    toolName = "admin_slack_channel_diagnose",
                    maxRequests = 30,
                    windowSeconds = 3600,
                    dimension = "cat-1",
                    actor = null,
                )
            }
            verify(exactly = 1) { categoryService.findById("cat-1") }
        }

        @Test
        fun `채널이 설정되지 않은 카테고리는 canPost false 와 안내 메시지`() {
            every { rateLimiter.checkOrThrow(any(), any(), any(), any(), any()) } just Runs
            every {
                categoryService.findById("cat-1")
            } returns category.copy(slackChannelId = null)

            val json = tool.admin_slack_channel_diagnose(categoryId = "cat-1")

            json shouldContain "\"canPost\":false"
            json shouldContain "\"botJoined\":false"
            json shouldContain "Slack 채널이 설정되지 않았습니다"
            // Slack API 는 호출되면 안 된다.
            verify(exactly = 0) { slackMessageSender.getChannelInfo(any(), any()) }
        }

        @Test
        fun `봇이 채널에 참여하지 않은 경우 botJoined false 와 초대 안내`() {
            every { rateLimiter.checkOrThrow(any(), any(), any(), any(), any()) } just Runs
            every { categoryService.findById("cat-1") } returns category
            every {
                slackMessageSender.getChannelInfo(botToken = null, channelId = "C0123ABC")
            } throws AccessForbiddenException("not_in_channel")

            val json = tool.admin_slack_channel_diagnose(categoryId = "cat-1")

            json shouldContain "\"canPost\":false"
            json shouldContain "\"botJoined\":false"
            json shouldContain "봇을 초대"
        }

        @Test
        fun `채널이 존재하지 않으면 NotFound issue 와 canPost false`() {
            every { rateLimiter.checkOrThrow(any(), any(), any(), any(), any()) } just Runs
            every { categoryService.findById("cat-1") } returns category
            every {
                slackMessageSender.getChannelInfo(botToken = null, channelId = "C0123ABC")
            } throws NotFoundException("channel_not_found")

            val json = tool.admin_slack_channel_diagnose(categoryId = "cat-1")

            json shouldContain "\"canPost\":false"
            json shouldContain "Slack 채널을 찾을 수 없습니다"
        }

        @Test
        fun `빈 categoryId 는 rate limit 차감 없이 validation error 로 거부된다`() {
            val json = tool.admin_slack_channel_diagnose(categoryId = " ")

            json shouldContain "\"error\""
            json shouldContain "categoryId is required"
            verify(exactly = 0) { rateLimiter.checkOrThrow(any(), any(), any(), any(), any()) }
            verify(exactly = 0) { categoryService.findById(any()) }
            verify(exactly = 0) { slackMessageSender.getChannelInfo(any(), any()) }
        }
    }
}
