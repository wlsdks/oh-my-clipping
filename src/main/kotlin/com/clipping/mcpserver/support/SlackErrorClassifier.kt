package com.clipping.mcpserver.support

/**
 * Slack API 에러 코드를 5가지 카테고리로 분류하는 유틸.
 *
 * Slack Web API 는 70여 종의 에러 코드를 돌려주지만, 발송 루프에서
 * 필요한 조치는 크게 5가지로 묶인다.
 *
 * - [SlackErrorCategory.AUTH]: 인증 실패 — 재시도 중단, CRITICAL 알림
 * - [SlackErrorCategory.SCOPE]: 권한 부족 — 영구 실패, 관리자 안내
 * - [SlackErrorCategory.CHANNEL]: 채널 접근 불가 — 카테고리 일시 비활성 시그널
 * - [SlackErrorCategory.PAYLOAD]: 메시지 payload 문제 — 복구 후 1회 재시도
 * - [SlackErrorCategory.RATE]: 레이트 리밋 — 오케스트레이터 위임
 * - [SlackErrorCategory.TRANSIENT]: 일시 장애 — 오케스트레이터 위임
 * - [SlackErrorCategory.UNKNOWN]: 매핑되지 않은 에러 — 기본적으로 transient 로 취급
 *
 * Slack 공식 문서: https://api.slack.com/methods/chat.postMessage#errors
 */
enum class SlackErrorCategory {
    AUTH,
    SCOPE,
    CHANNEL,
    PAYLOAD,
    RATE,
    TRANSIENT,
    UNKNOWN
}

/**
 * Slack 에러 코드를 카테고리로 분류한다.
 *
 * 코드 매핑은 Slack 공식 문서의 chat.postMessage / conversations.* 공통 에러 코드를
 * 기준으로 한다. null/빈 값은 UNKNOWN 으로 취급해 상위 레이어가 transient 와 동일하게
 * 처리하게 한다.
 */
object SlackErrorClassifier {

    private val AUTH_CODES = setOf(
        "invalid_auth",
        "not_authed",
        "token_expired",
        "token_revoked",
        "account_inactive",
        "no_auth"
    )

    private val SCOPE_CODES = setOf(
        "missing_scope",
        "no_permission",
        "ekm_access_denied",
        "not_allowed_token_type"
    )

    private val CHANNEL_CODES = setOf(
        "channel_not_found",
        "is_archived",
        "not_in_channel",
        "restricted_action",
        "restricted_action_read_only_channel",
        "restricted_action_thread_only_channel",
        "team_access_not_granted",
        "cannot_dm_bot",
        "user_not_found",
        "user_not_in_channel"
    )

    private val PAYLOAD_CODES = setOf(
        "msg_blocks_too_long",
        "invalid_blocks",
        "invalid_blocks_format",
        "no_text",
        "markdown_text_conflict",
        "too_many_attachments",
        "attachment_payload_limit_exceeded",
        "msg_too_long",
        "invalid_arg_name",
        "invalid_arguments"
    )

    private val RATE_CODES = setOf(
        "ratelimited",
        "rate_limited",
        "message_limit_exceeded"
    )

    private val TRANSIENT_CODES = setOf(
        "service_unavailable",
        "internal_error",
        "fatal_error",
        "request_timeout"
    )

    /**
     * 주어진 에러 코드를 카테고리로 분류한다.
     *
     * @param errorCode Slack API 응답의 `error` 필드 값 (null/빈 값 허용)
     * @return 분류 결과. 매핑되지 않은 코드는 [SlackErrorCategory.UNKNOWN]
     */
    fun classify(errorCode: String?): SlackErrorCategory {
        // 빈 값은 미상 에러로 취급한다
        val normalized = errorCode?.trim()?.lowercase().orEmpty()
        if (normalized.isEmpty()) return SlackErrorCategory.UNKNOWN

        return when (normalized) {
            in AUTH_CODES -> SlackErrorCategory.AUTH
            in SCOPE_CODES -> SlackErrorCategory.SCOPE
            in CHANNEL_CODES -> SlackErrorCategory.CHANNEL
            in PAYLOAD_CODES -> SlackErrorCategory.PAYLOAD
            in RATE_CODES -> SlackErrorCategory.RATE
            in TRANSIENT_CODES -> SlackErrorCategory.TRANSIENT
            else -> SlackErrorCategory.UNKNOWN
        }
    }
}
