package com.clipping.mcpserver.entity

import jakarta.persistence.*
import java.time.Instant

/**
 * 카테고리별 스크리닝/발송 규칙 엔티티.
 * clipping_category_rules 테이블에 매핑된다.
 * PK는 category_id 단독이다.
 */
@Entity
@Table(name = "clipping_category_rules")
class CategoryRuleEntity(
    @Id
    @Column(name = "category_id", length = 36)
    val categoryId: String = "",

    @Column(name = "include_keywords", columnDefinition = "TEXT", nullable = false)
    var includeKeywords: String = "[]",

    @Column(name = "exclude_keywords", columnDefinition = "TEXT", nullable = false)
    var excludeKeywords: String = "[]",

    @Column(name = "risk_tags", columnDefinition = "TEXT", nullable = false)
    var riskTags: String = "[]",

    /**
     * 자동 EXCLUDE 대상 event_type 블랙리스트(JSON 문자열).
     * V132 에서 추가됐으며 기본값은 '[]'(룰 비활성).
     * 도메인에서는 List<String> 로 Jackson 파싱된다.
     */
    @Column(name = "exclude_event_types", columnDefinition = "TEXT", nullable = false)
    var excludeEventTypes: String = "[]",

    @Column(name = "include_threshold", nullable = false)
    var includeThreshold: Double = 0.55,

    @Column(name = "review_threshold", nullable = false)
    var reviewThreshold: Double = 0.35,

    @Column(name = "uncertain_to_review", nullable = false)
    var uncertainToReview: Boolean = true,

    @Column(name = "auto_exclude_enabled", nullable = false)
    var autoExcludeEnabled: Boolean = true,

    @Column(name = "delivery_days", length = 50)
    var deliveryDays: String? = null,

    @Column(name = "delivery_hour")
    var deliveryHour: Int? = null,

    @Column(name = "delivery_preset", length = 20)
    var deliveryPreset: String? = null,

    /**
     * AI INCLUDE 제안을 자동 승인할 importance 임계값.
     * NULL이면 비활성(기본). 값이 있으면 해당 importance 이상의 INCLUDE 제안이 즉시 INCLUDE로 저장된다.
     * 범위는 [0,1]로 DB CHECK 제약으로 강제한다.
     */
    @Column(name = "auto_approve_threshold")
    var autoApproveThreshold: Double? = null,

    /**
     * 규칙 리비전 카운터. 저장할 때마다 1씩 증가한다.
     * V111에서 `version` → `revision`으로 리네이밍됐다.
     */
    @Column(nullable = false)
    var revision: Int = 1,

    @Column(name = "updated_by", length = 100)
    var updatedBy: String? = null,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),

    /**
     * 스케줄러/배치가 마지막으로 건드린 시각. 사용자 편집 시각과 분리한다.
     */
    @Column(name = "system_updated_at", nullable = false)
    var systemUpdatedAt: Instant = Instant.now()
)
