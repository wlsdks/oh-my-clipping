package com.ohmyclipping.user

import com.ohmyclipping.error.InvalidInputException
import com.ohmyclipping.service.UserEventService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.net.URI

private val log = KotlinLogging.logger {}

/**
 * 기사 클릭 추적 컨트롤러.
 *
 * 두 경로 제공:
 *   - GET /api/track/click?sid=X&url=Y           : 기존 쿼리파라미터 경로 (backward compat, source 태그 없음)
 *   - GET /api/track/click/slack/{sid}?url=Y     : 신규 path-based Slack 경로 (source="slack" 기록)
 *
 * Slack digest 메시지는 신규 경로를 사용해 "다른 경로 클릭"과 구분 가능하게 한다.
 * Path 기반이라 URL 복사/붙여넣기/북마크에도 source 태깅이 유지된다.
 */
@RestController
@RequestMapping("/api/track")
class ArticleClickTrackController(
    private val userEventService: UserEventService
) {

    /**
     * 기존 쿼리파라미터 기반 경로. source 태그 없이 기록한다 (backward compat).
     * 과거 발송된 Slack 메시지의 URL이 이 경로를 사용하고 있으므로 유지.
     *
     * @param summaryId 요약 ID (클릭 추적용)
     * @param targetUrl 리다이렉트 대상 원본 기사 URL
     * @param authentication 인증 정보 (Slack 사용자는 미인증이므로 nullable)
     * @return 302 Found 리다이렉트 응답
     * @throws InvalidInputException targetUrl이 http/https가 아닌 경우
     */
    @GetMapping("/click")
    fun trackClick(
        @RequestParam("sid") summaryId: String,
        @RequestParam("url") targetUrl: String,
        authentication: Authentication?
    ): ResponseEntity<Void> = performClickTrack(summaryId, targetUrl, authentication, source = null)

    /**
     * 신규 path-based 경로. Slack 다이제스트 버튼이 사용하며, source="slack"으로 태깅된다.
     * 쿼리파라미터 경로와 달리 path에 녹아있어 URL 복사/붙여넣기에도 source가 유지된다.
     *
     * @param summaryId 요약 ID (클릭 추적용, path variable)
     * @param targetUrl 리다이렉트 대상 원본 기사 URL
     * @param authentication 인증 정보 (Slack 사용자는 미인증이므로 nullable)
     * @return 302 Found 리다이렉트 응답
     * @throws InvalidInputException targetUrl이 http/https가 아닌 경우
     */
    @GetMapping("/click/slack/{sid}")
    fun trackClickFromSlack(
        @PathVariable("sid") summaryId: String,
        @RequestParam("url") targetUrl: String,
        authentication: Authentication?
    ): ResponseEntity<Void> = performClickTrack(summaryId, targetUrl, authentication, source = "slack")

    /** 두 경로가 공유하는 실제 처리 로직. */
    private fun performClickTrack(
        summaryId: String,
        targetUrl: String,
        authentication: Authentication?,
        source: String?
    ): ResponseEntity<Void> {
        // Open redirect 방지: http/https 프로토콜만 허용한다.
        val safeUrl = validateRedirectUrl(targetUrl)

        // 클릭 이벤트를 기록한다. 실패해도 리다이렉트는 보장한다.
        val userId = authentication?.name ?: "anonymous"
        try {
            userEventService.saveClick(userId, summaryId, safeUrl, source)
        } catch (e: Exception) {
            log.warn(e) { "Click tracking failed for summary=$summaryId source=$source, proceeding with redirect" }
        }

        return ResponseEntity.status(HttpStatus.FOUND)
            .header("Location", safeUrl)
            .header("Cache-Control", "no-cache, no-store")
            .build()
    }

    /**
     * URL이 http 또는 https 스킴인지 검증하여 open redirect를 방지한다.
     * javascript:, data:, file: 등 위험한 스킴은 차단한다.
     */
    private fun validateRedirectUrl(url: String): String {
        val safeUrl = url.trim()
        val parsed = runCatching { URI(safeUrl) }.getOrNull()
            ?: throw InvalidInputException("Invalid redirect URL")
        // Open redirect 목적지에는 웹 URL만 허용하고, 헤더 주입 가능성이 있는 제어 문자는 차단한다.
        val scheme = parsed.scheme?.lowercase()
        if ((scheme != "http" && scheme != "https") || parsed.host.isNullOrBlank() || hasControlCharacter(safeUrl)) {
            throw InvalidInputException("Invalid redirect URL scheme")
        }
        return safeUrl
    }

    /** HTTP 헤더에 들어갈 URL 값에 CR/LF 같은 제어 문자가 섞였는지 확인한다. */
    private fun hasControlCharacter(value: String): Boolean =
        value.any { it.code < 0x20 || it.code == 0x7F }
}
