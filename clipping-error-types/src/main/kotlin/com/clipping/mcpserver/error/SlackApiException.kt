package com.clipping.mcpserver.error

/**
 * Slack API 호출 실패를 나타내는 sealed 예외 계층.
 *
 * 각 서브클래스는 Slack API의 특정 오류 조건을 표현하며,
 * when 표현식으로 소진(exhaustive) 처리를 강제한다.
 */
sealed class SlackApiException(message: String, cause: Throwable? = null) : RuntimeException(message, cause) {

    /** Slack API rate limit 초과. retryAfterSec 이후 재시도해야 한다. */
    class RateLimited(val retryAfterSec: Int) :
        SlackApiException("Slack API rate limited, retry after ${retryAfterSec}s")

    /** 대상 채널 ID를 찾을 수 없음. 채널이 삭제됐거나 봇이 미초대된 경우. */
    class ChannelNotFound(val channelId: String) :
        SlackApiException("Slack channel not found: $channelId")

    /** 봇 토큰이 유효하지 않거나 만료된 경우. */
    class InvalidAuth : SlackApiException("Slack invalid auth (bot token invalid or expired)")

    /** 일시적 네트워크/서버 오류. 재시도 대상. */
    class Transient(cause: Throwable) : SlackApiException("Slack API transient failure", cause)
}
