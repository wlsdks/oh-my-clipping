package com.ohmyclipping.admin.mcp

import com.ohmyclipping.error.AccessForbiddenException
import com.ohmyclipping.error.InvalidInputException
import com.ohmyclipping.error.NotFoundException
import com.ohmyclipping.mcp.McpRateLimiter
import com.ohmyclipping.mcp.mcpToolCall
import com.ohmyclipping.service.CategoryService
import com.ohmyclipping.service.SlackMessageSender
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Component

/**
 * Admin 도구 — 카테고리에 설정된 Slack 채널의 발송 가능 여부를 진단한다.
 *
 * 진단 항목:
 *  - 채널 존재 여부 (`conversations.info` 호출)
 *  - 봇이 채널 멤버인지 (`not_in_channel` 감지)
 *  - 이 정보를 기반으로 한 `canPost` 종합 판정
 *
 * 실제 Slack API 를 호출하는 I/O 작업이며, 단위 테스트는 mock 으로만 검증 가능하다.
 */
@Component
class AdminSlackChannelDiagnoseTool(
    private val categoryService: CategoryService,
    private val slackMessageSender: SlackMessageSender,
    private val rateLimiter: McpRateLimiter,
) {

    /**
     * tools/list 에 직접 노출되는 결과 스키마.
     */
    data class SlackChannelDiagnosis(
        val categoryId: String,
        val channelId: String?,
        val channelName: String?,
        val isPrivate: Boolean?,
        val botJoined: Boolean,
        val canPost: Boolean,
        val issues: List<String>,
    )

    @Tool(
        description = """
            특정 카테고리의 Slack 발송 채널을 점검한다. (채널 존재 / 봇 초대 / 게시 가능 여부)
            **언제 쓰나:** 운영자가 "이 카테고리 Slack 설정 제대로 됐어?", "발송 실패 원인 뭐야" 를 물어볼 때.
            **쓰지 말 것:** 실제 메시지 발송이 필요한 경우 — admin_send_digest 사용.
            **파라미터:** categoryId 필수. 카테고리의 slackChannelId 를 자동으로 조회해 진단한다.
            **반환:** `{ categoryId, channelId, channelName, isPrivate, botJoined, canPost, issues[] }`.
            **결정 규칙:** `canPost=false` 이면 issues 항목을 사용자에게 그대로 보여주고 수정 액션을 제안한다.
        """,
    )
    fun admin_slack_channel_diagnose(
        @ToolParam(description = "진단할 카테고리 ID") categoryId: String,
    ): String = mcpToolCall {
        if (categoryId.isBlank()) {
            throw InvalidInputException("categoryId is required")
        }

        // 빈도 제한: 카테고리 단위로 30회/시간. 채널당 과도한 Slack API 호출을 막는다.
        rateLimiter.checkOrThrow(
            toolName = "admin_slack_channel_diagnose",
            maxRequests = 30,
            windowSeconds = 3600,
            dimension = categoryId,
        )

        val category = categoryService.findById(categoryId)
            ?: throw NotFoundException("Category not found: $categoryId")

        val issues = mutableListOf<String>()
        val storedChannelId = category.slackChannelId?.trim()?.ifBlank { null }
        if (storedChannelId == null) {
            issues += "카테고리에 Slack 채널이 설정되지 않았습니다"
            return@mcpToolCall SlackChannelDiagnosis(
                categoryId = categoryId,
                channelId = null,
                channelName = null,
                isPrivate = null,
                botJoined = false,
                canPost = false,
                issues = issues,
            )
        }

        // conversations.info — 채널 존재 확인. not_in_channel 은 AccessForbiddenException 으로 돌아옴.
        var channelName: String? = null
        var isPrivate: Boolean? = null
        var botJoined = false
        try {
            val info = slackMessageSender.getChannelInfo(botToken = null, channelId = storedChannelId)
            channelName = info.name
            isPrivate = info.isPrivate
            // 여기까지 왔으면 bot 이 channel 을 조회 가능한 상태 — 퍼블릭 멤버이거나 프라이빗 멤버.
            botJoined = true
        } catch (e: NotFoundException) {
            issues += "Slack 채널을 찾을 수 없습니다 ($storedChannelId) — 삭제되었거나 ID 가 잘못되었습니다"
        } catch (e: AccessForbiddenException) {
            issues += "봇이 채널에 참여하지 않았습니다 — Slack 채널에서 봇을 초대(`/invite @봇이름`)해야 합니다"
        }

        val canPost = botJoined && issues.isEmpty()
        SlackChannelDiagnosis(
            categoryId = categoryId,
            channelId = storedChannelId,
            channelName = channelName,
            isPrivate = isPrivate,
            botJoined = botJoined,
            canPost = canPost,
            issues = issues,
        )
    }
}
