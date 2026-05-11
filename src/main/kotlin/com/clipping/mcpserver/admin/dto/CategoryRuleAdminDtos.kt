package com.clipping.mcpserver.admin.dto

/**
 * 카테고리 운영 규칙 조회 응답 DTO.
 */
data class CategoryRuleResponse(
    val categoryId: String,
    val includeKeywords: List<String>,
    val excludeKeywords: List<String>,
    val riskTags: List<String>,
    /**
     * 자동 EXCLUDE 대상 event_type 블랙리스트. 빈 리스트면 룰 비활성 상태.
     * V132 에서 DB 에 추가됐고, 관리자 UI 에서 dry-run 후 저장 시 반영된다.
     * 정규화 규칙: trim + UPPERCASE + blank 제거 + 중복 제거.
     */
    val excludeEventTypes: List<String> = emptyList(),
    val includeThreshold: Double,
    val reviewThreshold: Double,
    val uncertainToReview: Boolean,
    val autoExcludeEnabled: Boolean,
    val autoApproveThreshold: Double? = null,
    /** 운영 규칙 리비전 카운터. 저장할 때마다 1씩 증가. */
    val revision: Int,
    val updatedBy: String?,
    val updatedAt: String
)

/**
 * 카테고리별 분류 통계 응답 DTO.
 */
data class RuleStatsResponse(
    val totalIncluded: Int,
    val totalReview: Int,
    val totalExcluded: Int,
    val perCategory: List<CategoryStat>
) {
    data class CategoryStat(
        val categoryId: String,
        val categoryName: String,
        val included: Int,
        val review: Int,
        val excluded: Int,
        val hasRule: Boolean
    )
}

/**
 * 제외 항목 조회 응답 DTO.
 */
data class ExcludedItemsResponse(
    val total: Int,
    val items: List<ExcludedItem>
) {
    data class ExcludedItem(
        val title: String,
        val reason: String,
        val matchedKeyword: String?,
        val score: Double,
        val excludedAt: String
    )
}

/**
 * 카테고리 운영 규칙 dry-run 요청 DTO.
 *
 * PR-3-lite 범위에서 편집 가능한 필드는 `excludeEventTypes` 한 개이므로 요청 body 도 해당
 * 필드만 받는다. null/미지정은 빈 리스트로 해석해 "룰 비활성 상태" 를 시뮬레이션한다.
 *
 * @property excludeEventTypes 제안된 event_type 블랙리스트. null → 빈 리스트로 정규화.
 * @property days 분석 기간 (일). null 이면 서비스 기본값(30) 사용. 1..90.
 * @property maxSamples 반환할 EXCLUDE 샘플 상한. null 이면 서비스 기본값(5) 사용. 1..50.
 */
data class CategoryRuleDryRunRequest(
    val excludeEventTypes: List<String>? = null,
    val days: Int? = null,
    val maxSamples: Int? = null,
)

/**
 * 카테고리 운영 규칙 수정 요청 DTO.
 */
data class UpdateCategoryRuleRequest(
    val includeKeywords: List<String>? = null,
    val excludeKeywords: List<String>? = null,
    val riskTags: List<String>? = null,
    /**
     * 자동 EXCLUDE 대상 event_type 블랙리스트. null 이면 기존 값을 유지한다.
     * 서비스에서 trim + UPPERCASE + blank 제거 + 중복 제거로 정규화된다.
     * 빈 리스트를 명시적으로 저장하면 룰 비활성 상태(=자동 EXCLUDE 없음)가 된다.
     */
    val excludeEventTypes: List<String>? = null,
    val includeThreshold: Double? = null,
    val reviewThreshold: Double? = null,
    val uncertainToReview: Boolean? = null,
    val autoExcludeEnabled: Boolean? = null,
    /** 자동 승인 importance 임계값(옵션). [0,1] 범위. null이면 비활성 유지. */
    val autoApproveThreshold: Double? = null,
    /** true면 자동 승인 임계값을 명시적으로 해제한다(null로 되돌림). */
    val clearAutoApproveThreshold: Boolean? = null,
    val updatedBy: String? = null,
    /** 낙관적 잠금 기준 시각(ISO-8601). null이면 잠금 검사 없이 덮어쓴다. */
    val expectedUpdatedAt: java.time.Instant? = null
)
