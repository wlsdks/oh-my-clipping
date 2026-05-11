package com.ohmyclipping.model

import java.time.Instant
import java.time.LocalDate
import java.time.Duration

/**
 * 구독(Category) 분석 목적 분류.
 * DB `batch_categories.purpose` CHECK 제약과 일치한다.
 */
enum class CategoryPurpose {
    SALES,
    RESEARCH,
    COMPETITIVE,
    CUSTOMER_CARE,
    OTHER
}

data class Category(
    val id: String,
    val name: String,
    val description: String? = null,
    val slackChannelId: String? = null,
    val isActive: Boolean = true,
    val isPublic: Boolean = false,
    val maxItems: Int = 5,
    val personaId: String? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    val status: CategoryStatus = CategoryStatus.ACTIVE,
    val pausedAt: Instant? = null,
    /**
     * 스케줄러/크롤러 등 시스템이 마지막으로 레코드를 건드린 시각.
     * 낙관적 잠금(옵션 B) 기준 시각인 [updatedAt]과 분리해 사용자 편집 시각이 오염되지 않게 유지한다.
     */
    val systemUpdatedAt: Instant = Instant.now(),
    /**
     * 구독 목적 분류. V123(Phase 3 PR1)에서 추가됐다.
     * DB CHECK 제약으로 허용값이 강제되며, null 이면 아직 분류되지 않은 상태.
     */
    val purpose: CategoryPurpose? = null,
    /** 구독을 만든 배경/맥락. 자유 텍스트. V123 에서 추가됨. */
    val background: String? = null,
    /** 구독이 해결하려는 문제 서술. 자유 텍스트. V123 에서 추가됨. */
    val problemStatement: String? = null
)

data class CategoryRule(
    val categoryId: String,
    val includeKeywords: List<String> = emptyList(),
    val excludeKeywords: List<String> = emptyList(),
    val riskTags: List<String> = emptyList(),
    /**
     * 자동 EXCLUDE 대상 event_type 블랙리스트. 기본값 emptyList()는 룰 비활성을 의미한다.
     * V132 에서 추가됐으며 DB 에는 clipping_category_rules.exclude_event_types TEXT JSON 으로 저장된다.
     */
    val excludeEventTypes: List<String> = emptyList(),
    val includeThreshold: Double = 0.55,
    val reviewThreshold: Double = 0.35,
    val uncertainToReview: Boolean = true,
    val autoExcludeEnabled: Boolean = true,
    val deliveryDays: List<String>? = null,
    val deliveryHour: Int? = null,
    val deliveryPreset: DeliveryPreset? = null,
    /**
     * AI INCLUDE 제안을 자동 승인할 importance 임계값. null이면 비활성(기본).
     * 값은 [0,1] 범위로 DB CHECK 제약과 서비스 검증에서 강제한다.
     */
    val autoApproveThreshold: Double? = null,
    /**
     * 운영 규칙 리비전 카운터. 저장할 때마다 1씩 증가해 감사 추적과
     * 프론트/백엔드 간 편집 충돌 감지 보조 신호로 사용한다.
     * (옛 이름은 `version`. V111에서 `revision`으로 리네이밍됐다.)
     */
    val revision: Int = 0,
    val updatedBy: String? = null,
    val updatedAt: Instant = Instant.now(),
    /**
     * 스케줄러/배치가 마지막으로 건드린 시각. 사용자 편집 시각인 [updatedAt]과 분리한다.
     */
    val systemUpdatedAt: Instant = Instant.now()
)

enum class ReviewDecisionStatus {
    INCLUDE,
    REVIEW,
    EXCLUDE
}

data class ReviewItemDecision(
    val summaryId: String,
    val categoryId: String,
    val status: ReviewDecisionStatus = ReviewDecisionStatus.REVIEW,
    val suggestedStatus: ReviewDecisionStatus? = null,
    val reason: String? = null,
    val reviewedBy: String? = null,
    val reviewedAt: Instant? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
)

data class ReviewItemAudit(
    val id: String,
    val summaryId: String,
    val categoryId: String,
    val fromStatus: ReviewDecisionStatus? = null,
    val toStatus: ReviewDecisionStatus,
    val reason: String? = null,
    val reviewedBy: String? = null,
    val reviewedAt: Instant? = null,
    val createdAt: Instant = Instant.now()
)

enum class TrendPeriodType {
    WEEKLY,
    MONTHLY
}

