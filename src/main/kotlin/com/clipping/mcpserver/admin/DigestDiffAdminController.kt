package com.clipping.mcpserver.admin

import com.clipping.mcpserver.error.InvalidInputException
import com.clipping.mcpserver.service.digest.DigestDiffService
import com.clipping.mcpserver.support.PaginationUtils
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

/**
 * Shadow mode diff 기록 조회 API.
 *
 * /api/admin/digest-diff 경로에서 digest_diff_log 테이블 내용을 페이지네이션으로 반환한다.
 * 보안은 SecurityConfig 의 /api/admin/... 경로 규칙으로 처리.
 * store 계층 직접 의존 금지 — DigestDiffService 를 경유한다 (Clean Architecture §2.1).
 */
@RestController
@RequestMapping("/api/admin/digest-diff")
class DigestDiffAdminController(
    private val digestDiffService: DigestDiffService,
) {

    /**
     * Diff 기록 목록을 페이지네이션으로 반환한다.
     *
     * @param categoryId 조회 대상 카테고리 ID (필수). 미입력 시 400.
     * @param from 시작일 (선택, yyyy-MM-dd). 미입력 시 오늘 기준 30일 전.
     * @param to 종료일 (선택, yyyy-MM-dd). 미입력 시 오늘.
     * @param page 페이지 번호 (0-based, 기본 0). 음수는 0 으로 강제.
     * @param size 페이지 크기 (기본 50, 최대 200)
     */
    @GetMapping
    fun list(
        @RequestParam(required = false) categoryId: String?,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        from: LocalDate?,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        to: LocalDate?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
    ): DigestDiffPageResponse {
        // categoryId 는 필수 파라미터 — 빈 값도 거부한다.
        if (categoryId.isNullOrBlank()) {
            throw InvalidInputException("categoryId 파라미터는 필수입니다.")
        }

        val safeSize = size.coerceIn(1, 200)
        // 음수 page 는 0 으로 강제하여 drop(-n) 이 전체 반환하는 묵시적 동작을 방지한다.
        val safePage = page.coerceAtLeast(0)
        val offset = PaginationUtils.safeOffset(safePage, safeSize)
        // 날짜 기본값 처리와 DB 페이지네이션은 서비스 계층에 위임한다.
        val paged = digestDiffService.listForCategory(categoryId, from, to, offset, safeSize)
        val totalElements = digestDiffService.countForCategory(categoryId, from, to)

        return DigestDiffPageResponse(
            content = paged.map { it.toResponse() },
            totalElements = totalElements,
            page = safePage,
            size = safeSize,
        )
    }
}
