package com.clipping.mcpserver.store

import com.clipping.mcpserver.entity.AuditLogEntity
import com.clipping.mcpserver.repository.AuditLogRepository
import jakarta.persistence.EntityManager
import org.springframework.context.annotation.Primary
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * 감사 로그 JPA 구현. JdbcAuditLogStore를 대체한다.
 * 동적 WHERE 필터 조합은 EntityManager 네이티브 쿼리를 사용한다.
 */
@Repository
@Primary
class JpaAuditLogStore(
    private val repository: AuditLogRepository,
    private val em: EntityManager
) : AuditLogStore {

    override fun log(
        actorId: String?,
        actorName: String,
        action: String,
        targetType: String,
        targetId: String?,
        targetName: String?,
        detail: String?
    ) {
        // actor_id 는 AuditActorResolver 가 이미 UUID(admin_users.id) 또는 null 로
        // 정규화했다고 가정한다. store 는 추가 검증을 하지 않는다 — FK(V120) 가 DB 레벨에서
        // 무결성을 보장한다.
        val entity = AuditLogEntity(
            actorId = actorId,
            actorName = actorName,
            action = action,
            targetType = targetType,
            targetId = targetId,
            targetName = targetName,
            detail = detail,
            createdAt = Instant.now()
        )
        repository.save(entity)
    }

    override fun findRecent(limit: Int): List<AuditLogStore.AuditLogEntry> =
        repository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, limit))
            .map { it.toEntry() }

    override fun findAll(
        actorId: String?,
        action: String?,
        targetType: String?,
        from: Instant?,
        to: Instant?,
        offset: Int,
        limit: Int
    ): List<AuditLogStore.AuditLogEntry> {
        val (whereClause, params) = buildWhereClause(actorId, action, targetType, from, to)
        val sql = "SELECT * FROM audit_log$whereClause ORDER BY created_at DESC LIMIT ? OFFSET ?"
        val query = em.createNativeQuery(sql, AuditLogEntity::class.java)
        var idx = 1
        for (param in params) {
            query.setParameter(idx++, param)
        }
        query.setParameter(idx++, limit)
        query.setParameter(idx, offset)
        @Suppress("UNCHECKED_CAST")
        return (query.resultList as List<AuditLogEntity>).map { it.toEntry() }
    }

    override fun countAll(
        actorId: String?,
        action: String?,
        targetType: String?,
        from: Instant?,
        to: Instant?
    ): Int {
        val (whereClause, params) = buildWhereClause(actorId, action, targetType, from, to)
        val sql = "SELECT COUNT(*) FROM audit_log$whereClause"
        val query = em.createNativeQuery(sql)
        var idx = 1
        for (param in params) {
            query.setParameter(idx++, param)
        }
        return (query.singleResult as Number).toInt()
    }

    override fun getDistinctActions(): List<String> =
        repository.findDistinctActions()

    override fun getDistinctTargetTypes(): List<String> =
        repository.findDistinctTargetTypes()

    @Transactional
    override fun deleteOlderThan(days: Int): Int {
        val cutoff = Instant.now().minus(days.toLong(), ChronoUnit.DAYS)
        return repository.deleteByCreatedAtBefore(cutoff)
    }

    /**
     * 동적 WHERE 절과 바인딩 파라미터를 생성한다.
     * 널이 아닌 필터만 조건에 포함된다.
     */
    private fun buildWhereClause(
        actorId: String?,
        action: String?,
        targetType: String?,
        from: Instant?,
        to: Instant?
    ): Pair<String, List<Any>> {
        val conditions = mutableListOf<String>()
        val params = mutableListOf<Any>()

        if (actorId != null) {
            conditions += "actor_id = ?"
            params += actorId
        }
        if (action != null) {
            conditions += "action = ?"
            params += action
        }
        if (targetType != null) {
            conditions += "target_type = ?"
            params += targetType
        }
        if (from != null) {
            conditions += "created_at >= ?"
            params += Timestamp.from(from)
        }
        if (to != null) {
            conditions += "created_at < ?"
            params += Timestamp.from(to)
        }

        val whereClause = if (conditions.isEmpty()) "" else " WHERE ${conditions.joinToString(" AND ")}"
        return whereClause to params
    }

    private fun AuditLogEntity.toEntry() = AuditLogStore.AuditLogEntry(
        id = id,
        actorId = actorId,
        actorName = actorName,
        action = action,
        targetType = targetType,
        targetId = targetId,
        targetName = targetName,
        detail = detail,
        createdAt = createdAt
    )
}
