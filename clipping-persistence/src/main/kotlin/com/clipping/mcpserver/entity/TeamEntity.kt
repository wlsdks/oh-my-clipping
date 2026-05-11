package com.clipping.mcpserver.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * 부서 하위 팀 JPA 엔티티.
 *
 * V128 마이그레이션으로 생성된 teams 테이블에 매핑된다. 상위 부서와는 department_id FK 로
 * 연결되며 DB 는 ON DELETE CASCADE 지만 운영 정책은 soft-delete(is_active=false) 만 허용한다.
 *
 * UNIQUE 제약은 (department_id, name_normalized) 단위라 부서를 넘나드는 동명 팀은 허용되지만
 * 같은 부서 안에서는 [nameNormalized] 중복이 막힌다.
 */
@Entity
@Table(name = "teams")
class TeamEntity(
    @Id
    @Column(length = 36)
    val id: String = "",

    @Column(name = "department_id", length = 36, nullable = false)
    var departmentId: String = "",

    @Column(length = 100, nullable = false)
    var name: String = "",

    @Column(name = "name_normalized", length = 100, nullable = false)
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
