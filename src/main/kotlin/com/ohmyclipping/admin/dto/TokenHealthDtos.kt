package com.ohmyclipping.admin.dto

/**
 * 토큰 헬스 상태 조회 응답 DTO (F8).
 *
 * @property slackBot Slack Bot 토큰 상태 (`ok` / `expired` / `scope_mismatch` / `unknown`)
 * @property gemini Gemini API 키 상태 (`ok` / `expired` / `quota_exhausted` / `unknown`)
 * @property ok 모든 토큰이 정상(`ok`)이면 true. 프론트 배너 노출 여부 판단용.
 */
data class TokenHealthResponse(
    val slackBot: String,
    val gemini: String,
    val ok: Boolean
)
