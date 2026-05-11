package com.ohmyclipping.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import java.io.Serializable
import java.time.Instant

/**
 * Category ↔ Organization 복합키.
 */
data class CategoryOrganizationId(
    val categoryId: String = "",
    val organizationId: String = ""
) : Serializable

/**
 * Category 와 Organization 의 many-to-many 링크 엔티티.
 * category_organizations 테이블(V126)에 매핑된다.
 */
@Entity
@Table(name = "category_organizations")
@IdClass(CategoryOrganizationId::class)
class CategoryOrganizationEntity(
    @Id
    @Column(name = "category_id", length = 36)
    val categoryId: String = "",

    @Id
    @Column(name = "organization_id", length = 36)
    val organizationId: String = "",

    @Column(name = "tenant_id", length = 36, nullable = false)
    var tenantId: String = "default",

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
)
