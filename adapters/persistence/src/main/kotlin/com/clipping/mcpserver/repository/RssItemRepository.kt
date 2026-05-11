package com.clipping.mcpserver.repository

import com.clipping.mcpserver.entity.RssItemEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

/**
 * 수집된 RSS 기사 JPA 리포지토리.
 * rss_items 테이블에 대한 CRUD 및 조회 메서드를 제공한다.
 */
interface RssItemRepository : JpaRepository<RssItemEntity, String> {

    /** 카테고리 ID로 기사 목록을 조회한다. */
    fun findByCategoryId(categoryId: String): List<RssItemEntity>

    /** 미처리 기사 목록을 조회한다. */
    fun findByIsProcessedFalse(): List<RssItemEntity>

    /** 특정 카테고리의 미처리 기사 목록을 조회한다. */
    fun findByCategoryIdAndIsProcessedFalse(categoryId: String): List<RssItemEntity>

    /** 기준 시각보다 오래된 기사 수를 반환한다. */
    fun countByCreatedAtBefore(cutoff: Instant): Int

    /** 특정 카테고리에서 기준 시각보다 오래된 기사 수를 반환한다. */
    fun countByCategoryIdAndCreatedAtBefore(categoryId: String, cutoff: Instant): Int

    /** 기준 시각보다 오래된 기사를 삭제한다. */
    @Modifying
    @Query("DELETE FROM RssItemEntity i WHERE i.createdAt < :cutoff")
    fun deleteByCreatedAtBefore(cutoff: Instant): Int

    /** 특정 카테고리에서 기준 시각보다 오래된 기사를 삭제한다. */
    @Modifying
    @Query("DELETE FROM RssItemEntity i WHERE i.categoryId = :categoryId AND i.createdAt < :cutoff")
    fun deleteByCategoryIdAndCreatedAtBefore(categoryId: String, cutoff: Instant): Int

    /** 링크와 카테고리 ID로 기사를 단건 조회한다 (카테고리 스코프 중복 수집 방지용). */
    fun findByLinkAndCategoryId(link: String, categoryId: String): RssItemEntity?

    /** 여러 링크의 존재 여부를 카테고리 스코프로 일괄 확인하여 이미 저장된 링크 목록을 반환한다. */
    @Query("SELECT i.link FROM RssItemEntity i WHERE i.link IN :links AND i.categoryId = :categoryId")
    fun findLinksByLinkInAndCategoryId(
        links: Collection<String>,
        @Param("categoryId") categoryId: String
    ): List<String>

    /**
     * 기준 시각보다 오래된 기사를 최대 limit 건만 삭제한다 (청크 DELETE용).
     * H2/PostgreSQL 모두 호환하는 서브쿼리 기반 DELETE-with-LIMIT 패턴을 사용한다.
     */
    @Modifying
    @Query(
        value = "DELETE FROM rss_items WHERE id IN (SELECT id FROM rss_items WHERE created_at < :cutoff LIMIT :limit)",
        nativeQuery = true
    )
    fun deleteByCreatedAtBeforeLimit(@Param("cutoff") cutoff: Instant, @Param("limit") limit: Int): Int

    /**
     * 특정 카테고리에서 기준 시각보다 오래된 기사를 최대 limit 건만 삭제한다 (청크 DELETE용).
     */
    @Modifying
    @Query(
        value = "DELETE FROM rss_items WHERE id IN " +
            "(SELECT id FROM rss_items WHERE category_id = :categoryId AND created_at < :cutoff LIMIT :limit)",
        nativeQuery = true
    )
    fun deleteByCategoryIdAndCreatedAtBeforeLimit(
        @Param("categoryId") categoryId: String,
        @Param("cutoff") cutoff: Instant,
        @Param("limit") limit: Int
    ): Int
}