enum class TrendRegionType {
    ALL,
    GLOBAL,
    DOMESTIC
}

enum class TrendSnapshotStatus {
    DRAFT,
    PUBLISHED
}

enum class TrendVisualCardType {
    INFO_CARD,
    COMIC_4,
    COMIC_8
}

enum class TrendVisualReviewStatus {
    PENDING,
    APPROVED,
    REJECTED
}

data class TrendSnapshot(
    val id: String,
    val periodType: TrendPeriodType,
    val snapshotFrom: LocalDate,
    val snapshotTo: LocalDate,
    val categoryId: String? = null,
    val categoryName: String,
    val regionType: TrendRegionType = TrendRegionType.ALL,
    val title: String,
    val summary: String,
    val keySignals: List<String> = emptyList(),
    val actionItems: List<String> = emptyList(),
    val sourceCount: Int = 0,
    val itemCount: Int = 0,
    val status: TrendSnapshotStatus = TrendSnapshotStatus.DRAFT,
    val templateType: String = "DETAILED",
    val generatedBy: String? = null,
    val publishedAt: Instant? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
)

data class TrendVisualCard(
    val id: String,
    val snapshotId: String,
    val cardType: TrendVisualCardType,
    val title: String,
    val summary: String,
    val panels: List<String> = emptyList(),
    val reviewStatus: TrendVisualReviewStatus = TrendVisualReviewStatus.PENDING,
    val reviewNote: String? = null,
    val generatedBy: String? = null,
    val reviewedBy: String? = null,
    val reviewedAt: Instant? = null,
    val published: Boolean = false,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
)

data class RssSource(
    val id: String,
    val name: String,
    val url: String,
    val emoji: String? = null,
    val isActive: Boolean = true,
    val crawlApproved: Boolean = false,
    val approvedBy: String? = null,
    val approvedAt: Instant? = null,
    val legalBasis: SourceLegalBasis = SourceLegalBasis.QUOTATION_ONLY,
    val summaryAllowed: Boolean = true,
    val fulltextAllowed: Boolean = false,
    val termsReviewedAt: Instant? = null,
    /**
     * 저작권 재검토 예정일. [termsReviewedAt] + 180일 기준으로 서비스가 자동 갱신한다.
     * NULL 이면 아직 검토 기록이 없는 상태.
     */
    val expectedReviewAt: Instant? = null,
    val reviewNotes: String? = null,
    val verificationStatus: String = "PENDING",
    val reliabilityScore: Int = 50,
    val lastCrawlError: String? = null,
    val crawlFailCount: Int = 0,
    val lastSuccessAt: Instant? = null,
    val sourceRegion: SourceRegionType = SourceRegionType.UNKNOWN,
    val categoryId: String,
    val curated: Boolean = false,
    val responsibilityAcknowledgedAt: Instant? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    /**
     * 스케줄러/크롤러가 마지막으로 갱신한 시각.
     * verificationStatus/reliabilityScore/crawlFailCount 등 시스템 상태 변경은
     * [updatedAt]을 건드리지 않고 이 필드만 갱신해 관리자 편집 시각을 오염시키지 않는다.
     */
    val systemUpdatedAt: Instant = Instant.now(),

    /**
     * 소스 등록 경로. 'manual'(관리자 직접), 'auto_generated'(자동 생성), 'legacy' 중 하나.
     * DB CHECK 제약 chk_rss_sources_origin 로 보호된다.
     */
    val origin: String = "manual"
)

enum class SourceLegalBasis {
    LICENSED,
    OPEN_LICENSE,
    QUOTATION_ONLY,
    PROHIBITED
}

enum class SourceRegionType {
    GLOBAL,
    DOMESTIC,
    UNKNOWN
}

enum class Language { KOREAN, FOREIGN }

/**
 * 수집된 RSS 기사 엔티티.
 * 카테고리 귀속과 후속 요약/LLM 실행 연결 무결성을 판단하는 기준이 된다.
 */
