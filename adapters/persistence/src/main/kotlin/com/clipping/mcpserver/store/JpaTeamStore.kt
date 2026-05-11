package com.clipping.mcpserver.store

import com.clipping.mcpserver.entity.TeamEntity
import com.clipping.mcpserver.model.Team
import com.clipping.mcpserver.repository.TeamRepository
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

/**
 * [TeamStore] 의 JPA 구현.
 *
 * `@Primary` 패턴을 따른다. 부서 간 이동 시 UNIQUE(department_id, name_normalized) 재검증은
 * 상위 서비스의 책임이고, 구현체는 식별자 할당과 단순 매핑만 책임진다.
 */
@Repository
@Primary
class JpaTeamStore(
    private val repository: TeamRepository
) : TeamStore {

    override fun findAll(): List<Team> =
        repository.findAllByOrderByDisplayOrderAscCreatedAtAsc().map { it.toModel() }

    override fun findAllActive(): List<Team> =
        repository.findAllByIsActiveOrderByDisplayOrderAscCreatedAtAsc(true).map { it.toModel() }

    override fun findAllByDepartmentId(departmentId: String): List<Team> =
        repository.findAllByDepartmentIdOrderByDisplayOrderAscCreatedAtAsc(departmentId).map { it.toModel() }

    override fun findAllActiveByDepartmentId(departmentId: String): List<Team> =
        repository
            .findAllByDepartmentIdAndIsActiveOrderByDisplayOrderAscCreatedAtAsc(departmentId, true)
            .map { it.toModel() }

    override fun findById(id: String): Team? =
        repository.findById(id).orElse(null)?.toModel()

    override fun findByDepartmentIdAndNameNormalized(departmentId: String, nameNormalized: String): Team? =
        repository.findByDepartmentIdAndNameNormalized(departmentId, nameNormalized)?.toModel()

    override fun save(team: Team): Team {
        // UUID 발급 + 생성/수정 시각을 확정한다.
        val id = team.id.ifBlank { UUID.randomUUID().toString() }
        val now = Instant.now()
        val entity = TeamEntity(
            id = id,
            departmentId = team.departmentId,
            name = team.name,
            nameNormalized = team.nameNormalized,
            displayOrder = team.displayOrder,
            isActive = team.isActive,
            createdAt = now,
            updatedAt = now
        )
        return repository.save(entity).toModel()
    }

    override fun update(team: Team): Team {
        val entity = repository.findById(team.id).orElseThrow {
            NoSuchElementException("Team not found: ${team.id}")
        }
        // 부서 이동도 여기서 처리한다 (서비스가 UNIQUE 재검증을 마친 후 호출).
        entity.departmentId = team.departmentId
        entity.name = team.name
        entity.nameNormalized = team.nameNormalized
        entity.displayOrder = team.displayOrder
        entity.isActive = team.isActive
        entity.updatedAt = Instant.now()
        return repository.save(entity).toModel()
    }

    override fun deleteById(id: String) {
        // 팀 물리 삭제. 서비스 레이어가 참조 무결성(사용자) 을 선행 검증한다.
        if (repository.existsById(id)) {
            repository.deleteById(id)
        }
    }

    private fun TeamEntity.toModel(): Team = Team(
        id = id,
        departmentId = departmentId,
        name = name,
        nameNormalized = nameNormalized,
        displayOrder = displayOrder,
        isActive = isActive,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
