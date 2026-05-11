package com.clipping.mcpserver.admin

import com.clipping.mcpserver.error.ensureValid
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId

/**
 * 날짜 범위 결과 DTO.
 */
data class DateRange(val from: LocalDate, val to: LocalDate)

/**
 * 컨트롤러에서 공통으로 사용하는 날짜 범위 파싱 유틸.
 * from/to 파라미터가 없으면 오늘 기준 defaultDays만큼 이전을 기본값으로 사용한다.
 *
 * @param from 시작 날짜 문자열 (ISO-8601, 선택)
 * @param to 종료 날짜 문자열 (ISO-8601, 선택)
 * @param defaultDays from/to가 없을 때 기본 조회 일수 (기본 30)
 * @param maxDays 최대 허용 일수 (기본 365)
 */
fun resolveDateRange(
    from: String?,
    to: String?,
    defaultDays: Long = 30,
    maxDays: Long = 365
): DateRange {
    val toDate = if (to != null) LocalDate.parse(to) else LocalDate.now(ZoneId.of("Asia/Seoul"))
    val fromDate = if (from != null) LocalDate.parse(from) else toDate.minusDays(defaultDays - 1)
    ensureValid(!toDate.isBefore(fromDate)) { "종료일은 시작일 이후여야 합니다." }
    ensureValid(
        Duration.between(fromDate.atStartOfDay(), toDate.plusDays(1).atStartOfDay()).toDays() <= maxDays
    ) { "조회 기간은 최대 ${maxDays}일까지 가능합니다." }
    return DateRange(fromDate, toDate)
}
