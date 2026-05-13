package com.ohmyclipping.mcp

import com.ohmyclipping.error.RateLimitExceededException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

/**
 * MCP 전용 JSON 직렬화 및 에러 래핑 유틸.
 *
 * 기존 [com.ohmyclipping.tool.ToolResponse]의 safeToolCall과 달리,
 * JSON-RPC 2.0 스타일의 에러 코드(`code`)를 포함하여
 * MCP 클라이언트가 에러 유형을 프로그래밍적으로 구분할 수 있게 한다.
 */

@PublishedApi
internal val mapper = jacksonObjectMapper().apply {
    findAndRegisterModules()
}

/** 임의 객체를 MCP 응답용 JSON 문자열로 직렬화한다. */
fun Any.toMcpJson(): String = mapper.writeValueAsString(this)

/**
 * MCP 툴 호출 래퍼.
 *
 * [block] 실행 결과를 JSON으로 직렬화하고,
 * 예외 발생 시 [McpErrorCode]로 매핑한 에러 객체를 반환한다.
 *
 * @param block 실행할 서비스 로직
 * @return 성공 시 결과 JSON, 실패 시 `{"error": {"code": ..., "message": ...}}`
 */
inline fun <T : Any> mcpToolCall(block: () -> T): String = try {
    mapper.writeValueAsString(block())
} catch (e: Exception) {
    val code = McpErrorCode.from(e)
    val errorMap = mutableMapOf<String, Any>(
        "code" to code.code,
        "type" to code.name,
        "message" to safeMcpErrorMessage(code, e),
        "retryable" to isRetryableMcpError(code),
    )
    // RateLimit 예외는 클라이언트가 재시도 간격을 계산할 수 있도록 retryAfterSeconds와
    // 절대 재시도 시각(retryAt, ISO-8601 UTC)을 노출한다. LLM 이 `Retry-After` 해석 없이
    // 바로 다음 시도 시점을 알 수 있게 해 자동 재시도 loop 를 막는다.
    if (e is RateLimitExceededException) {
        errorMap["retryAfterSeconds"] = e.retryAfterSeconds
        e.retryAt?.let { errorMap["retryAt"] = it.toString() }
    }
    mapper.writeValueAsString(mapOf("error" to errorMap))
}

@PublishedApi
internal fun safeMcpErrorMessage(code: McpErrorCode, e: Exception): String =
    when (code) {
        McpErrorCode.INTERNAL_ERROR -> "Internal error"
        else -> e.message?.let(::redactMcpErrorMessage) ?: "Internal error"
    }

@PublishedApi
internal fun redactMcpErrorMessage(message: String): String {
    if (!MCP_ERROR_SECRET_PATTERN.containsMatchIn(message)) return message
    return MCP_ERROR_SECRET_PATTERN.replace(message) { match ->
        val keyName = match.groupValues[1]
        "$keyName=***REDACTED***"
    }
}

@PublishedApi
internal fun isRetryableMcpError(code: McpErrorCode): Boolean =
    when (code) {
        McpErrorCode.RATE_LIMITED,
        McpErrorCode.DEPENDENCY_FAILURE -> true
        else -> false
    }

@PublishedApi
internal val MCP_ERROR_SECRET_PATTERN: Regex = Regex(
    "(password|passwd|secret|token|apikey|api_key|authorization|bearer|credential|credentials|privatekey|private_key)" +
        "([\"':= ]+)" +
        "(?:bearer\\s+)?" +
        "[^\"',\\s]+",
    RegexOption.IGNORE_CASE,
)
