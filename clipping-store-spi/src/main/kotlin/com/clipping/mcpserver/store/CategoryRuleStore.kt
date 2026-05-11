package com.clipping.mcpserver.store

import com.clipping.mcpserver.model.CategoryRule
import java.time.Instant

interface CategoryRuleStore {
    fun findByCategoryId(categoryId: String): CategoryRule?
    fun upsert(rule: CategoryRule): CategoryRule

    /**
     * 기대 updated_at과 일치할 때만 저장한다. 편집 충돌(낙관적 잠금 실패) 시 null 반환.
     *
     * 서비스 레이어는 반환값이 null이면 `ConflictException(staleEditInfo)` 로 변환해야 한다.
     * 저장 시 revision은 `current.revision + 1` 로 반영되고, updated_at은 서버 시각으로 강제 갱신된다.
     */
    fun updateWithExpectedUpdatedAt(rule: CategoryRule, expectedUpdatedAt: Instant): CategoryRule?

    /** 개별 발송 스케줄(delivery_preset)이 설정된 카테고리 ID 목록을 반환한다. */
    fun findCategoryIdsWithCustomSchedule(): Set<String>

    /**
     * 카테고리 룰의 include_keywords 목록을 반환한다.
     * 룰이 없거나 키워드가 설정되지 않은 경우 빈 리스트를 반환한다.
     *
     * CategorySourceBuilder 가 digest mode 결정 및 URL 생성에 사용한다.
     */
    fun findIncludeKeywords(categoryId: String): List<String>

    /**
     * 카테고리 룰의 include_keywords 를 덮어쓴다.
     * 룰이 없으면 신규 생성, 있으면 include_keywords 만 교체한다.
     * 위자드 승인 흐름에서 keyword entry 를 카테고리 룰로 저장할 때 사용한다.
     */
    fun setIncludeKeywords(categoryId: String, keywords: List<String>)

    /**
     * include_keywords 와 exclude_event_types 를 원자적으로 함께 덮어쓴다.
     * 룰이 없으면 두 필드만 채운 신규 룰을 생성하고, 있으면 두 필드만 교체한다.
     * 나머지 필드(excludeKeywords, thresholds 등)는 보존된다.
     *
     * CategoryRuleBundleService 의 atomic PUT endpoint 에서 사용한다.
     * @see docs/ADR.md ADR-032
     */
    fun setKeywordsAndExcludeEventTypes(
        categoryId: String,
        includeKeywords: List<String>,
        excludeEventTypes: List<String>,
    )
}
