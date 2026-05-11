package com.clipping.mcpserver.store

import com.clipping.mcpserver.model.BatchSummary
import java.time.Instant

interface BatchSummaryStore :
    SummaryDeliveryStore,
    SentArticleStore,
    SummaryKeywordLookupStore,
    SummaryCategoryCountStore {
    fun findById(id: String): BatchSummary?

    /** ID 목록으로 배치 요약을 일괄 조회한다. 순서는 보장하지 않는다. */
    fun findByIds(ids: List<String>): List<BatchSummary>

    fun findByCategoryId(categoryId: String, limit: Int = 1000): List<BatchSummary>

    /**
     * 날짜 범위로 배치 요약을 조회한다. categoryId 가 null 이면 전체 카테고리.
     * limit 가 null 이면 전체 행을 반환하고(기존 호출자 호환), non-null 이면 SQL LIMIT 을 적용해
     * 크로스-카테고리 full-scan 을 피한다.
     */
    fun findByDateRange(
        from: Instant,
        to: Instant,
        categoryId: String? = null,
        limit: Int? = null,
    ): List<BatchSummary>

    /**
     * 중요도 상위 기사 화면용 조건 검색을 DB 정렬/LIMIT으로 조회한다.
     * 대량 기간 조회 후 JVM에서 필터링하지 않도록 운영 조회 경로에서 사용한다.
     */
    fun findTopArticles(
        from: Instant,
        to: Instant,
        categoryId: String? = null,
        sentiment: String? = null,
        eventType: String? = null,
        keyword: String? = null,
        limit: Int = 10,
    ): List<BatchSummary>

    /**
     * 여러 카테고리에 대해 카테고리별 최근 N개 요약을 단일 쿼리로 조회한다. briefing 같은
     * "구독 카테고리 × perCategoryLimit" UX 에서 N+1 round-trip 을 제거하기 위한 경로다.
     *
     * 정렬: 카테고리별로 created_at DESC, 각 카테고리마다 limitPerCategory 만큼 반환.
     * 반환은 category_id + created_at DESC 평면 리스트 — 호출자가 groupBy(categoryId) 로
     * 섹션화하면 된다.
     *
     * 구현 주의: PostgreSQL 의 윈도 함수 `ROW_NUMBER() OVER (PARTITION BY category_id ORDER BY
     * created_at DESC)` 를 사용한다. H2(MODE=PostgreSQL) 도 윈도 함수를 지원하므로 테스트
     * 환경에서도 동일 쿼리가 동작한다.
     */
    fun findByCategoryIdsAndDateRange(
        categoryIds: List<String>,
        from: Instant,
        to: Instant,
        limitPerCategory: Int,
    ): List<BatchSummary>
    fun save(summary: BatchSummary): BatchSummary

    /** 원본 기사 링크로 배치 요약을 조회한다. 경쟁사 수집 시 중복 기사를 판별하는 데 사용한다. */
    fun findBySourceLink(link: String): BatchSummary?

    /** 카테고리 스코핑된 원본 기사 링크 조회. 같은 link가 여러 카테고리에 존재할 수 있다. */
    fun findBySourceLinkAndCategoryId(link: String, categoryId: String): BatchSummary?

}
