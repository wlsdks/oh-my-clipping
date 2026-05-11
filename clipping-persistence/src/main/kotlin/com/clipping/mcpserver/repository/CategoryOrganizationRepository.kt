package com.clipping.mcpserver.repository

import com.clipping.mcpserver.entity.CategoryOrganizationEntity
import com.clipping.mcpserver.entity.CategoryOrganizationId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

/**
 * Category ↔ Organization 링크 JPA 리포지토리.
 */
interface CategoryOrganizationRepository :
    JpaRepository<CategoryOrganizationEntity, CategoryOrganizationId> {

    /** 카테고리에 연결된 조직 링크 목록. */
    fun findAllByCategoryId(categoryId: String): List<CategoryOrganizationEntity>

    /** 조직에 연결된 카테고리 링크 목록. */
    fun findAllByOrganizationId(organizationId: String): List<CategoryOrganizationEntity>

    /** 카테고리에 연결된 링크 전체 삭제 (setCategoryOrganizations 에서 사용). */
    fun deleteAllByCategoryId(categoryId: String)

    /**
     * 조직 ID 목록에 대해 카테고리 링크 수를 배치 집계한다.
     *
     * 반환값은 [organizationId, count] 쌍의 배열 목록.
     * 링크가 없는 조직 ID 는 GROUP BY 결과에 포함되지 않는다.
     */
    @Query("""
        SELECT c.organizationId, COUNT(c) FROM CategoryOrganizationEntity c
        WHERE c.organizationId IN :orgIds
        GROUP BY c.organizationId
    """)
    fun countByOrganizationIds(@Param("orgIds") orgIds: List<String>): List<Array<Any>>
}
