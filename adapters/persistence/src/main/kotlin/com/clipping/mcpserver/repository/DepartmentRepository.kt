package com.clipping.mcpserver.repository

import com.clipping.mcpserver.entity.DepartmentEntity
import org.springframework.data.jpa.repository.JpaRepository

/**
 * 부서 Spring Data JPA 리포지토리.
 *
 * 서비스 레이어는 [com.clipping.mcpserver.store.DepartmentStore] 포트를 경유하고
 * 이 리포지토리는 `JpaDepartmentStore` 가 내부 구현으로만 사용한다.
 */
interface DepartmentRepository : JpaRepository<DepartmentEntity, String> {

    /** 정규화된 이름으로 단건 조회. UNIQUE 제약 위반 검증 및 재활용 검색에 사용한다. */
    fun findByNameNormalized(nameNormalized: String): DepartmentEntity?

    /** 전체 부서를 display_order 오름차순으로 반환. Admin UI 용 (비활성 포함). */
    fun findAllByOrderByDisplayOrderAscCreatedAtAsc(): List<DepartmentEntity>

    /** 활성 부서만 display_order 오름차순으로 반환. 공개 엔드포인트/signup 용. */
    fun findAllByIsActiveOrderByDisplayOrderAscCreatedAtAsc(isActive: Boolean): List<DepartmentEntity>
}
