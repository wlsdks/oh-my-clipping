package com.clipping.mcpserver.entity

import jakarta.persistence.*
import java.io.Serializable
import java.time.Instant

/**
 * 사용자-카테고리 소유 관계 복합키.
 */
data class UserOwnedCategoryId(
    val userId: String = "",
    val categoryId: String = ""
) : Serializable

/**
 * 사용자가 소유한 카테고리 매핑 엔티티.
 * clipping_user_owned_categories 테이블에 매핑된다.
 */
@Entity
@Table(name = "clipping_user_owned_categories")
@IdClass(UserOwnedCategoryId::class)
class UserOwnedCategoryEntity(
    @Id
    @Column(name = "user_id", length = 36)
    val userId: String = "",

    @Id
    @Column(name = "category_id", length = 36)
    val categoryId: String = "",

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
)
