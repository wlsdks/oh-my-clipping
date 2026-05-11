package com.clipping.mcpserver.store

import com.clipping.mcpserver.model.Department

/**
 * 부서 저장소 포트.
 *
 * 서비스 레이어가 의존하는 순수 도메인 인터페이스. JPA 구현은 [JpaDepartmentStore] 이며
 * Clean Architecture 에 따라 서비스는 JPA 엔티티/리포지토리에 직접 접근하지 않는다.
 */
interface DepartmentStore {

    /** 활성/비활성 모두 포함한 부서 목록 (Admin UI 용). display_order 오름차순. */
    fun findAll(): List<Department>

    /** 활성 부서만 반환 (공개 엔드포인트 / signup 용). */
    fun findAllActive(): List<Department>

    /** 단건 조회. 없으면 null. */
    fun findById(id: String): Department?

    /** 정규화된 이름으로 조회. UNIQUE 위반 사전 검증에 사용한다. */
    fun findByNameNormalized(nameNormalized: String): Department?

    /** 신규 저장. id 가 blank 이면 UUID 생성. */
    fun save(department: Department): Department

    /** 기존 레코드 갱신. id 로 조회되지 않으면 예외. */
    fun update(department: Department): Department

    /** id 로 부서 row 를 물리 삭제한다. 참조 무결성(사용자/팀) 가드는 서비스 레이어가 담당. */
    fun deleteById(id: String)
}