data class RssItem(
    val id: String,
    val title: String,
    val content: String? = null,
    val link: String,
    val publishedAt: Instant? = null,
    val language: Language = Language.FOREIGN,
    val isProcessed: Boolean = false,
    val categoryId: String,
    val rssSourceId: String? = null,
    val screenedScore: Float? = null,
    val createdAt: Instant = Instant.now()
) {
    /** 이 기사가 특정 카테고리에 속하는지 확인한다. */
    fun belongsToCategory(categoryId: String): Boolean = this.categoryId == categoryId

    /** 요약이 같은 기사/카테고리/링크를 가리키는지 확인한다. rssItemId가 null이면 항상 false. */
    fun matches(summary: BatchSummary): Boolean =
        summary.rssItemId != null &&
            id == summary.rssItemId &&
            title == summary.originalTitle &&
            link == summary.sourceLink &&
            belongsToCategory(summary.categoryId)

    /** LLM 실행 이력이 같은 기사/카테고리를 기준으로 기록됐는지 확인한다. */
    fun matches(run: LlmRun): Boolean =
        run.rssItemId == id &&
            belongsToCategory(run.categoryId)
}

/**
 * 기사 기반 요약 엔티티.
 * 원문 기사와 카테고리/제목/링크 기준으로 같은 대상을 가리키는지 검증한다.
 */
data class BatchSummary(
    val id: String,
    val originalTitle: String,
    val translatedTitle: String? = null,
    val summary: String,
    val insights: String? = null,
    val keywords: List<String> = emptyList(),
    val importanceScore: Float = 0f,
    val sourceLink: String,
    val isSentToSlack: Boolean = false,
    val categoryId: String,
    val rssItemId: String? = null,
    val sentiment: String? = null,
    val eventType: String? = null,
    val isFallback: Boolean = false,
    val createdAt: Instant = Instant.now()
) {
    /** 요약이 특정 카테고리에 속하는지 확인한다. */
    fun belongsToCategory(categoryId: String): Boolean = this.categoryId == categoryId

    /** 이 요약이 실제 기사와 동일한 카테고리/제목/링크를 기준으로 연결됐는지 확인한다. */
    fun isLinkedTo(item: RssItem): Boolean = item.matches(this)
}

/**
 * 경쟁사 워치리스트 항목.
 * 경쟁사 이름, 검색 키워드 목록, 등급(DIRECT/ADJACENT/GLOBAL)을 관리한다.
 */
data class CompetitorWatchlist(
    val id: String,
    val name: String,
    val aliases: List<String> = emptyList(),
    val excludeKeywords: List<String> = emptyList(),
    val tier: String = "DIRECT",
    val isActive: Boolean = true,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
)

data class BatchSummaryCompetitor(
    val summaryId: String,
    val competitorId: String
)

data class CompetitorRssFeed(
    val id: String,
    val competitorId: String,
    val feedUrl: String,
    val label: String? = null,
    val createdAt: Instant = Instant.now()
)

data class DailySummary(
    val id: String,
    val title: String,
    val totalItems: Int,
    val summaryDate: LocalDate,
    val topicKeywords: List<String> = emptyList(),
    val overallSummary: String? = null,
    val isSentToSlack: Boolean = false,
    val categoryId: String,
    val createdAt: Instant = Instant.now()
)

data class Persona(
    val id: String,
    val name: String,
    val description: String? = null,
    val systemPrompt: String,
    val summaryStyle: String? = null,
    val targetAudience: String? = null,
    val maxItems: Int = 5,
    val language: String = "ko",
    val isActive: Boolean = true,
    val isPreset: Boolean = false,
    val previewTitle: String? = null,
    val previewSource: String? = null,
    val previewBody: String? = null,
    val currentVersion: Int = 1,
    val tone: String? = null,
    val lengthPref: String? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    /**
     * 스케줄러/배치가 마지막으로 건드린 시각. 사용자 편집 시각인 [updatedAt]과 분리한다.
     */
    val systemUpdatedAt: Instant = Instant.now()
)

data class ClippingStat(
    val id: String,
    val categoryId: String,
    val statDate: LocalDate,
    val itemsCollected: Int = 0,
    val itemsDuplicates: Int = 0,
    val itemsSummarized: Int = 0,
    val itemsSent: Int = 0,
    val slackSendAttempts: Int = 0,
    val slackSendSuccesses: Int = 0,
    val topKeywords: List<String> = emptyList(),
    val avgImportanceScore: Float = 0f,
    val createdAt: Instant = Instant.now()
)

data class SummaryFeedback(
    val id: String,
    val summaryId: String,
    val feedbackType: String,
    val userId: String,
    val createdAt: Instant = Instant.now()
)

data class SummaryFeedbackHotSummary(
    val summaryId: String,
    val title: String,
    val sourceLink: String,
    val likeCount: Int,
    val neutralCount: Int,
    val dislikeCount: Int,
    val totalCount: Int,
    val score: Double,
    val lastFeedbackAt: Instant
)

