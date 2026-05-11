package com.clipping.mcpserver.store

import com.clipping.mcpserver.model.Category
import java.time.Duration
import java.time.Instant

interface CategoryStore {
    fun list(): List<Category>

    /** 지정 ID 목록에 해당하는 카테고리만 조회한다. 결과는 생성일 오름차순으로 반환한다. */
    fun listByIds(ids: Collection<String>): List<Category>

    /** 운영 가능한(ACTIVE) 카테고리만 조회한다. 스케줄러/배치가 전체 카테고리를 로드하지 않도록 사용한다. */
    fun findOperational(): List<Category>

    /** 사용자 탐색에 노출 가능한 공개 운영 카테고리만 조회한다. */
    fun findPublicOperational(): List<Category>

    fun findById(id: String): Category?
    fun findByName(name: String): Category?
    fun save(category: Category): Category
    fun update(category: Category): Category
    fun updateWithExpectedUpdatedAt(category: Category, expectedUpdatedAt: Instant): Category?
    fun delete(id: String)
    fun countSources(categoryId: String): Int

    /** 여러 카테고리의 소스 수를 한 번에 집계한다. */
    fun countSourcesByCategoryIds(categoryIds: List<String>): Map<String, Int>

    /** 검색 조건을 적용하여 카테고리를 페이지네이션 조회한다. */
    fun findAll(search: String? = null, offset: Int = 0, limit: Int = 30): List<Category>

    /** 검색 조건에 해당하는 카테고리 총 건수를 반환한다. */
    fun countAll(search: String? = null): Int

    /** 특정 페르소나에 연결된 활성 카테고리를 반환한다. 분석 배치에서 사용. */
    fun findActiveByPersonaId(personaId: String): List<Category>

    /** 카테고리를 일시정지 상태로 변경한다. 변경 성공 시 true를 반환한다. */
    fun pause(id: String): Boolean

    /** 카테고리를 활성 상태로 복원한다. 변경 성공 시 true를 반환한다. */
    fun resume(id: String): Boolean

    /** pausedAt이 maxDuration을 초과한 PAUSED 카테고리를 조회한다. */
    fun findExpiredPaused(maxDuration: Duration): List<Category>

    /** 활성(is_active=true) 카테고리 수를 반환한다. */
    fun countActive(): Long

    /** 운영 가능한(ACTIVE) 카테고리 수를 반환한다. */
    fun countOperational(): Long

    /**
     * since 이후 생성된 카테고리 수를 반환한다.
     * 홈 대시보드 "구독 트렌드" 카드의 신규 구독 지표에 사용한다.
     *
     * @param since 집계 시작 시각 (createdAt >= since 조건)
     */
    fun countNewSince(since: Instant): Long

    /**
     * since 이후 비활성화된 카테고리 수를 반환한다.
     * 홈 대시보드 "구독 트렌드" 카드의 이탈 구독 지표에 사용한다.
     *
     * 주의: updatedAt을 기준으로 is_active=false 건을 집계하므로,
     * 비활성화 이후 다시 수정된 경우 결과에 포함될 수 있다 (근사치).
     *
     * @param since 집계 시작 시각 (updatedAt >= since AND is_active=false 조건)
     */
    fun countDeactivatedSince(since: Instant): Long
}
