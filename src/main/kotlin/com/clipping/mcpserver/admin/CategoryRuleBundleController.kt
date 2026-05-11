package com.clipping.mcpserver.admin

import com.clipping.mcpserver.admin.dto.RuleBundleRequest
import com.clipping.mcpserver.service.CategoryRuleBundleService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/**
 * 카테고리 룰 번들 원자적 업데이트 엔드포인트.
 *
 * CategoryRuleEditModal 이 저장 버튼을 누를 때 사용한다.
 * 보안은 SecurityConfig 의 /api/admin 경로 규칙으로 처리된다.
 * 설계 배경: docs/ADR.md ADR-032 참고.
 */
@RestController
class CategoryRuleBundleController(
    private val service: CategoryRuleBundleService,
) {

    /**
     * 카테고리의 룰 번들(excludeEventTypes + includeKeywords + organizationIds
     * + accountBasedDigestEnabled + shadowModeEnabled)을 단일 트랜잭션으로 저장한다.
     *
     * @param categoryId 대상 카테고리 ID
     * @param request 저장할 룰 번들 요청 본문
     * @param authentication Spring Security 인증 컨텍스트 — actorId 추출에 사용
     */
    @PutMapping("/api/admin/categories/{categoryId}/rule-bundle")
    fun updateRuleBundle(
        @PathVariable categoryId: String,
        @Valid @RequestBody request: RuleBundleRequest,
        authentication: Authentication,
    ): ResponseEntity<Void> {
        service.updateRuleBundle(
            categoryId = categoryId,
            excludeEventTypes = request.excludeEventTypes,
            includeKeywords = request.includeKeywords,
            organizationIds = request.organizationIds,
            accountBasedDigestEnabled = request.accountBasedDigestEnabled,
            shadowModeEnabled = request.shadowModeEnabled,
            actor = authentication.name,
        )
        return ResponseEntity.ok().build()
    }
}
