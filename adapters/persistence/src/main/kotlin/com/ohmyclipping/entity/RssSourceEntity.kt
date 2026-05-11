package com.ohmyclipping.entity

import jakarta.persistence.*
import java.time.Instant

/**
 * RSS 뉴스 소스 엔티티.
 * rss_sources 테이블에 매핑된다.
 */
@Entity
@Table(name = "rss_sources")
class RssSourceEntity(
    @Id
    @Column(length = 36)
    val id: String = "",

    @Column(length = 200, nullable = false)
    var name: String = "",

    @Column(length = 2000, nullable = false)
    var url: String = "",

    @Column(length = 10)
    var emoji: String? = null,

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,

    @Column(name = "crawl_approved", nullable = false)
    var crawlApproved: Boolean = false,

    @Column(name = "approved_by", length = 100)
    var approvedBy: String? = null,

    @Column(name = "approved_at")
    var approvedAt: Instant? = null,

    @Column(name = "legal_basis", length = 40, nullable = false)
    var legalBasis: String = "QUOTATION_ONLY",

    @Column(name = "summary_allowed", nullable = false)
    var summaryAllowed: Boolean = true,

    @Column(name = "fulltext_allowed", nullable = false)
    var fulltextAllowed: Boolean = false,

    @Column(name = "terms_reviewed_at")
    var termsReviewedAt: Instant? = null,

    @Column(name = "expected_review_at")
    var expectedReviewAt: Instant? = null,

    @Column(name = "review_notes", columnDefinition = "TEXT")
    var reviewNotes: String? = null,

    @Column(name = "verification_status", length = 20, nullable = false)
    var verificationStatus: String = "PENDING",

    @Column(name = "reliability_score", nullable = false)
    var reliabilityScore: Int = 50,

    @Column(name = "last_crawl_error", columnDefinition = "TEXT")
    var lastCrawlError: String? = null,

    @Column(name = "crawl_fail_count", nullable = false)
    var crawlFailCount: Int = 0,

    @Column(name = "last_success_at")
    var lastSuccessAt: Instant? = null,

    @Column(name = "responsibility_acknowledged_at")
    var responsibilityAcknowledgedAt: Instant? = null,

    @Column(name = "source_region", length = 20, nullable = false)
    var sourceRegion: String = "UNKNOWN",

    @Column(name = "category_id", length = 36, nullable = false)
    var categoryId: String = "",

    @Column(nullable = false)
    var curated: Boolean = false,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),

    /**
     * 스케줄러/크롤러가 마지막으로 건드린 시각.
     * verificationStatus/reliabilityScore 등 시스템 상태 변경 시
     * [updatedAt]을 건드리지 않고 이 필드만 갱신해 관리자 편집 시각을 보존한다.
     */
    @Column(name = "system_updated_at", nullable = false)
    var systemUpdatedAt: Instant = Instant.now(),

    /**
     * 소스 등록 경로. 'manual'(관리자 직접), 'auto_generated'(자동 생성), 'legacy' 중 하나.
     * DB CHECK 제약 chk_rss_sources_origin 로 보호된다.
     */
    @Column(name = "origin", length = 32, nullable = false)
    var origin: String = "manual"
)
