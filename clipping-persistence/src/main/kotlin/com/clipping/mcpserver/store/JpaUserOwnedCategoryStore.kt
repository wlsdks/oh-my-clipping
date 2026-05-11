package com.clipping.mcpserver.store

import com.clipping.mcpserver.entity.UserOwnedCategoryEntity
import com.clipping.mcpserver.entity.UserOwnedCategoryId
import com.clipping.mcpserver.repository.UserOwnedCategoryRepository
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Repository

/**
 * 사용자-카테고리 소유권 JPA 구현. JdbcUserOwnedCategoryStore를 대체한다.
 */
@Repository
@Primary
class JpaUserOwnedCategoryStore(
    private val repository: UserOwnedCategoryRepository
) : UserOwnedCategoryStore {

    override fun save(userId: String, categoryId: String) {
        val id = UserOwnedCategoryId(userId, categoryId)
        // 이미 존재하면 저장을 생략한다 (중복 방지).
        if (repository.existsById(id)) return
        repository.save(UserOwnedCategoryEntity(userId = userId, categoryId = categoryId))
    }

    override fun exists(userId: String, categoryId: String): Boolean =
        repository.existsByUserIdAndCategoryId(userId, categoryId)

    override fun listCategoryIds(userId: String): List<String> =
        repository.findCategoryIdsByUserId(userId)
}
