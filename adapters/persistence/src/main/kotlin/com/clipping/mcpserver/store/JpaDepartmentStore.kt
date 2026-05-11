package com.clipping.mcpserver.store

import com.clipping.mcpserver.entity.DepartmentEntity
import com.clipping.mcpserver.model.Department
import com.clipping.mcpserver.repository.DepartmentRepository
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

/**
 * [DepartmentStore] 의 JPA 구현.
 *
 * `JpaAuditLogStore` / `JpaOrganizationStore` 와 같은 `@Primary` 패턴을 따른다. UNIQUE
 * 검증은 상위 서비스가 수행하므로 이 구현체는 단순 매핑과 식별자 할당만 책임진다.
 */
@Repository
@Primary
class JpaDepartmentStore(
    private val repository: DepartmentRepository
) : DepartmentStore {

    override fun findAll(): List<Department> =
        repository.findAllByOrderByDisplayOrderAscCreatedAtAsc().map { it.toModel() }

    override fun findAllActive(): List<Department> =
        repository.findAllByIsActiveOrderByDisplayOrderAscCreatedAtAsc(true).map { it.toModel() }

    override fun findById(id: String): Department? =
        repository.findById(id).orElse(null)?.toModel()

    override fun findByNameNormalized(nameNormalized: String): Department? =
        repository.findByNameNormalized(nameNormalized)?.toModel()

    override fun save(department: Department): Department {
        // id 가 비어 있으면 UUID 를 발급해 PK 충돌 위험을 줄인다.
        val id = department.id.ifBlank { UUID.randomUUID().toString() }
        val now = Instant.now()
        val entity = DepartmentEntity(
            id = id,
            name = department.name,
            nameNormalized = department.nameNormalized,
            displayOrder = department.displayOrder,
            isActive = department.isActive,
            createdAt = now,
            updatedAt = now
        )
        return repository.save(entity).toModel()
    }

    override fun update(department: Department): Department {
        // 기존 createdAt 은 유지하고 updatedAt 만 현재 시각으로 갱신한다.
        val entity = repository.findById(department.id).orElseThrow {
            NoSuchElementException("Department not found: ${department.id}")
        }
        entity.name = department.name
        entity.nameNormalized = department.nameNormalized
        entity.displayOrder = department.displayOrder
        entity.isActive = department.isActive
        entity.updatedAt = Instant.now()
        return repository.save(entity).toModel()
    }

    override fun deleteById(id: String) {
        // 부서 물리 삭제. 서비스 레이어가 참조 무결성 (사용자/하위 팀) 을 선행 검증한다.
        // 존재하지 않는 id 는 silently 무시 — 멱등성 확보.
        if (repository.existsById(id)) {
            repository.deleteById(id)
        }
    }

    private fun DepartmentEntity.toModel(): Department = Department(
        id = id,
        name = name,
        nameNormalized = nameNormalized,
        displayOrder = displayOrder,
        isActive = isActive,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
