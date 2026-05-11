package com.clipping.mcpserver.service

import com.clipping.mcpserver.service.port.SlackDeliveryColor

/**
 * Slack 메시지 전송과 연결 확인을 추상화한 포트다.
 */
interface SlackMessageSender {

    /**
     * Slack 연결 확인 결과를 표현한다.
     */
    data class SlackConnectionTestResult(
        val ok: Boolean,
        val botUser: String?,
        val team: String?,
        val channelId: String?,
        val channelName: String?,
        val neededScopes: String?,
        val providedScopes: String?,
        val rawError: String?,
        val warning: String? = null
    )

    /**
     * chat.postMessage / chat.update 전송 결과를 표현한다.
     *
     * 기존 필드(ts, channelId, ok)는 순서를 유지하고, 신규 필드는 끝에 추가해 구조 분해 호환을 보장한다.
     *
     * - [ts]: Slack 메시지 타임스탬프. feature flag 비활성 등으로 실제 전송이 없었다면 빈 문자열이다.
     * - [channelId]: 정규화된 채널 ID. feature flag 비활성 시 입력값 원본이 그대로 들어갈 수 있다.
     * - [ok]: Slack API `ok=true` 여부.
     * - [payloadJson]: 전송된 전체 페이로드 JSON (감사 로그용).
     * - [isNew]: true = 신규 메시지, false = 기존 메시지 업데이트(chat.update).
     * - [fallbackUsed]: payload 에러로 Block Kit 렌더에 실패해 text-only fallback 으로 대체된 경우 true.
     *   호출부는 이 값을 delivery_log 에 기록해 24h 내 반복 fallback 을 탐지할 수 있다.
     */
    data class SendResult(
        val ts: String,
        val channelId: String,
        val ok: Boolean = true,
        val payloadJson: String = "",
        val isNew: Boolean = true,
        val fallbackUsed: Boolean = false,
    )

    /**
     * Slack 채널로 메시지를 전송한다.
     *
     * threadTs를 지정하면 스레드 댓글로 발송되고, replyBroadcast=true면 채널에도 함께 노출된다.
     * metadata는 Slack 메시지 메타데이터 API를 사용해 이벤트 타입과 페이로드를 첨부한다.
     *
     * payload 에러로 Block Kit 렌더에 실패해 text-only fallback 으로 대체된 경우
     * [SendResult.fallbackUsed] 가 true 로 반환된다.
     *
     * feature flag가 꺼져 있으면 no-op SendResult(ts="", channelId=channelId, ok=false)를 반환한다.
     */
    fun sendMessage(
        channelId: String,
        text: String,
        blocks: List<Map<String, Any?>> = emptyList(),
        threadTs: String? = null,
        replyBroadcast: Boolean = false,
        metadata: SlackMetadata? = null,
        color: SlackDeliveryColor? = null,
        botToken: String? = null,
    ): SendResult

    /**
     * 현재 Slack 토큰/채널 설정으로 연결 가능 여부를 확인한다.
     */
    fun testConnection(
        botToken: String?,
        channelId: String?
    ): SlackConnectionTestResult

    /**
     * Socket Mode 앱 토큰으로 WebSocket 연결 가능한지 확인한다.
     */
    data class SlackSocketModeConnectionTestResult(
        val ok: Boolean,
        val appId: String?,
        val socketUrl: String?,
        val rawError: String?,
        val warning: String? = null
    )

    /**
     * 앱 레벨 토큰으로 Slack Socket Mode 핸드셰이크가 가능한지 확인한다.
     */
    fun testSocketModeConnection(
        appLevelToken: String?
    ): SlackSocketModeConnectionTestResult

    /**
     * Slack 채널 단건 조회 결과.
     */
    data class SlackChannel(
        val id: String,
        val name: String,
        val isPrivate: Boolean
    )

    /**
     * conversations.list로 봇이 접근 가능한 채널 목록을 가져온다.
     * channelType: "public_channel" 또는 "private_channel" 단일 값만 허용.
     */
    fun listChannels(
        botToken: String?,
        channelType: String
    ): List<SlackChannel>

    /**
     * conversations.info로 채널 단건 정보를 가져온다.
     * channel_not_found → NotFoundException, not_in_channel → AccessForbiddenException.
     */
    fun getChannelInfo(
        botToken: String?,
        channelId: String
    ): SlackChannel

    /**
     * conversations.members로 채널의 전체 멤버 ID 목록을 가져온다.
     * 결과는 캐싱되며, 서비스 레이어에서 in-memory 필터링에 사용한다.
     * 페이지네이션 상한: MAX_MEMBERS_PAGES(3)까지만 조회 (최대 3,000명).
     *
     * @return 채널 멤버 Slack User ID의 Set
     */
    fun getChannelMembers(
        botToken: String?,
        channelId: String
    ): Set<String>

    /**
     * Slack 멤버 ID로 DM 채널을 열고 채널 ID를 반환한다.
     * conversations.open API를 사용한다.
     *
     * @param botToken 봇 토큰 (null이면 기본 토큰 사용)
     * @param memberId Slack 멤버 ID (U로 시작)
     * @return DM 채널 ID (D로 시작)
     */
    fun openDmChannel(botToken: String?, memberId: String): String

    /**
     * 기존 Slack 메시지의 블록을 업데이트한다.
     * 피드백 버튼 클릭 시 확인 메시지로 교체하는 데 사용한다.
     *
     * @param channelId 메시지가 있는 채널/DM ID
     * @param messageTs 업데이트할 메시지의 타임스탬프
     * @param blocks 교체할 블록 목록 (내부에서 Jackson으로 직렬화한다)
     * @param fallbackText blocks를 표시할 수 없는 클라이언트용 대체 텍스트
     * @param botToken 사용할 봇 토큰 (null이면 기본 토큰 사용)
     * @return 전송 결과 (isNew=false)
     */
    fun updateMessage(
        channelId: String,
        messageTs: String,
        blocks: List<Map<String, Any?>>,
        fallbackText: String,
        color: SlackDeliveryColor? = null,
        botToken: String? = null,
    ): SendResult
}
