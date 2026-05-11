package com.clipping.mcpserver.repository

import com.clipping.mcpserver.entity.TeamEntity
import org.springframework.data.jpa.repository.JpaRepository

/**
 * 팀 Spring Data JPA 리포지토리.
 *
 * (department_id, name_normalized) UNIQUE 제약과 부서 간 이동 재검증을 위해
 * [findByDepartmentIdAndNameNormalized] 와 부서별 목록 조회 메서드를 제공한다.
 */
interface TeamRepository : JpaRepository<TeamEntity, String> {

    /** 부서 내에서 정규화된 이름으로 단건 조회. UNIQUE 위반 사전 검증용. */
    fun findByDepartmentIdAndNameNormalized(departmentId: String, nameNormalized: String): TeamEntity?

    /** 특정 부서의 전체 팀을 display_order 오름차순으로 반환 (비활성 포함, Admin UI 용). */
    fun findAllByDepartmentIdOrderByDisplayOrderAscCreatedAtAsc(departmentId: String): List<TeamEntity>

    /** 특정 부서의 활성 팀만 display_order 오름차순으로 반환 (공개 엔드포인트용). */
    fun findAllByDepartmentIdAndIsActiveOrderByDisplayOrderAscCreatedAtAsc(
        departmentId: String,
        isActive: Boolean
    ): List<TeamEntity>

    /** 전체 팀을 한 번에 읽는다. 트리 집계용 (부서당 개별 쿼리를 피하기 위함). */
    fun findAllByOrderByDisplayOrderAscCreatedAtAsc(): List<TeamEntity>

    /** 활성 팀만 전체 반환. 공개 트리 집계용. */
    fun findAllByIsActiveOrderByDisplayOrderAscCreatedAtAsc(isActive: Boolean): List<TeamEntity>
}
