package com.clipping.mcpserver.store

import com.clipping.mcpserver.model.RssSource
import com.clipping.mcpserver.model.SourceComplianceStatus
import java.time.Instant
import java.time.LocalDate

interface RssSourceStore {
    fun list(limit: Int = 1000): List<RssSource>
    fun listByCategoryId(categoryId: String): List<RssSource>
    fun listApproved(categoryId: String? = null): List<RssSource>
    fun findById(id: String): RssSource?
    fun findPendingVerificationCreatedBefore(cutoff: Instant): List<RssSource>
    /** URL과 카테고리 ID로 기존 소스 조회. URL 재사용 판단용. */
    fun findByUrlAndCategoryId(url: String, categoryId: String): RssSource?
    fun save(source: RssSource): RssSource
    fun update(source: RssSource): RssSource
    fun updateWithExpectedUpdatedAt(source: RssSource, expectedUpdatedAt: Instant): RssSource?
    fun delete(id: String)
    fun updateApproval(id: String, approved: Boolean, approvedBy: String?)
    fun updateVerificationStatus(id: String, status: String)
    fun incrementFailCount(id: String, error: String)
    fun resetFailCount(id: String)

    /** 연속 실패 횟수가 minFailCount 이상인 활성 소스를 조회한다. */
    fun findFailedSources(minFailCount: Int): List<RssSource>

    /** 소스를 비활성화(is_active = false)한다. */
    fun deactivate(id: String)

    /** 비활성화된 소스 목록을 조회한다. */
    fun findDeactivated(): List<RssSource>

    /** 소스를 재활성화(is_active = true)한다. */
    fun reactivate(id: String)

    /** 해당 카테고리에서 수집 오류(crawlFailCount > 0)인 소스 수를 반환한다. */
    fun countErrorByCategoryId(categoryId: String): Int

    /**
     * 검색/카테고리/저작권 조건을 적용하여 소스를 페이지네이션 조회한다.
     *
     * @param complianceStatus null 이면 필터 미적용. 각 상태의 의미는
     *                         [SourceComplianceStatus] KDoc 참고.
     */
    fun findAll(
        categoryId: String? = null,
        search: String? = null,
        complianceStatus: SourceComplianceStatus? = null,
        offset: Int = 0,
        limit: Int = 30
    ): List<RssSource>

    /** 검색/카테고리/저작권 조건에 해당하는 소스 총 건수를 반환한다. */
    fun countAll(
        categoryId: String? = null,
        search: String? = null,
        complianceStatus: SourceComplianceStatus? = null
    ): Int

    /**
     * 전체 소스 중 재검토가 필요한(만료/만료 임박/미검토) 건수를 반환한다.
     * 사이드바 뱃지와 대시보드 카드에서 사용한다.
     */
    fun countComplianceAttention(now: Instant): Int

    /** cutoff 이후 수집된 기사 수를 소스별로 집계한다. */
    fun countArticlesBySource(cutoff: Instant): Map<String, Int>

    /** 특정 소스의 cutoff 이후 일별 기사 수를 반환한다. */
    fun countDailyArticlesBySource(sourceId: String, cutoff: Instant): List<Pair<LocalDate, Int>>

    /** 특정 소스에 연결된 수집 기사 수를 반환한다. 삭제 전 참조 보호에 사용한다. */
    fun countArticlesBySourceId(sourceId: String): Int

    /** 소스별 신뢰도 점수를 일괄 갱신한다. */
    fun updateReliabilityScores(scores: Map<String, Int>)

    /**
     * 소스를 삽입한다. save()와 달리 origin 등 생성 시 전달할 필드를 명시적으로 지정한다.
     * origin 기본값은 'manual'.
     */
    fun insert(
        id: String,
        categoryId: String,
        sourceUrl: String,
        sourceName: String,
        origin: String = "manual"
    )

    /**
     * 카테고리 ID와 origin으로 소스 목록을 조회한다.
     * CategorySourceBuilder 가 자동 생성 소스와 수동 소스를 구분하는 데 사용한다.
     */
    fun findByCategoryIdAndOrigin(categoryId: String, origin: String): List<RssSource>

    /**
     * 카테고리 내 동일 URL 소스가 이미 존재하는지 확인한다.
     * CategorySourceBuilder 중복 삽입 방지에 사용한다.
     */
    fun existsByCategoryIdAndUrl(categoryId: String, sourceUrl: String): Boolean
}
