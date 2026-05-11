package com.ohmyclipping.admin

import com.ohmyclipping.error.InvalidInputException
import java.time.Instant
import java.time.format.DateTimeParseException

/**
 * 낙관적 잠금용 기준 시각 문자열을 파싱합니다.
 */
internal fun parseExpectedUpdatedAt(raw: String?, fieldName: String): Instant? {
    val normalized = raw?.trim().orEmpty()
    if (normalized.isBlank()) return null
    return try {
        Instant.parse(normalized)
    } catch (_: DateTimeParseException) {
        throw InvalidInputException(
            "$fieldName 값이 올바르지 않습니다. ISO-8601 UTC 형식(예: 2026-03-02T12:34:56Z)으로 입력해주세요."
        )
    }
}
