package com.clipping.mcpserver.store

import com.clipping.mcpserver.entity.TrendVisualCardEntity
import com.clipping.mcpserver.model.TrendVisualCard
import com.clipping.mcpserver.model.TrendVisualCardType
import com.clipping.mcpserver.model.TrendVisualReviewStatus
import com.clipping.mcpserver.repository.TrendVisualCardRepository
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.context.annotation.Primary
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

/**
 * 트렌드 비주얼 카드 JPA 구현. JdbcTrendVisualCardStore를 대체한다.
 */
@Repository
@Primary
class JpaTrendVisualCardStore(
    private val repository: TrendVisualCardRepository
) : TrendVisualCardStore {

    private val mapper = jacksonObjectMapper()

    override fun findById(id: String): TrendVisualCard? =
        repository.findById(id).orElse(null)?.toModel()

    override fun listBySnapshotId(snapshotId: String, limit: Int): List<TrendVisualCard> {
        val safeLimit = limit.coerceIn(1, 300)
        return repository.findBySnapshotIdOrderByCreatedAtDesc(snapshotId, PageRequest.of(0, safeLimit))
            .map { it.toModel() }
    }

    override fun list(reviewStatus: TrendVisualReviewStatus?, limit: Int): List<TrendVisualCard> {
        val safeLimit = limit.coerceIn(1, 300)
        val pageable = PageRequest.of(0, safeLimit)
        val entities = if (reviewStatus != null) {
            repository.findByReviewStatusOrderByCreatedAtDesc(reviewStatus.name, pageable)
        } else {
            repository.findAll(pageable).content
        }
        return entities.map { it.toModel() }
    }

    override fun save(card: TrendVisualCard): TrendVisualCard {
        val now = Instant.now()
        val id = card.id.ifBlank { UUID.randomUUID().toString() }

        val existing = repository.findById(id).orElse(null)
        if (existing != null) {
            // 기존 카드를 갱신한다.
            existing.snapshotId = card.snapshotId
            existing.cardType = card.cardType.name
            existing.title = card.title
            existing.summary = card.summary
            existing.panels = mapper.writeValueAsString(card.panels)
            existing.reviewStatus = card.reviewStatus.name
            existing.reviewNote = card.reviewNote
            existing.generatedBy = card.generatedBy
            existing.reviewedBy = card.reviewedBy
            existing.reviewedAt = card.reviewedAt
            existing.published = card.published
            existing.updatedAt = now
            return repository.save(existing).toModel()
        }

        // 새 카드를 생성한다.
        val entity = TrendVisualCardEntity(
            id = id,
            snapshotId = card.snapshotId,
            cardType = card.cardType.name,
            title = card.title,
            summary = card.summary,
            panels = mapper.writeValueAsString(card.panels),
            reviewStatus = card.reviewStatus.name,
            reviewNote = card.reviewNote,
            generatedBy = card.generatedBy,
            reviewedBy = card.reviewedBy,
            reviewedAt = card.reviewedAt,
            published = card.published,
            createdAt = card.createdAt,
            updatedAt = now
        )
        return repository.save(entity).toModel()
    }

    private fun TrendVisualCardEntity.toModel() = TrendVisualCard(
        id = id,
        snapshotId = snapshotId,
        cardType = TrendVisualCardType.valueOf(cardType),
        title = title,
        summary = summary,
        panels = parseJsonList(panels),
        reviewStatus = TrendVisualReviewStatus.valueOf(reviewStatus),
        reviewNote = reviewNote,
        generatedBy = generatedBy,
        reviewedBy = reviewedBy,
        reviewedAt = reviewedAt,
        published = published,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun parseJsonList(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { mapper.readValue<List<String>>(raw) }.getOrDefault(emptyList())
    }
}