data class OriginalContent(
    val id: String,
    val rssItemId: String,
    val sourceLink: String,
    val title: String,
    val markdown: String,
    val contentHash: String? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
)

/**
 * LLM 실행 이력 엔티티.
 * 기사 기반 실행인지와 연결된 기사와의 카테고리 일치 여부를 스스로 설명한다.
 */
data class LlmRun(
    val id: String,
    val categoryId: String,
    val rssItemId: String? = null,
    val model: String,
    val promptVersion: String,
    val inputHash: String,
    val inputChars: Int,
    val outputChars: Int,
    val status: String,
    val errorMessage: String? = null,
    val durationMs: Long,
    val tokensIn: Int? = null,
    val tokensOut: Int? = null,
    val createdAt: Instant = Instant.now()
) {
    /** 기사 기반 실행인지 여부를 반환한다. */
    fun isItemBound(): Boolean = !rssItemId.isNullOrBlank()

    /** 기사 기반 실행이라면 실제 기사와 같은 카테고리에 묶였는지 확인한다. */
    fun isLinkedTo(item: RssItem): Boolean =
        !isItemBound() || item.matches(this)
}

enum class AccountRole {
    ADMIN,
    USER
}

enum class AccountApprovalStatus {
    PENDING,
    APPROVED,
    REJECTED;

    fun allowedTransitions(): Set<AccountApprovalStatus> = when (this) {
        PENDING -> setOf(APPROVED, REJECTED)
        APPROVED -> setOf(REJECTED)  // 탈퇴 시 REJECTED 전환 허용
        REJECTED -> emptySet()
    }

    fun canTransitionTo(target: AccountApprovalStatus): Boolean =
        target in allowedTransitions()
}

data class AdminUser(
    val id: String,
    val username: String,
    val passwordHash: String,
    val role: AccountRole = AccountRole.ADMIN,
    val displayName: String? = null,
    val department: String? = null,
    /**
     * 부서 하위 조직(팀). V124(Phase 3 PR1)에서 추가됐다. null 허용.
     * 저장 시 service 레이어에서 [com.ohmyclipping.util.DepartmentNormalizer] 로 정규화된다.
     */
    val team: String? = null,
    /**
     * 부서 FK. V129 에서 추가됐다. 레거시 [department] 는 이름 캐시로 유지.
     */
    val departmentId: String? = null,
    /**
     * 팀 FK. V129 에서 추가됐다. 레거시 [team] 은 이름 캐시로 유지.
     */
    val teamId: String? = null,
    val isActive: Boolean = true,
    val approvalStatus: AccountApprovalStatus = AccountApprovalStatus.APPROVED,
    val approvalNote: String? = null,
    val approvedByUserId: String? = null,
    val approvedAt: Instant? = null,
    val slackMemberId: String? = null,
    val slackDmChannelId: String? = null,
    val mustChangePassword: Boolean = false,
    val lastLoginAt: Instant? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
)

enum class UserClippingRequestStatus {
    PENDING,
    APPROVED,
    REJECTED,
    WITHDRAWN;

    /** 허용된 상태 전이 목록. 정의되지 않은 전이는 서비스 레이어에서 거부한다. */
    fun allowedTransitions(): Set<UserClippingRequestStatus> = when (this) {
        PENDING -> setOf(APPROVED, REJECTED, WITHDRAWN)
        APPROVED -> setOf(WITHDRAWN)
        REJECTED -> emptySet()
        WITHDRAWN -> emptySet()
    }

    fun canTransitionTo(target: UserClippingRequestStatus): Boolean =
        target in allowedTransitions()
}

/**
 * 사용자 구독 요청 애그리거트 루트.
 * 요청의 생애주기(PENDING -> APPROVED/REJECTED/WITHDRAWN)와 전이 제약을 책임진다.
 */
