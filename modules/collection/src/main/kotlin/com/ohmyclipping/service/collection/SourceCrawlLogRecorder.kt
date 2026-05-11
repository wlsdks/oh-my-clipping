package com.ohmyclipping.service.collection

import com.ohmyclipping.model.SourceCrawlLog
import com.ohmyclipping.store.SourceCrawlLogStore
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.dao.DataAccessException
import org.springframework.stereotype.Component
import java.net.SocketTimeoutException

private val log = KotlinLogging.logger {}

/**
 * RSS 소스별 크롤 시도 결과를 관측 로그로 기록한다.
 *
 * 로그 저장 실패는 수집 자체의 실패가 아니므로 DB 접근 예외만 좁게 흡수한다.
 */
@Component
class SourceCrawlLogRecorder(
    private val crawlLogStore: SourceCrawlLogStore
) {

    companion object {
        /** source_crawl_log.error_message 로그 길이 상한. 컬럼은 TEXT지만 대시보드 가독성을 위해 500자로 제한한다. */
        private const val MAX_CRAWL_ERROR_MESSAGE_LENGTH = 500
    }

    fun recordSuccess(
        sourceId: String,
        durationMs: Long,
        articlesFound: Int
    ) {
        record(
            sourceId = sourceId,
            success = true,
            durationMs = durationMs,
            articlesFound = articlesFound,
            errorMessage = null
        )
    }

    fun recordFailure(
        sourceId: String,
        durationMs: Long,
        error: Throwable
    ) {
        record(
            sourceId = sourceId,
            success = false,
            durationMs = durationMs,
            articlesFound = 0,
            errorMessage = classify(error)
        )
    }

    internal fun classify(error: Throwable): String {
        val rawMessage = error.message?.trim().orEmpty()
        val tag = when (error) {
            is SocketTimeoutException -> "TIMEOUT"
            is java.net.UnknownHostException -> "DNS_FAIL"
            is com.rometools.rome.io.ParsingFeedException -> "PARSE_FAIL"
            is java.io.IOException -> "HTTP_ERROR"
            else -> "CRAWL_FAIL"
        }
        return if (rawMessage.isBlank()) tag else "$tag: $rawMessage"
    }

    private fun record(
        sourceId: String,
        success: Boolean,
        durationMs: Long,
        articlesFound: Int,
        errorMessage: String?
    ) {
        try {
            crawlLogStore.save(
                SourceCrawlLog(
                    sourceId = sourceId,
                    success = success,
                    errorMessage = errorMessage?.take(MAX_CRAWL_ERROR_MESSAGE_LENGTH),
                    responseTimeMs = durationMs.toInt().coerceAtLeast(0),
                    articlesFound = articlesFound
                )
            )
        } catch (e: DataAccessException) {
            log.warn { "Failed to persist source_crawl_log for source=$sourceId: ${e.message}" }
        }
    }
}
