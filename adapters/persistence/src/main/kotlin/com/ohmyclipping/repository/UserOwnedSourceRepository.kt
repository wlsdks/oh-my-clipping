package com.ohmyclipping.repository

import com.ohmyclipping.entity.UserOwnedSourceEntity
import com.ohmyclipping.entity.UserOwnedSourceId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface UserOwnedSourceRepository : JpaRepository<UserOwnedSourceEntity, UserOwnedSourceId> {
    fun existsByUserIdAndSourceId(userId: String, sourceId: String): Boolean

    @Query("SELECT e.sourceId FROM UserOwnedSourceEntity e WHERE e.userId = :userId")
    fun findSourceIdsByUserId(userId: String): List<String>
}
