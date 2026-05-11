package com.clipping.mcpserver.admin

import com.clipping.mcpserver.admin.dto.BlockChannelRequest
import com.clipping.mcpserver.admin.dto.BlockedChannelResponse
import com.clipping.mcpserver.model.BlockedSlackChannel
import com.clipping.mcpserver.service.AdminBlockedSlackChannelService
import com.clipping.mcpserver.service.dto.SlackChannelListResponse
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

/**
 * 관리자용 Slack 채널 차단 관리 API.
 * 차단된 채널은 사용자 채널 목록에서 자동 제외된다.
 *
 * 응답의 `blockedByUserId`는 [ANONYMIZED_BLOCKED_BY] 상수("관리자")로 고정 반환된다.
 * DB에는 감사 목적으로 원본 관리자 username이 그대로 저장되지만, API를 통해 다른 관리자가
 * "누가 차단했는지" 식별할 수 없도록 익명화한다. 운영팀은 DB의
 * `blocked_slack_channels.blocked_by_user_id` 컬럼으로 직접 추적할 수 있다.
 */
@RestController
@RequestMapping("/api/admin/slack/blocked-channels")
class AdminSlackBlockedChannelController(
    private val adminBlockedSlackChannelService: AdminBlockedSlackChannelService
) {

    companion object {
        /** 응답 전용 익명화 표기. 사용자 신원 노출 방지용 고정 문자열. */
        const val ANONYMIZED_BLOCKED_BY: String = "관리자"
    }

    /** 차단된 채널 전체 목록을 반환한다. */
    @GetMapping
    fun list(): List<BlockedChannelResponse> =
        adminBlockedSlackChannelService.findAll().map { it.toResponse() }

    /** 관리자용 Slack 채널 목록 조회 (차단 대상 선택용). */
    @GetMapping("/available-channels")
    fun listAvailableChannels(@RequestParam type: String): SlackChannelListResponse =
        adminBlockedSlackChannelService.listAvailableChannels(type)

    /** 채널을 차단 목록에 추가한다. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun block(
        authentication: Authentication,
        @RequestBody request: BlockChannelRequest
    ): BlockedChannelResponse {
        val blocked = adminBlockedSlackChannelService.block(
            adminUsername = authentication.name,
            channelId = request.channelId,
            channelName = request.channelName,
            isPrivate = request.isPrivate,
            reason = request.reason
        )
        return blocked.toResponse()
    }

    /** 채널 차단을 해제한다. */
    @DeleteMapping("/{channelId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun unblock(authentication: Authentication, @PathVariable channelId: String) {
        adminBlockedSlackChannelService.unblock(
            adminUsername = authentication.name,
            channelId = channelId
        )
    }

    /**
     * 엔티티를 API 응답 DTO로 변환한다.
     *
     * `blockedByUserId`는 감사 DB의 원본 username 대신 익명화된 [ANONYMIZED_BLOCKED_BY]로 치환한다.
     */
    private fun BlockedSlackChannel.toResponse() = BlockedChannelResponse(
        id = id,
        channelId = channelId,
        channelName = channelName,
        isPrivate = isPrivate,
        // 응답에서 관리자 신원 익명화 — DB의 원본 username은 보존된다.
        blockedByUserId = ANONYMIZED_BLOCKED_BY,
        blockedAt = blockedAt.toString(),
        reason = reason
    )
}
