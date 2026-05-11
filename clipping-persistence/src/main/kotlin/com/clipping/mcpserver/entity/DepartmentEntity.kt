package com.clipping.mcpserver.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * 내부 조직도 최상위 노드(부서) JPA 엔티티.
 *
 * V128 마이그레이션으로 생성된 departments 테이블에 매핑된다. Admin UI 로 생성/수정/비활성화하며,
 * [nameNormalized] 는 DB UNIQUE 제약 기준으로 표기 드리프트를 차단한다.
 *
 * 외부 조직(경쟁사/고객사) 을 다루는 [OrganizationEntity] 와 이름이 유사하지만 서로 무관하다.
 */
@Entity
@Table(name = "departments")
class DepartmentEntity(
    @Id
    @Column(length = 36)
    val id: String = "",

    @Column(length = 100, nullable = false)
    var name: String = "",

    /** DepartmentNormalizer 결과 저장. DB UNIQUE 제약 기준. */
    @Column(name = "name_normalized", length = 100, nullable = false, unique = true)
    var nameNormalized: String = "",

    @Column(name = "display_order", nullable = false)
    var displayOrder: Int = 0,

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)
