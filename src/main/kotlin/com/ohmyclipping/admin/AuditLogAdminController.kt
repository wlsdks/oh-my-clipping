package com.ohmyclipping.admin

import com.ohmyclipping.admin.dto.AuditLogEntryResponse
import com.ohmyclipping.admin.dto.AuditLogFiltersResponse
import com.ohmyclipping.admin.dto.AuditLogPageResponse
import com.ohmyclipping.service.AuditLogAdminService
import com.ohmyclipping.support.PaginationUtils
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 감사 로그 조회 API를 제공하는 관리자 컨트롤러.
 * 페이지네이션 목록 조회와 필터 드롭다운 옵션 조회를 담당한다.
 */
@RestController
@RequestMapping("/api/admin/audit-log")
class AuditLogAdminController(
    private val auditLogAdminService: AuditLogAdminService
) {

    /**
     * 감사 로그를 필터 조건으로 페이지네이션 조회한다.
     *
     * @param actorId 행위자 ID 필터 (선택)
     * @param action 행위 종류 필터 (선택)
     * @param targetType 대상 유형 필터 (선택)
     * @param from 시작 날짜 필터 (선택, yyyy-MM-dd)
     * @param to 종료 날짜 필터 (선택, yyyy-MM-dd)
     * @param page 페이지 번호 (기본 0)
     * @param size 페이지 크기 (기본 30)
     */
    @GetMapping
    fun list(
        @RequestParam(required = false) actorId: String?,
        @RequestParam(required = false) action: String?,
        @RequestParam(required = false) targetType: String?,
        @RequestParam(required = false) from: String?,
        @RequestParam(required = false) to: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "30") size: Int
    ): AuditLogPageResponse {
        val safeSize = size.coerceIn(1, 200)
        // 음수 페이지가 DB OFFSET 음수로 전달되지 않도록 첫 페이지로 보정한다.
        val safePage = page.coerceAtLeast(0)
        val offset = PaginationUtils.safeOffset(safePage, safeSize)

        // 날짜 문자열을 KST 기준 Instant로 변환한다.
        val fromInstant = from?.let { parseToStartOfDay(it) }
        val toInstant = to?.let { parseToStartOfNextDay(it) }

        // 필터 조건으로 감사 로그를 조회한다.
        val entries = auditLogAdminService.findAll(
            actorId = actorId,
            action = action,
            targetType = targetType,
            from = fromInstant,
            to = toInstant,
            offset = offset,
            limit = safeSize
        )

        // 총 건수를 조회한다.
        val totalCount = auditLogAdminService.countAll(
            actorId = actorId,
            action = action,
            targetType = targetType,
            from = fromInstant,
            to = toInstant
        )

        return AuditLogPageResponse(
            content = entries.map { e -> toResponse(e) },
            totalCount = totalCount,
            page = safePage,
            size = safeSize
        )
    }

    /**
     * 감사 로그 필터 드롭다운에 사용할 옵션을 반환한다.
     */
    @GetMapping("/filters")
    fun filters(): AuditLogFiltersResponse {
        return AuditLogFiltersResponse(
            actions = auditLogAdminService.getDistinctActions(),
            targetTypes = auditLogAdminService.getDistinctTargetTypes()
        )
    }

    /** AuditLogEntry를 API 응답 DTO로 변환한다. */
    private fun toResponse(
        e: com.ohmyclipping.store.AuditLogStore.AuditLogEntry
    ) = AuditLogEntryResponse(
        id = e.id,
        actorId = e.actorId,
        actorName = e.actorName,
        action = e.action,
        targetType = e.targetType,
        targetId = e.targetId,
        targetName = e.targetName,
        detail = e.detail,
        createdAt = e.createdAt.toString()
    )

    /** yyyy-MM-dd 문자열을 해당 날짜 00:00 KST Instant로 변환한다. */
    private fun parseToStartOfDay(value: String): Instant? {
        return runCatching {
            LocalDate.parse(value).atStartOfDay(KST).toInstant()
        }.getOrNull()
    }

    /** yyyy-MM-dd 문자열을 다음 날 00:00 KST Instant로 변환한다 (exclusive). */
    private fun parseToStartOfNextDay(value: String): Instant? {
        return runCatching {
            LocalDate.parse(value).plusDays(1).atStartOfDay(KST).toInstant()
        }.getOrNull()
    }

    private companion object {
        val KST: ZoneId = ZoneId.of("Asia/Seoul")
    }
}
