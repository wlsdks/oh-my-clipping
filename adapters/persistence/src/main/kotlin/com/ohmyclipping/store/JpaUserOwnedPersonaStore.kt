package com.ohmyclipping.store

import com.ohmyclipping.entity.UserOwnedPersonaEntity
import com.ohmyclipping.entity.UserOwnedPersonaId
import com.ohmyclipping.repository.UserOwnedPersonaRepository
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

/**
 * 사용자-페르소나 소유권 JPA 구현. JdbcUserOwnedPersonaStore를 대체한다.
 */
@Repository
@Primary
class JpaUserOwnedPersonaStore(
    private val repository: UserOwnedPersonaRepository
) : UserOwnedPersonaStore {

    override fun save(userId: String, personaId: String) {
        val id = UserOwnedPersonaId(userId, personaId)
        // 이미 존재하면 저장을 생략한다 (중복 방지).
        if (repository.existsById(id)) return
        repository.save(UserOwnedPersonaEntity(userId = userId, personaId = personaId))
    }

    override fun exists(userId: String, personaId: String): Boolean =
        repository.existsByUserIdAndPersonaId(userId, personaId)

    override fun listPersonaIds(userId: String): List<String> =
        repository.findPersonaIdsByUserId(userId)

    @Transactional
    override fun delete(userId: String, personaId: String) {
        repository.deleteByUserIdAndPersonaId(userId, personaId)
    }
}
