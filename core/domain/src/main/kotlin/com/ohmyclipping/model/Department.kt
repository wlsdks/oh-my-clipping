package com.ohmyclipping.model

import java.time.Instant

/**
 * 부서 도메인 모델.
 *
 * V128(스펙 §1.1) 에서 도입. Admin UI 로 관리 가능한 조직도의 최상위 노드.
 * 기존 자유 텍스트 `admin_users.department` 컬럼을 FK 체계로 전환하기 위한 핵심 엔티티.
 *
 * - [name] 은 표시용 원본 문자열.
 * - [nameNormalized] 는 [com.ohmyclipping.util.DepartmentNormalizer] 결과로,
 *   DB UNIQUE 제약 기준이다. "영업팀" 과 "영업 팀" 같은 표기 차이를 막는다.
 * - [displayOrder] 는 Admin UI 정렬, [isActive] 는 soft-delete 플래그.
 */
data class Department(
    val id: String,
    val name: String,
    val nameNormalized: String,
    val displayOrder: Int = 0,
    val isActive: Boolean = true,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
)

/**
 * 팀 도메인 모델.
 *
 * V128(스펙 §1.2) 에서 도입. 부서 하위 조직 단위.
 *
 * - [departmentId] 는 상위 부서 FK. ON DELETE CASCADE.
 * - [nameNormalized] 는 (department_id, name_normalized) UNIQUE 제약 기준.
 * - [isActive] 가 false 면 신규 선택 대상에서 제외되지만 이력은 보존.
 */
data class Team(
    val id: String,
    val departmentId: String,
    val name: String,
    val nameNormalized: String,
    val displayOrder: Int = 0,
    val isActive: Boolean = true,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
)

/**
 * 부서 + 하위 팀을 묶은 트리 뷰 모델.
 *
 * Admin 및 공개 signup 엔드포인트가 공통으로 반환하는 구조로,
 * 응답 DTO 는 이 모델을 목적별(전체 vs 활성 최소 노출) 로 매핑한다.
 */
data class DepartmentTree(
    val department: Department,
    val teams: List<Team>
)
