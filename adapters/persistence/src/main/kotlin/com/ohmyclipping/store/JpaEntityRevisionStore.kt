package com.ohmyclipping.store

import com.ohmyclipping.entity.EntityRevisionEntity
import com.ohmyclipping.model.EntityRevision
import com.ohmyclipping.repository.EntityRevisionRepository
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Repository
import java.util.UUID

/**
 * JPA 기반 엔티티 변경 이력 저장소.
 *
 * `changed_fields`는 JSON 배열 문자열로 직렬화하여 저장한다.
 * `snapshot`은 상위 서비스가 이미 직렬화해 넘기므로 그대로 저장한다.
 */
@Repository
class JpaEntityRevisionStore(
    private val repository: EntityRevisionRepository
) : EntityRevisionStore {

    companion object {
        /** listRecent limit 상한. 기본 20, 최대 100까지 허용. */
        private const val MAX_LIMIT = 100
    }

    private val mapper: ObjectMapper = jacksonObjectMapper()

    override fun append(
        resourceType: String,
        resourceId: String,
        editorId: String,
        editorDisplayName: String?,
        changedFields: List<String>,
        snapshot: String
    ): EntityRevision {
        // 다음 리비전 번호를 계산한다. 이 연산은 append 호출 트랜잭션 내부에서 동작해야 한다.
        val nextRevision = nextRevisionNumber(resourceType, resourceId)
        val entity = EntityRevisionEntity(
            id = UUID.randomUUID().toString(),
            resourceType = resourceType,
            resourceId = resourceId,
            revisionNumber = nextRevision,
            editorId = editorId,
            editorDisplayName = editorDisplayName,
            changedFields = mapper.writeValueAsString(changedFields),
            snapshot = snapshot
        )
        return repository.save(entity).toModel()
    }

    override fun listRecent(resourceType: String, resourceId: String, limit: Int): List<EntityRevision> {
        // limit 경계값 보정 — 0 이하/상한 초과 요청을 방어한다.
        val safeLimit = limit.coerceIn(1, MAX_LIMIT)
        val pageable = PageRequest.of(0, safeLimit)
        return repository
            .findByResourceTypeAndResourceIdOrderByRevisionNumberDesc(resourceType, resourceId, pageable)
            .map { it.toModel() }
    }

    override fun findById(revisionId: String): EntityRevision? =
        repository.findById(revisionId).orElse(null)?.toModel()

    override fun nextRevisionNumber(resourceType: String, resourceId: String): Long {
        // 현재 최대 번호가 없으면 1부터 시작한다.
        val current = repository.findMaxRevisionNumber(resourceType, resourceId)
        return (current ?: 0L) + 1L
    }

    private fun EntityRevisionEntity.toModel(): EntityRevision {
        // changedFields는 빈 배열 또는 null 저장이 가능하므로 양쪽 모두 안전하게 역직렬화한다.
        val fields: List<String> = changedFields
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { mapper.readValue(it, LIST_STRING_TYPE) }.getOrElse { emptyList() } }
            ?: emptyList()
        return EntityRevision(
            id = id,
            resourceType = resourceType,
            resourceId = resourceId,
            revisionNumber = revisionNumber,
            editorId = editorId,
            editorDisplayName = editorDisplayName,
            changedFields = fields,
            snapshot = snapshot,
            createdAt = createdAt
        )
    }
}

private val LIST_STRING_TYPE = object : TypeReference<List<String>>() {}
