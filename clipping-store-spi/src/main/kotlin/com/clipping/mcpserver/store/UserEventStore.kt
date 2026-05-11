package com.clipping.mcpserver.store

import com.clipping.mcpserver.model.UserEvent
import java.time.Instant

/**
 * 사용자 행동 이벤트 저장소 인터페이스.
 * 이벤트 저장과 통계 집계 메서드를 제공한다.
 */
interface UserEventStore {

    /** 단일 이벤트를 저장한다. */
    fun save(event: UserEvent)

    /** 여러 이벤트를 일괄 저장한다. */
    fun saveBatch(events: List<UserEvent>)

    /** 기간 내 특정 이벤트 타입의 발생 횟수를 반환한다. */
    fun countByEventType(eventType: String, from: Instant, to: Instant): Long

    /** 여러 KST 날짜의 특정 이벤트 타입 발생 횟수를 한 번에 반환한다. */
    fun countByEventTypeForDays(eventType: String, days: List<java.time.LocalDate>): Map<java.time.LocalDate, Long>

    /** 기간 내 고유 사용자 수를 반환한다. */
    fun countDistinctUsers(from: Instant, to: Instant): Long

    /** 특정 사용자의 기간 내 이벤트를 최신순으로 조회한다. */
    fun findByUserAndDateRange(
        userId: String,
        from: Instant,
        to: Instant,
        limit: Int = 100
    ): List<UserEvent>

    /**
     * 기간 내 여러 사용자의 이벤트 타입별 건수를 한번에 집계한다.
     * 반환: Map<userId, Map<eventType, count>>
     */
    fun countEventsByTypeForUsers(
        userIds: List<String>,
        from: Instant,
        to: Instant
    ): Map<String, Map<String, Int>>

    /** 기간 내 일별 활성 사용자 수를 집계한다. */
    fun dailyActiveUsers(from: Instant, to: Instant): List<DailyCount>

    /** 기간 내 wizard_step 이벤트의 raw 데이터를 조회한다. */
    fun findWizardStepEvents(from: Instant, to: Instant): List<WizardStepRow>

    /** 기간 내 기사 노출/클릭 이벤트의 raw 데이터를 조회한다. */
    fun findArticleEvents(from: Instant, to: Instant): List<ArticleEventRow>

    /** 지정 cutoff 이전의 오래된 raw 이벤트를 삭제한다. */
    fun deleteOlderThan(cutoff: Instant): Int

    /**
     * summaryId 목록에 해당하는 기사 메타데이터(제목, 카테고리명, 출처명, 발행일)를 조회한다.
     * batch_summaries -> batch_categories, rss_items -> rss_sources를 조인한다.
     */
    fun findArticleMetadata(summaryIds: List<String>): List<ArticleMetadataRow>

    /**
     * summaryId 목록에 해당하는 기사별 북마크 수를 조회한다.
     */
    fun countBookmarksBySummaryIds(summaryIds: List<String>): Map<String, Long>

    /**
     * 특정 일자의 긍정(feedback_positive) / 부정(feedback_negative) 피드백 이벤트 수를 반환한다.
     * 홈 대시보드 "사용자 참여 트렌드" 카드에서 일별 피드백 추이를 계산할 때 사용한다.
     *
     * @param day 집계 대상 날짜 (KST 기준)
     * @return positive / negative 카운트를 담은 DailyFeedbackCount
     */
    fun countFeedbackByDay(day: java.time.LocalDate): DailyFeedbackCount
}

/**
 * 일별 피드백 집계 결과.
 *
 * @property positive feedback_positive 이벤트 수
 * @property negative feedback_negative 이벤트 수
 */
data class DailyFeedbackCount(val positive: Long, val negative: Long)

/**
 * 일별 집계 결과.
 */
data class DailyCount(val date: String, val count: Long)

/**
 * 위자드 단계별 이벤트 raw 데이터 (JSON 파싱은 서비스 레이어에서 수행).
 */
data class WizardStepRow(
    val eventData: String?,
    val userId: String
)

/**
 * 기사 이벤트 raw 데이터 (JSON 파싱은 서비스 레이어에서 수행).
 */
data class ArticleEventRow(
    val eventType: String,
    val eventData: String?,
    val userId: String
)

/**
 * 기사 메타데이터 조회 결과 (batch_summaries + batch_categories + rss_sources 조인).
 */
data class ArticleMetadataRow(
    val summaryId: String,
    val title: String?,
    val categoryId: String?,
    val categoryName: String?,
    val sourceName: String?,
    val publishedAt: String?
)
