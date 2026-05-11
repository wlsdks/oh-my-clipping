package com.clipping.mcpserver.store

import com.clipping.mcpserver.entity.ReviewItemAuditEntity
import com.clipping.mcpserver.model.ReviewDecisionStatus
import com.clipping.mcpserver.model.ReviewItemAudit
import com.clipping.mcpserver.repository.ReviewItemAuditRepository
import org.springframework.context.annotation.Primary
import org.springframework.data.domain.PageRequest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * 리뷰 결정 변경 감사 이력 JPA 구현. JdbcReviewItemAuditStore를 대체한다.
 */
@Repository
@Primary
class JpaReviewItemAuditStore(
    private val repository: ReviewItemAuditRepository,
    private val jdbc: JdbcTemplate
) : ReviewItemAuditStore {

    override fun listBySummaryId(summaryId: String, limit: Int): List<ReviewItemAudit> {
        val safeLimit = limit.coerceIn(1, 200)
        return repository.findBySummaryIdOrderByCreatedAtDesc(
            summaryId,
            PageRequest.of(0, safeLimit)
        ).map { it.toModel() }
    }

    override fun append(audit: ReviewItemAudit): ReviewItemAudit {
        val persisted = audit.copy(
            id = audit.id.ifBlank { UUID.randomUUID().toString() }
        )
        repository.save(persisted.toEntity())
        return persisted
    }

    override fun batchAppend(audits: List<ReviewItemAudit>): List<ReviewItemAudit> {
        if (audits.isEmpty()) return emptyList()
        val persisted = audits.map { it.copy(id = it.id.ifBlank { UUID.randomUUID().toString() }) }
        // saveAll을 사용해 JPA 배치 INSERT를 수행한다
        repository.saveAll(persisted.map { it.toEntity() })
        return persisted
    }

    @Transactional
    override fun deleteOlderThan(cutoff: Instant, limit: Int): Int {
        // 음수/0 limit은 호출 버그로 간주하고 명시적으로 거부한다
        require(limit > 0) { "limit must be positive: $limit" }
        // JPA repository는 LIMIT가 포함된 bulk DELETE를 안전하게 표현하기 어렵다.
        // 대량 삭제 시 락/Undo 부담을 줄이기 위해 PostgreSQL/H2 공통 구문(IN + LIMIT)을
        // JdbcTemplate으로 직접 실행한다.
        return jdbc.update(
            """
            DELETE FROM clipping_review_item_audits
            WHERE id IN (
                SELECT id FROM clipping_review_item_audits
                WHERE created_at < ?
                ORDER BY created_at
                LIMIT ?
            )
            """.trimIndent(),
            Timestamp.from(cutoff),
            limit
        )
    }

    // ── private helpers ──

    private fun ReviewItemAuditEntity.toModel() = ReviewItemAudit(
        id = id,
        summaryId = summaryId,
        categoryId = categoryId,
        fromStatus = fromStatus?.let { ReviewDecisionStatus.valueOf(it) },
        toStatus = ReviewDecisionStatus.valueOf(toStatus),
        reason = reason,
        reviewedBy = reviewedBy,
        reviewedAt = reviewedAt,
        createdAt = createdAt
    )

    private fun ReviewItemAudit.toEntity() = ReviewItemAuditEntity(
        id = id,
        summaryId = summaryId,
        categoryId = categoryId,
        fromStatus = fromStatus?.name,
        toStatus = toStatus.name,
        reason = reason,
        reviewedBy = reviewedBy,
        reviewedAt = reviewedAt,
        createdAt = createdAt
    )
}
