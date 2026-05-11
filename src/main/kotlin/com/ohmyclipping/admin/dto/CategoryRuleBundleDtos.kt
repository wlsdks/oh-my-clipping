package com.ohmyclipping.admin.dto

import jakarta.validation.constraints.Size

/**
 * 카테고리 룰 + 조직 링크 + feature flag 를 한번에 저장하는 원자적 요청.
 *
 * 설계 배경: docs/ADR.md ADR-032 참고.
 */
data class RuleBundleRequest(
    @field:Size(max = 100, message = "excludeEventTypes 최대 100개")
    val excludeEventTypes: List<String> = emptyList(),
    @field:Size(max = 100, message = "includeKeywords 최대 100개")
    val includeKeywords: List<String> = emptyList(),
    @field:Size(max = 50, message = "organizationIds 최대 50개")
    val organizationIds: List<String> = emptyList(),
    val accountBasedDigestEnabled: Boolean = false,
    /** V137 merge 후 활성화됨 — D2 이전에는 best-effort(no-op). */
    val shadowModeEnabled: Boolean = false,
)
