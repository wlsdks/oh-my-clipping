package com.ohmyclipping.entity

import jakarta.persistence.*
import java.io.Serializable
import java.time.Instant

/**
 * 사용자-페르소나 소유 관계 복합키.
 */
data class UserOwnedPersonaId(
    val userId: String = "",
    val personaId: String = ""
) : Serializable

/**
 * 사용자가 소유한 페르소나 매핑 엔티티.
 * clipping_user_owned_personas 테이블에 매핑된다.
 */
@Entity
@Table(name = "clipping_user_owned_personas")
@IdClass(UserOwnedPersonaId::class)
class UserOwnedPersonaEntity(
    @Id
    @Column(name = "user_id", length = 36)
    val userId: String = "",

    @Id
    @Column(name = "persona_id", length = 36)
    val personaId: String = "",

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
)
