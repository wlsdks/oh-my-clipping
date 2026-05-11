package com.ohmyclipping.admin

import com.ohmyclipping.service.dto.admin.SlackChannelDto
import com.ohmyclipping.service.dto.admin.SlackChannelListResponse
import com.ohmyclipping.admin.dto.SlackConnectionVerifyRequest
import com.ohmyclipping.admin.dto.SlackConnectionVerifyResponse
import com.ohmyclipping.service.UserSetupResourceService
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 사용자 quick setup에서 사용할 Slack 채널 확인 API를 제공한다.
 */
@RestController
@RequestMapping("/api/user/setup/slack")
class UserSetupSlackController(
    private val userSetupResourceService: UserSetupResourceService
) {

    /**
     * 로그인 USER가 입력한 Slack 채널 ID를 현재 운영 토큰으로 검증한다.
     */
    @PostMapping("/verify")
    fun verify(
        authentication: Authentication,
        @RequestBody request: SlackConnectionVerifyRequest
    ): SlackConnectionVerifyResponse {
        val result = userSetupResourceService.verifyOwnSlackChannel(
            requesterUsername = authentication.name,
            slackChannelId = request.slackChannelId
        )
        return SlackConnectionVerifyResponse(
            ok = result.ok,
            botUser = result.botUser,
            team = result.team,
            channelId = result.channelId,
            channelName = result.channelName,
            neededScopes = result.neededScopes,
            providedScopes = result.providedScopes,
            message = if (result.ok) "Slack 채널을 확인했어요." else result.rawError ?: "Slack 채널 확인에 실패했어요.",
            warning = result.warning
        )
    }

    /**
     * 운영 봇 토큰으로 접근 가능한 Slack 채널 목록을 조회한다.
     * type: public_channel 또는 private_channel.
     * 비공개 채널은 사용자 멤버십 필터링이 적용되며, 메타 정보를 함께 반환한다.
     * refresh=true이면 slack-channels 캐시를 우회해 Slack API에서 최신 목록을 받아온다.
     */
    @GetMapping("/channels")
    fun listChannels(
        authentication: Authentication,
        @RequestParam type: String,
        @RequestParam(defaultValue = "false") refresh: Boolean
    ): SlackChannelListResponse {
        return userSetupResourceService.listOwnSlackChannels(
            requesterUsername = authentication.name,
            channelType = type,
            refresh = refresh
        )
    }

    /**
     * 채널 ID로 단건 채널 정보를 조회한다.
     * 수정 모드에서 기존 channelId의 public/private 여부를 판별할 때 사용한다.
     */
    @GetMapping("/channels/{channelId}")
    fun getChannelInfo(
        authentication: Authentication,
        @PathVariable channelId: String
    ): SlackChannelDto {
        val channel = userSetupResourceService.getOwnSlackChannelInfo(
            requesterUsername = authentication.name,
            channelId = channelId
        )
        return SlackChannelDto(id = channel.id, name = channel.name, isPrivate = channel.isPrivate)
    }
}
