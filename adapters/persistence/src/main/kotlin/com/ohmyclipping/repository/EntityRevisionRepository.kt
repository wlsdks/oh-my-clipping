package com.ohmyclipping.repository

import com.ohmyclipping.entity.EntityRevisionEntity
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

/**
 * 엔티티 변경 이력 Repository.
 * append/listRecent/max revision 계산에만 사용되며 검색 용도는 아니다.
 */
interface EntityRevisionRepository : JpaRepository<EntityRevisionEntity, String> {

    /**
     * (resourceType, resourceId)의 revision을 최신순으로 반환한다. [pageable]로 limit를 제어한다.
     */
    fun findByResourceTypeAndResourceIdOrderByRevisionNumberDesc(
        resourceType: String,
        resourceId: String,
        pageable: Pageable
    ): List<EntityRevisionEntity>

    /**
     * (resourceType, resourceId)의 현재 최대 revision_number를 반환한다.
     * 이력이 없으면 null.
     */
    @Query(
        """
        SELECT MAX(e.revisionNumber)
        FROM EntityRevisionEntity e
        WHERE e.resourceType = :resourceType AND e.resourceId = :resourceId
        """
    )
    fun findMaxRevisionNumber(resourceType: String, resourceId: String): Long?
}
