package com.ohmyclipping.admin

import com.ohmyclipping.config.RedisRateLimitService
import com.ohmyclipping.error.InvalidInputException
import com.ohmyclipping.error.RateLimitExceededException
import com.ohmyclipping.error.ensureValid
import com.ohmyclipping.service.dto.ClientErrorReport
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange

private val log = KotlinLogging.logger {}

/**
 * 프론트엔드 ErrorBoundary에서 포착한 렌더링 예외를 수집하는 컨트롤러.
 *
 * 목적은 배포 직후 크래시를 조기에 탐지하기 위한 관찰 창구다.
 * 저장소에는 기록하지 않고, stdout warn 로그로만 남긴다.
 * 악용을 막기 위해 인증된 사용자만 호출할 수 있고 IP 기준 분당 30회로 제한한다.
 */
@RestController
@RequestMapping("/api/client-errors")
class ClientErrorController(
    private val redisRateLimitService: RedisRateLimitService
) {

    /**
     * 클라이언트 에러 보고를 수신한다.
     *
     * 입력 제약:
     * - message: 필수, 1~2000자
     * - stack/componentStack: 선택, 각 10000자 이하
     * - url/userAgent: 선택, 각 500자 이하
     * - reactErrorCode: 선택, 50자 이하
     * - tags: 선택, 최대 20 entry, key/value 각 100자 이하
     *
     * 실패 조건:
     * - 입력 검증 실패 → [InvalidInputException] (400)
     * - IP 기준 분당 30회 초과 → [RateLimitExceededException] (429)
     *
     * 성공 시 HTTP 202 Accepted, 본문 없음.
     */
    @PostMapping
    fun reportClientError(
        @RequestBody report: ClientErrorReport,
        exchange: ServerWebExchange
    ): ResponseEntity<Void> {
        // 1) 남용 방지를 위해 IP 기준으로 분당 허용 횟수를 제한한다.
        enforceRateLimit(exchange)
        // 2) 길이/개수 제한을 검증해 로그 폭주를 막는다.
        validatePayload(report)
        // 3) 감사 성격으로 warn 로그 한 줄만 남긴다 — DB 저장은 하지 않는다.
        logReport(report)
        return ResponseEntity.status(HttpStatus.ACCEPTED).build()
    }

    private fun enforceRateLimit(exchange: ServerWebExchange) {
        val clientIp = exchange.request.remoteAddress?.address?.hostAddress ?: "unknown"
        val limited = redisRateLimitService.isRateLimited(
            key = "$KEY_PREFIX$clientIp",
            maxRequests = MAX_REPORTS_PER_MINUTE,
            windowSeconds = WINDOW_SECONDS
        )
        if (limited) {
            throw RateLimitExceededException(
                message = "에러 보고 요청이 너무 많아요. 잠시 후 다시 시도해 주세요 (분당 $MAX_REPORTS_PER_MINUTE 회 제한).",
                retryAfterSeconds = WINDOW_SECONDS
            )
        }
    }

    private fun validatePayload(report: ClientErrorReport) {
        // message는 필수이며 비어있지 않아야 한다.
        val trimmedMessage = report.message.trim()
        ensureValid(trimmedMessage.isNotEmpty()) { "에러 메시지가 비어있습니다." }
        ensureValid(trimmedMessage.length <= MESSAGE_MAX_CHARS) {
            "에러 메시지가 너무 깁니다 (최대 $MESSAGE_MAX_CHARS 자)."
        }
        report.stack?.let {
            ensureValid(it.length <= STACK_MAX_CHARS) { "stack 문자열이 너무 깁니다 (최대 $STACK_MAX_CHARS 자)." }
        }
        report.componentStack?.let {
            ensureValid(it.length <= STACK_MAX_CHARS) {
                "componentStack 문자열이 너무 깁니다 (최대 $STACK_MAX_CHARS 자)."
            }
        }
        report.url?.let {
            ensureValid(it.length <= URL_MAX_CHARS) { "url이 너무 깁니다 (최대 $URL_MAX_CHARS 자)." }
        }
        report.userAgent?.let {
            ensureValid(it.length <= URL_MAX_CHARS) { "userAgent가 너무 깁니다 (최대 $URL_MAX_CHARS 자)." }
        }
        report.reactErrorCode?.let {
            ensureValid(it.length <= REACT_CODE_MAX_CHARS) {
                "reactErrorCode가 너무 깁니다 (최대 $REACT_CODE_MAX_CHARS 자)."
            }
        }
        report.tags?.let { validateTags(it) }
    }

    private fun validateTags(tags: Map<String, String>) {
        ensureValid(tags.size <= TAGS_MAX_ENTRIES) {
            "tags 항목 수가 너무 많습니다 (최대 $TAGS_MAX_ENTRIES 개)."
        }
        tags.forEach { (key, value) ->
            ensureValid(key.length <= TAG_ENTRY_MAX_CHARS) {
                "tags key가 너무 깁니다 (최대 $TAG_ENTRY_MAX_CHARS 자)."
            }
            ensureValid(value.length <= TAG_ENTRY_MAX_CHARS) {
                "tags value가 너무 깁니다 (최대 $TAG_ENTRY_MAX_CHARS 자)."
            }
        }
    }

    private fun logReport(report: ClientErrorReport) {
        // 구조화된 key=value 형식으로 남긴다 — 후속 파서/알림이 인식하기 쉽다.
        log.warn {
            buildString {
                append("[client-error]")
                append(" reactCode=").append(report.reactErrorCode ?: "-")
                append(" url=").append(report.url ?: "-")
                append(" message=").append(report.message.take(MESSAGE_MAX_CHARS))
                report.stack?.let { append(" stack=").append(it.take(STACK_LOG_PREVIEW)) }
                report.componentStack?.let {
                    append(" componentStack=").append(it.take(STACK_LOG_PREVIEW))
                }
                append(" ua=").append(report.userAgent ?: "-")
                report.tags?.let { append(" tags=").append(it.toString()) }
            }
        }
    }

    companion object {
        /** IP 기준 분당 최대 보고 횟수. 초과 시 429 반환. */
        private const val MAX_REPORTS_PER_MINUTE = 30
        private const val WINDOW_SECONDS = 60L
        private const val KEY_PREFIX = "rl:client-errors:"

        private const val MESSAGE_MAX_CHARS = 2000
        private const val STACK_MAX_CHARS = 10000
        private const val URL_MAX_CHARS = 500
        private const val REACT_CODE_MAX_CHARS = 50
        private const val TAGS_MAX_ENTRIES = 20
        private const val TAG_ENTRY_MAX_CHARS = 100

        /** 로그에 남길 스택 프리뷰 길이 — 너무 길면 로그 라인을 깨뜨린다. */
        private const val STACK_LOG_PREVIEW = 2000
    }
}
