package com.ohmyclipping.repository

import com.ohmyclipping.entity.RssSourceEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant

/**
 * RSS 뉴스 소스 JPA 리포지토리.
 * rss_sources 테이블에 대한 CRUD 및 조회 메서드를 제공한다.
 */
interface RssSourceRepository : JpaRepository<RssSourceEntity, String> {

    /**
     * 지정 검증 상태이면서 createdAt 이 cutoff 이전인 소스 목록을 조회한다.
     * SLA 에스컬레이션 스케줄러에서 `verificationStatus='PENDING'` 조합으로 사용한다.
     */
    fun findByVerificationStatusAndCreatedAtBefore(
        verificationStatus: String,
        cutoff: Instant,
    ): List<RssSourceEntity>

    /** 카테고리 ID로 소스 목록을 조회한다. */
    fun findByCategoryId(categoryId: String): List<RssSourceEntity>

    /** 카테고리 ID별 소스 수를 반환한다. */
    fun countByCategoryId(categoryId: String): Int

    /** 승인된 활성 소스 목록을 조회한다. */
    fun findByCrawlApprovedTrueAndIsActiveTrue(): List<RssSourceEntity>

    /** 특정 카테고리의 승인된 활성 소스 목록을 조회한다. */
    fun findByCategoryIdAndCrawlApprovedTrueAndIsActiveTrue(categoryId: String): List<RssSourceEntity>

    /** URL과 카테고리 ID로 소스를 조회한다. URL 재사용 판단용. */
    fun findFirstByUrlAndCategoryId(url: String, categoryId: String): RssSourceEntity?

    /** 연속 실패 횟수가 지정값 이상인 활성 소스를 조회한다. */
    fun findByCrawlFailCountGreaterThanEqualAndIsActiveTrue(minFailCount: Int): List<RssSourceEntity>

    /** 비활성화된 소스 목록을 조회한다. */
    fun findByIsActiveFalse(): List<RssSourceEntity>

    /** 해당 카테고리에서 수집 오류(crawlFailCount > 0)인 소스 수를 반환한다. */
    fun countByCategoryIdAndCrawlFailCountGreaterThan(categoryId: String, minFailCount: Int): Int

    /** 카테고리 ID와 origin 으로 소스 목록을 조회한다. */
    fun findByCategoryIdAndOrigin(categoryId: String, origin: String): List<RssSourceEntity>

    /** 카테고리 내 특정 URL의 소스 수를 반환한다. 존재 여부 확인에 사용. */
    fun countByCategoryIdAndUrl(categoryId: String, url: String): Int
}
