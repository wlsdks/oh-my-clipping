package com.ohmyclipping.repository

import com.ohmyclipping.entity.OrganizationEntity
import org.springframework.data.jpa.repository.JpaRepository

/**
 * Organization JPA 리포지토리.
 * organizations 테이블에 대한 CRUD 및 조회 메서드를 제공한다.
 */
interface OrganizationRepository : JpaRepository<OrganizationEntity, String> {

    /** tenant 내에서 이름으로 단건 조회한다 (중복 검증용). */
    fun findByTenantIdAndName(tenantId: String, name: String): OrganizationEntity?

    /** tenant 내 타입별 조회. */
    fun findAllByTenantIdAndTypeOrderByNameAsc(tenantId: String, type: String): List<OrganizationEntity>

    /** tenant 내 전체 조회. */
    fun findAllByTenantIdOrderByNameAsc(tenantId: String): List<OrganizationEntity>
}