data class UserClippingRequest(
    val id: String,
    val requesterUserId: String,
    val requestName: String,
    val sourceName: String,
    val sourceUrl: String,
    val slackChannelId: String,
    val personaName: String,
    val personaPrompt: String,
    val summaryStyle: String? = null,
    val targetAudience: String? = null,
    val selectedPresetId: String? = null,
    val requestNote: String? = null,
    val status: UserClippingRequestStatus = UserClippingRequestStatus.PENDING,
    val reviewNote: String? = null,
    val reviewedByUserId: String? = null,
    val reviewedAt: Instant? = null,
    val approvedCategoryId: String? = null,
    val approvedPersonaId: String? = null,
    val approvedSourceId: String? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
) {
    /** 아직 관리자 검토 전인지 반환한다. */
    fun isPendingReview(): Boolean = status == UserClippingRequestStatus.PENDING

    /** 승인 완료 상태인지 반환한다. */
    fun isApproved(): Boolean = status == UserClippingRequestStatus.APPROVED

    /** 반려 완료 상태인지 반환한다. */
    fun isRejected(): Boolean = status == UserClippingRequestStatus.REJECTED

    /** 사용자가 스스로 철회했거나 해지한 상태인지 반환한다. */
    fun isWithdrawn(): Boolean = status == UserClippingRequestStatus.WITHDRAWN

    /** 특정 사용자가 소유한 요청인지 확인한다. */
    fun isOwnedBy(userId: String): Boolean = requesterUserId == userId

    /** 같은 카테고리에 이미 연결된 승인 완료 요청인지 확인한다. */
    fun isApprovedForCategory(categoryId: String): Boolean =
        isApproved() && approvedCategoryId == categoryId

    /** 구독 한도 계산 시 포함해야 하는 상태인지 판별한다. */
    fun countsTowardSubscriptionLimit(): Boolean =
        isPendingReview() || isApproved()

    /** 관리자 리뷰가 완료된 상태인지 반환한다. */
    fun isReviewCompleted(): Boolean =
        isApproved() || isRejected()

    /** 삭제 가능한 종료 상태인지 반환한다. */
    fun isDeletable(): Boolean =
        isRejected() || isWithdrawn()

    /** 반려 사유를 통계용으로 정규화해 반환한다. */
    fun rejectionReason(): String? =
        reviewNote?.trim()?.takeIf { isRejected() && it.isNotBlank() }

    /** 승인 완료까지 걸린 시간을 시간 단위로 반환한다. */
    fun approvalLeadTimeHours(): Double? =
        reviewedAt?.takeIf { isApproved() }
            ?.let { approvedAt -> Duration.between(createdAt, approvedAt).toHours().toDouble() }

    /** 지정 시점 이후에 관리자 리뷰가 끝났는지 반환한다. */
    fun wasReviewedAfter(cutoff: Instant): Boolean =
        isReviewCompleted() && reviewedAt?.isAfter(cutoff) == true

    /**
     * 승인 상태로 전이한다.
     * 검토 전 요청만 승인할 수 있다.
     */
    fun approve(
        reviewerUserId: String,
        slackChannelId: String,
        reviewNote: String?,
        approvedCategoryId: String,
        approvedPersonaId: String?,
        approvedSourceId: String?,
        reviewedAt: Instant = Instant.now()
    ): UserClippingRequest {
        require(isPendingReview()) { "Only pending requests can be approved" }
        return copy(
            status = UserClippingRequestStatus.APPROVED,
            slackChannelId = slackChannelId,
            reviewNote = reviewNote,
            reviewedByUserId = reviewerUserId,
            reviewedAt = reviewedAt,
            approvedCategoryId = approvedCategoryId,
            approvedPersonaId = approvedPersonaId,
            approvedSourceId = approvedSourceId,
            updatedAt = reviewedAt
        )
    }

    /**
     * 반려 상태로 전이한다.
     * 검토 전 요청만 반려할 수 있고, 반려 사유는 필수다.
     */
    fun reject(
        reviewerUserId: String,
        reviewNote: String,
        reviewedAt: Instant = Instant.now()
    ): UserClippingRequest {
        require(isPendingReview()) { "Only pending requests can be rejected" }
        require(reviewNote.isNotBlank()) { "reviewNote is required when rejecting a request" }
        return copy(
            status = UserClippingRequestStatus.REJECTED,
            reviewNote = reviewNote,
            reviewedByUserId = reviewerUserId,
            reviewedAt = reviewedAt,
            updatedAt = reviewedAt
        )
    }

    /**
     * 검토 전 요청을 사용자가 직접 철회한다.
     * 승인/반려 완료 요청은 이 메서드로 철회할 수 없다.
     */
    fun withdrawPending(now: Instant = Instant.now()): UserClippingRequest {
        require(isPendingReview()) { "Only pending requests can be withdrawn" }
        return copy(
            status = UserClippingRequestStatus.WITHDRAWN,
            updatedAt = now
        )
    }

    /**
     * 승인 완료 구독을 사용자가 해지한다.
     * 공유 카테고리 모델을 유지하기 위해 요청 상태만 WITHDRAWN으로 전이한다.
     */
    fun unsubscribeApproved(now: Instant = Instant.now()): UserClippingRequest {
        require(isApproved()) { "Only approved requests can be unsubscribed" }
        return copy(
            status = UserClippingRequestStatus.WITHDRAWN,
            updatedAt = now
        )
    }
}

