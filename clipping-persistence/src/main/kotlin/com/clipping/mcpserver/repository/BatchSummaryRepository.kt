package com.clipping.mcpserver.repository

import com.clipping.mcpserver.entity.BatchSummaryEntity
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant

/**
 * 기사 요약 JPA 리포지토리.
 * batch_summaries 테이블에 대한 CRUD 및 조회 메서드를 제공한다.
 * 복잡한 검색(full-text, 동적 필터)은 Store 구현에서 EntityManager/JdbcTemplate으로 처리한다.
 */
interface BatchSummaryRepository : JpaRepository<BatchSummaryEntity, String> {

    /** 카테고리 ID로 요약 목록을 pageable 크기만큼 조회한다. */
    fun findByCategoryId(categoryId: String, pageable: Pageable): List<BatchSummaryEntity>

    /** 카테고리 ID로 요약 목록을 최신순 제한 조회한다. */
    fun findByCategoryIdOrderByCreatedAtDesc(
        categoryId: String,
        pageable: Pageable,
    ): List<BatchSummaryEntity>

    /** Slack 미발송 요약 목록을 오래된 순으로 최대 pageable.pageSize개 조회한다. */
    @Query("SELECT s FROM BatchSummaryEntity s WHERE s.isSentToSlack = false ORDER BY s.createdAt ASC")
    fun findByIsSentToSlackFalse(pageable: Pageable): List<BatchSummaryEntity>

    /** 특정 카테고리의 Slack 미발송 요약 목록을 조회한다. */
    fun findByCategoryIdAndIsSentToSlackFalse(categoryId: String): List<BatchSummaryEntity>

    /** 특정 카테고리의 Slack 미발송 요약 목록을 오래된 순으로 제한 조회한다. */
    fun findByCategoryIdAndIsSentToSlackFalseOrderByCreatedAtAsc(
        categoryId: String,
        pageable: Pageable,
    ): List<BatchSummaryEntity>

    /** 날짜 범위로 요약을 조회한다. */
    fun findByCreatedAtBetween(from: Instant, to: Instant): List<BatchSummaryEntity>

    /** 날짜 범위 요약을 최신순으로 제한 조회한다. */
    fun findByCreatedAtBetweenOrderByCreatedAtDesc(
        from: Instant,
        to: Instant,
        pageable: Pageable,
    ): List<BatchSummaryEntity>

    /** 특정 카테고리의 날짜 범위 요약을 조회한다. */
    fun findByCategoryIdAndCreatedAtBetween(categoryId: String, from: Instant, to: Instant): List<BatchSummaryEntity>

    /** 특정 카테고리의 날짜 범위 요약을 최신순으로 제한 조회한다. */
    fun findByCategoryIdAndCreatedAtBetweenOrderByCreatedAtDesc(
        categoryId: String,
        from: Instant,
        to: Instant,
        pageable: Pageable,
    ): List<BatchSummaryEntity>

    /** Slack 발송 완료된 가장 최근 요약을 카테고리별로 조회한다. */
    fun findFirstByCategoryIdAndIsSentToSlackTrueOrderByCreatedAtDesc(categoryId: String): BatchSummaryEntity?

    /** 발송 완료 여부를 일괄 갱신한다. */
    @Modifying
    @Query("UPDATE BatchSummaryEntity s SET s.isSentToSlack = true WHERE s.id IN :ids")
    fun markSent(ids: List<String>)

    /** 원본 기사 링크로 배치 요약을 조회한다. */
    fun findBySourceLink(sourceLink: String): BatchSummaryEntity?

    /** 카테고리 스코핑된 원본 기사 링크 조회. 같은 link가 여러 카테고리에 존재할 수 있다. */
    fun findFirstBySourceLinkAndCategoryId(sourceLink: String, categoryId: String): BatchSummaryEntity?
}
