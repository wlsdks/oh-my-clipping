package com.ohmyclipping.service

import com.ohmyclipping.error.ConflictException
import com.ohmyclipping.error.InvalidInputException
import com.ohmyclipping.error.NotFoundException
import com.ohmyclipping.error.ensureValid
import com.ohmyclipping.model.BlockedSlackChannel
import com.ohmyclipping.service.dto.admin.SlackChannelDto
import com.ohmyclipping.service.dto.admin.SlackChannelListResponse
import com.ohmyclipping.store.AdminUserStore
import com.ohmyclipping.store.AuditLogStore
import com.ohmyclipping.store.BlockedSlackChannelStore
import com.ohmyclipping.support.SlackChannelIdNormalizer
import com.ohmyclipping.support.SlackMentionGuard
import org.springframework.stereotype.Service

/**
 * 관리자 Slack 채널 차단 관리 서비스.
 * 차단 CRUD와 감사 로그 기록을 담당한다.
 */
@Service
class AdminBlockedSlackChannelService(
    private val blockedSlackChannelStore: BlockedSlackChannelStore,
    private val adminUserStore: AdminUserStore,
    private val auditLogStore: AuditLogStore,
    private val slackMessageSender: SlackMessageSender,
    private val runtimeSettingService: RuntimeSettingService
) {

    companion object {
        /** 차단 사유 허용 최대 길이 (trim 후 기준). */
        const val REASON_MAX_LENGTH = 200
    }

    /** 차단된 채널 전체 목록을 최신순으로 반환한다. */
    fun findAll(): List<BlockedSlackChannel> =
        blockedSlackChannelStore.findAll()

    /**
     * 관리자용 Slack 채널 목록 조회.
     * 사용자 멤버십 필터 없이 봇이 접근 가능한 전체 채널을 반환한다.
     * 이미 차단된 채널은 제외하지 않는다 (프론트에서 필터).
     */
    fun listAvailableChannels(channelType: String): SlackChannelListResponse {
        ensureValid(channelType in listOf("public_channel", "private_channel")) {
            "channelType은 public_channel 또는 private_channel만 허용합니다."
        }
        val runtime = runtimeSettingService.current()
        val botToken = runtime.slackBotToken.takeIf { it.isNotBlank() }
        val channels = slackMessageSender.listChannels(botToken = botToken, channelType = channelType)
        return SlackChannelListResponse(
            channels = channels.map { SlackChannelDto(id = it.id, name = it.name, isPrivate = it.isPrivate) }
        )
    }

    /**
     * 채널을 차단 목록에 추가한다.
     * 이미 차단된 채널이면 ConflictException, ID 형식이 올바르지 않으면 InvalidInputException을 발생시킨다.
     * 차단 사유는 trim 후 200자 이하여야 한다.
     */
    fun block(
        adminUsername: String,
        channelId: String,
        channelName: String,
        isPrivate: Boolean = false,
        reason: String? = null
    ): BlockedSlackChannel {
        // 채널 ID를 SlackChannelIdNormalizer로 정규화한다 (URL/#channel/ID:Cxxx 형태 지원).
        val normalizedId = requireValidChannelId(channelId)
        // 사유 길이 검증 — trim 후 허용 최대치를 초과하면 전체 요청을 거부한다.
        val normalizedReason = requireValidReason(reason)
        // 중복 차단을 방지한다.
        if (blockedSlackChannelStore.existsByChannelId(normalizedId)) {
            throw ConflictException("이미 차단된 채널입니다: $normalizedId")
        }
        val admin = adminUserStore.findByUsername(adminUsername)
            ?: throw NotFoundException("관리자를 찾을 수 없습니다.")

        val trimmedName = channelName.trim()
        // 차단 레코드를 저장한다. blockedByUserId에는 UI 표시를 위해 username을 저장한다.
        val blocked = blockedSlackChannelStore.save(
            BlockedSlackChannel(
                id = "", channelId = normalizedId,
                channelName = trimmedName, isPrivate = isPrivate, blockedByUserId = admin.username,
                reason = normalizedReason
            )
        )
        // 감사 로그를 남긴다.
        auditLogStore.log(
            actorId = admin.id, actorName = admin.username,
            action = "SLACK_CHANNEL_BLOCKED", targetType = "SLACK_CHANNEL",
            targetId = normalizedId, targetName = trimmedName
        )
        return blocked
    }

    /**
     * 채널 차단을 해제한다.
     * 차단되지 않은 채널이면 NotFoundException을 발생시킨다.
     */
    fun unblock(adminUsername: String, channelId: String) {
        // 채널 ID를 SlackChannelIdNormalizer로 정규화한다.
        val normalizedId = requireValidChannelId(channelId)
        // 차단 해제를 시도한다.
        if (!blockedSlackChannelStore.deleteByChannelId(normalizedId)) {
            throw NotFoundException("차단된 채널을 찾을 수 없습니다: $normalizedId")
        }
        // 감사 로그를 남긴다.
        val admin = adminUserStore.findByUsername(adminUsername)
        if (admin != null) {
            auditLogStore.log(
                actorId = admin.id, actorName = admin.username,
                action = "SLACK_CHANNEL_UNBLOCKED", targetType = "SLACK_CHANNEL",
                targetId = normalizedId
            )
        }
    }

    /**
     * Slack 채널 입력을 표준 채널 ID로 정규화한다.
     * 정규화에 실패하면 한국어 안내 문구와 함께 [InvalidInputException]을 던진다.
     */
    private fun requireValidChannelId(raw: String): String =
        SlackChannelIdNormalizer.normalize(raw)
            ?: throw InvalidInputException("채널 ID 형식이 올바르지 않습니다 (예: C0123456789)")

    /**
     * 차단 사유를 trim 후 검증한다. 빈 입력은 null로 통일하고,
     * 허용 최대 길이를 넘으면 전체 요청을 거부한다.
     */
    private fun requireValidReason(raw: String?): String? {
        val trimmed = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        ensureValid(trimmed.length <= REASON_MAX_LENGTH) {
            "차단 사유는 ${REASON_MAX_LENGTH}자 이하로 입력해 주세요"
        }
        // 차단 사유가 감사 로그나 향후 알림에 노출될 수 있으므로 Slack 멘션 패턴을 중립화한다.
        return SlackMentionGuard.neutralize(trimmed)
    }
}
