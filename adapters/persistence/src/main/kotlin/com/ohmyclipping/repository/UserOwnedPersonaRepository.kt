package com.ohmyclipping.repository

import com.ohmyclipping.entity.UserOwnedPersonaEntity
import com.ohmyclipping.entity.UserOwnedPersonaId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface UserOwnedPersonaRepository : JpaRepository<UserOwnedPersonaEntity, UserOwnedPersonaId> {
    fun existsByUserIdAndPersonaId(userId: String, personaId: String): Boolean

    @Query("SELECT e.personaId FROM UserOwnedPersonaEntity e WHERE e.userId = :userId")
    fun findPersonaIdsByUserId(userId: String): List<String>

    fun deleteByUserIdAndPersonaId(userId: String, personaId: String)
}
