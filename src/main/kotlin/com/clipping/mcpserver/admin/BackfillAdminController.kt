package com.clipping.mcpserver.admin

import com.clipping.mcpserver.service.dto.BackfillApplyRequest
import com.clipping.mcpserver.service.dto.BackfillApplyResponse
import com.clipping.mcpserver.service.dto.BackfillPreviewResponse
import com.clipping.mcpserver.service.BackfillService
import jakarta.validation.Valid
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * RSS 소스 → 조직 링크 백필 어드민 API.
 *
 * - `GET  /api/admin/organizations/backfill/preview` — 후보 미리보기 (읽기 전용)
 * - `POST /api/admin/organizations/backfill/apply`   — 후보 적용 (쓰기)
 */
@RestController
@RequestMapping("/api/admin/organizations/backfill")
class BackfillAdminController(
    private val service: BackfillService,
) {

    /**
     * RSS 소스 기반 기업 매칭 후보를 미리보기 한다.
     *
     * @param confidence 반환 기준 최소 confidence (high/medium/low, 기본: "high")
     * @param includeMedium confidence=high 일 때 medium 을 함께 포함 여부 (기본: false)
     * @param categoryId 특정 카테고리 필터 (null 이면 전체)
     */
    @GetMapping("/preview")
    fun preview(
        @RequestParam(required = false) confidence: String?,
        @RequestParam(required = false) includeMedium: Boolean?,
        @RequestParam(required = false) categoryId: String?,
    ): BackfillPreviewResponse {
        return service.previewCompanyBackfill(
            confidence = confidence ?: "high",
            includeMedium = includeMedium ?: false,
            categoryId = categoryId,
        )
    }

    /**
     * 선택된 소스 ID 목록에 대해 조직 upsert + 카테고리 링크를 적용한다.
     *
     * @param request 적용할 sourceId 목록 (최대 100개)
     * @param authentication 현재 인증 주체 (로그 목적)
     */
    @PostMapping("/apply")
    fun apply(
        @Valid @RequestBody request: BackfillApplyRequest,
        authentication: Authentication,
    ): BackfillApplyResponse {
        return service.applyCompanyBackfill(
            candidateIds = request.candidateIds,
            actorName = authentication.name,
        )
    }
}
