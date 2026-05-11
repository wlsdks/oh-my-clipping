package com.clipping.mcpserver.support

/**
 * Gemini API 에러를 운영 관점의 카테고리로 분류한다.
 *
 * 운영팀은 원인에 따라 조치가 다르므로 (키 재발급 vs 쿼터 증액 vs 일시 대기)
 * `Throwable` 메시지를 소문자 정규화 후 HTTP 상태 코드/키워드로 분류한다.
 *
 * - [GeminiErrorCategory.EXPIRED]: 401/403/invalid-auth 계열 — 관리자가 키를 재발급해야 한다
 * - [GeminiErrorCategory.QUOTA_EXHAUSTED]: 429/quota — 쿼터 증액 또는 다음 윈도우까지 대기
 * - [GeminiErrorCategory.TRANSIENT]: 5xx/timeout/unavailable — 자동 재시도 범주
 * - [GeminiErrorCategory.UNKNOWN]: 매핑 실패 — 알림만 남기고 기본 처리
 */
enum class GeminiErrorCategory {
    EXPIRED,
    QUOTA_EXHAUSTED,
    TRANSIENT,
    UNKNOWN
}

/**
 * Gemini API 에러 분류 유틸.
 *
 * Spring AI `ChatClient`는 HTTP 상태 코드를 메시지 문자열에 포함시키는 경우가 많아,
 * 메시지 문자열 기반 매칭이 가장 안정적이다. 메시지 체인을 cause까지 따라가
 * 중첩된 예외에서도 원인을 찾는다.
 */
object GeminiErrorClassifier {

    // 인증 만료/잘못된 키 시그널. 소문자 비교를 위해 소문자로 유지한다.
    private val EXPIRED_KEYWORDS = listOf(
        "401",
        "403",
        "invalid api key",
        "api key not valid",
        "permission_denied",
        "unauthenticated",
        "unauthorized",
        "authentication failed",
        "invalid_api_key"
    )

    // 쿼터 소진 시그널.
    private val QUOTA_KEYWORDS = listOf(
        "429",
        "quota",
        "resource_exhausted",
        "rate_limit",
        "rate limit",
        "too many requests"
    )

    // 일시 장애 시그널 (재시도 가능 범주).
    private val TRANSIENT_KEYWORDS = listOf(
        "500",
        "502",
        "503",
        "504",
        "unavailable",
        "deadline_exceeded",
        "timeout",
        "timed out"
    )

    /**
     * 주어진 예외를 Gemini 에러 카테고리로 분류한다.
     *
     * @param throwable 분류 대상 예외 (null은 UNKNOWN)
     * @return 분류 결과. 매핑 실패 시 [GeminiErrorCategory.UNKNOWN]
     */
    fun classify(throwable: Throwable?): GeminiErrorCategory {
        if (throwable == null) return GeminiErrorCategory.UNKNOWN

        // cause 체인을 따라 모든 메시지를 모아 하나의 문자열로 정규화한다.
        val haystack = collectMessages(throwable).lowercase()
        if (haystack.isBlank()) return GeminiErrorCategory.UNKNOWN

        // 만료/인증이 쿼터와 동시에 매칭되는 경우는 드물지만, 만료가 우선순위가 높다.
        if (EXPIRED_KEYWORDS.any { haystack.contains(it) }) {
            return GeminiErrorCategory.EXPIRED
        }
        if (QUOTA_KEYWORDS.any { haystack.contains(it) }) {
            return GeminiErrorCategory.QUOTA_EXHAUSTED
        }
        if (TRANSIENT_KEYWORDS.any { haystack.contains(it) }) {
            return GeminiErrorCategory.TRANSIENT
        }
        return GeminiErrorCategory.UNKNOWN
    }

    private fun collectMessages(throwable: Throwable): String {
        val sb = StringBuilder()
        var current: Throwable? = throwable
        var guard = 0
        // 순환 참조를 방지하기 위해 최대 깊이를 둔다.
        while (current != null && guard < 8) {
            current.message?.let { sb.append(it).append(' ') }
            current = current.cause
            guard++
        }
        return sb.toString()
    }
}
