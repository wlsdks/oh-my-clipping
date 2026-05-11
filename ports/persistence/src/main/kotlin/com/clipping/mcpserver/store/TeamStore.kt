package com.clipping.mcpserver.store

import com.clipping.mcpserver.model.Team

/**
 * 팀 저장소 포트.
 *
 * Clean Architecture 포트. 서비스 레이어는 이 인터페이스에 의존하고, JPA 구현은
 * [JpaTeamStore] 가 담당한다. UNIQUE(department_id, name_normalized) 제약과
 * 부서 간 이동 재검증을 위한 메서드를 제공한다.
 */
interface TeamStore {

    /** 모든 팀(비활성 포함) 을 display_order 오름차순으로 반환. 트리 집계용. */
    fun findAll(): List<Team>

    /** 활성 팀만 전체 반환. 공개 트리 집계용. */
    fun findAllActive(): List<Team>

    /** 특정 부서의 팀 목록. 비활성 포함. */
    fun findAllByDepartmentId(departmentId: String): List<Team>

    /** 특정 부서의 활성 팀 목록. */
    fun findAllActiveByDepartmentId(departmentId: String): List<Team>

    /** 단건 조회. 없으면 null. */
    fun findById(id: String): Team?

    /**
     * (departmentId, nameNormalized) 조합으로 조회. UNIQUE 위반 사전 검증과
     * 팀을 다른 부서로 이동할 때의 재검증에 사용한다.
     */
    fun findByDepartmentIdAndNameNormalized(departmentId: String, nameNormalized: String): Team?

    /** 신규 저장. id 가 blank 이면 UUID 생성. */
    fun save(team: Team): Team

    /** 기존 레코드 갱신. id 로 조회되지 않으면 예외. */
    fun update(team: Team): Team

    /** id 로 팀 row 를 물리 삭제한다. 참조 무결성(사용자) 가드는 서비스 레이어가 담당. */
    fun deleteById(id: String)
}
