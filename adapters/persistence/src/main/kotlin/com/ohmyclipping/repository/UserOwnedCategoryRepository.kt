package com.ohmyclipping.repository

import com.ohmyclipping.entity.UserOwnedCategoryEntity
import com.ohmyclipping.entity.UserOwnedCategoryId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface UserOwnedCategoryRepository : JpaRepository<UserOwnedCategoryEntity, UserOwnedCategoryId> {
    fun existsByUserIdAndCategoryId(userId: String, categoryId: String): Boolean

    @Query("SELECT e.categoryId FROM UserOwnedCategoryEntity e WHERE e.userId = :userId")
    fun findCategoryIdsByUserId(userId: String): List<String>
}
