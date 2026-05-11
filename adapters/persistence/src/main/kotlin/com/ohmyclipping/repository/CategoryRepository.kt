package com.ohmyclipping.repository

import com.ohmyclipping.entity.CategoryEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant

/**
 * 뉴스 클리핑 카테고리 JPA 리포지토리.
 * batch_categories 테이블에 대한 CRUD 및 조회 메서드를 제공한다.
 */
interface CategoryRepository : JpaRepository<CategoryEntity, String> {

    /** 카테고리명으로 단건 조회한다. */
    fun findByName(name: String): CategoryEntity?

    /** 이름 오름차순으로 전체 카테고리를 조회한다. */
    fun findAllByOrderByNameAsc(): List<CategoryEntity>

    /** 특정 페르소나의 활성 카테고리를 생성일 오름차순으로 조회한다. */
    fun findByPersonaIdAndStatusOrderByCreatedAtAsc(personaId: String, status: String): List<CategoryEntity>

    /** 상태별 카테고리를 생성일 오름차순으로 조회한다. */
    fun findByStatusOrderByCreatedAtAsc(status: String): List<CategoryEntity>

    /** 사용자 탐색에 노출 가능한 공개 상태별 카테고리를 생성일 오름차순으로 조회한다. */
    fun findByStatusAndIsPublicTrueOrderByCreatedAtAsc(status: String): List<CategoryEntity>

    /** 특정 시각 이전에 일시정지된 카테고리를 생성일 오름차순으로 조회한다. */
    fun findByStatusAndPausedAtBeforeOrderByCreatedAtAsc(status: String, pausedAt: Instant): List<CategoryEntity>

    /** is_active=true인 카테고리 수를 반환한다. */
    fun countByIsActiveTrue(): Long

    /** 상태별 카테고리 수를 반환한다. */
    fun countByStatus(status: String): Long

    /**
     * since 이후 생성된 카테고리 수를 반환한다.
     * CategoryStore.countNewSince() 구현에서 호출한다.
     */
    fun countByCreatedAtGreaterThanEqual(createdAt: Instant): Long

    /**
     * since 이후 갱신되었으며 is_active=false인 카테고리 수를 반환한다.
     * CategoryStore.countDeactivatedSince() 구현에서 호출한다.
     * updatedAt 기반이므로 비활성화 후 재수정된 경우 근사치가 될 수 있다.
     */
    fun countByUpdatedAtGreaterThanEqualAndIsActiveFalse(updatedAt: Instant): Long
}
