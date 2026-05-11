package com.clipping.mcpserver.store

import com.clipping.mcpserver.entity.UserOwnedSourceEntity
import com.clipping.mcpserver.entity.UserOwnedSourceId
import com.clipping.mcpserver.repository.UserOwnedSourceRepository
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Repository

/**
 * 사용자-RSS 소스 소유권 JPA 구현. JdbcUserOwnedSourceStore를 대체한다.
 */
@Repository
@Primary
class JpaUserOwnedSourceStore(
    private val repository: UserOwnedSourceRepository
) : UserOwnedSourceStore {

    override fun save(userId: String, sourceId: String) {
        val id = UserOwnedSourceId(userId, sourceId)
        // 이미 존재하면 저장을 생략한다 (중복 방지).
        if (repository.existsById(id)) return
        repository.save(UserOwnedSourceEntity(userId = userId, sourceId = sourceId))
    }

    override fun exists(userId: String, sourceId: String): Boolean =
        repository.existsByUserIdAndSourceId(userId, sourceId)

    override fun listSourceIds(userId: String): List<String> =
        repository.findSourceIdsByUserId(userId)
}