data class BookmarkedArticle(
    val id: String,
    val userId: String,
    val summaryId: String,
    val originalTitle: String,
    val translatedTitle: String?,
    val summary: String,
    val insights: String?,
    val keywords: List<String>,
    val importanceScore: Float,
    val sourceLink: String,
    val categoryId: String,
    val sentiment: String?,
    val eventType: String?,
    val articleCreatedAt: Instant,
    val bookmarkedAt: Instant = Instant.now()
) {
    /** 이 북마크가 어떤 BatchSummary를 스냅샷한 것인지 식별한다. */
    fun matchesSummary(summaryId: String): Boolean = this.summaryId == summaryId
}

data class RetentionPolicy(
    val id: String,
    val categoryId: String,
    val keepDays: Int,
    val isEnabled: Boolean = true,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
)

enum class DeliveryPreset {
    WEEKDAYS,
    EVERYDAY,
    CUSTOM
}

data class UserDeliverySchedule(
    val userId: String,
    val deliveryDays: List<String> = listOf("MON", "TUE", "WED", "THU", "FRI"),
    val deliveryHour: Int = 8,
    val preset: DeliveryPreset = DeliveryPreset.WEEKDAYS,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
)

/**
 * 카테고리-채널 단위 발송 이력.
 * 같은 날·같은 시간대에 중복 발송을 방지하기 위한 delivery_log 레코드.
 */
data class DeliveryLog(
    val id: String,
    val categoryId: String,
    val channelId: String,
    val deliveryDate: LocalDate,
    val deliveryHour: Int,
    val status: String = "RESERVED",
    val itemCount: Int = 0,
    val slackMessageTs: String? = null,
    val retryCount: Int = 0,
    val nextRetryAt: Instant? = null,
    val claimedAt: Instant? = null,
    val lastError: String? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
)

/**
 * 특정 날짜의 발송 요약 통계.
 * 총 건수, 성공/실패/스킵 건수, 성공률을 포함한다.
 */
data class DeliveryDaySummary(
    val totalCount: Int,
    val sentCount: Int,
    val failedCount: Int,
    val skippedCount: Int,
    val successRate: Double
)

/**
 * 키워드 엔티티 분류.
 * 키워드를 PERSON, ORG, TECH, TOPIC, LOCATION 등 카테고리로 분류한다.
 */
data class KeywordEntity(
    val id: String = "",
    val keyword: String,
    val category: String,
    val firstSeen: Instant = Instant.now()
)

/**
 * 개별 아이템 요약 처리 결과.
 * 성공 여부, 추출된 키워드, 중요도 점수, 스크리닝 스킵 여부를 포함한다.
 */
data class ItemSummarizationResult(
    val success: Boolean,
    val keywords: List<String> = emptyList(),
    val importanceScore: Float = 0f,
    val skippedByScreening: Boolean = false,
    val cachedHit: Boolean = false,
    val isFallback: Boolean = false,
)

data class BudgetSetting(
    val id: String = "default",
    val monthlyBudgetUsd: Double = 0.0,
    val alertThresholdPercent: Int = 80,
    val slackAlertEnabled: Boolean = true,
    val updatedAt: Instant = Instant.now()
)

/**
 * 관리자가 차단한 Slack 채널.
 * 사용자 채널 목록에서 제외되며, 구독 신청/설정 변경 시에도 사용할 수 없다.
 */
data class BlockedSlackChannel(
    val id: String,
    val channelId: String,
    val channelName: String,
    val isPrivate: Boolean = false,
    val blockedByUserId: String,
    val blockedAt: Instant = Instant.now(),
    val reason: String? = null
)

/**
 * RSS 소스 크롤 로그 엔티티.
 * 개별 크롤 시도의 성공/실패, 응답시간, 수집 기사 수를 기록한다.
 */
data class SourceCrawlLog(
    val id: Long = 0,
    val sourceId: String,
    val crawledAt: Instant = Instant.now(),
    val success: Boolean,
    val errorMessage: String? = null,
    val responseTimeMs: Int? = null,
    val articlesFound: Int = 0
)
