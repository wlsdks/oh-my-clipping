package com.ohmyclipping.store

import com.ohmyclipping.entity.CompetitorWatchlistEntity
import com.ohmyclipping.error.NotFoundException
import com.ohmyclipping.model.CompetitorWatchlist
import com.ohmyclipping.repository.CompetitorWatchlistRepository
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.context.annotation.Primary
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

/**
 * 경쟁사 워치리스트 JPA 구현. JdbcCompetitorWatchlistStore를 대체한다.
 */
@Repository
@Primary
class JpaCompetitorWatchlistStore(
    private val repository: CompetitorWatchlistRepository
) : CompetitorWatchlistStore {

    private val mapper = jacksonObjectMapper()

    override fun findAll(): List<CompetitorWatchlist> =
        repository.findAll(Sort.by("createdAt")).map { it.toModel() }

    override fun findActive(): List<CompetitorWatchlist> =
        repository.findByIsActiveTrue()
            .sortedBy { it.createdAt }
            .map { it.toModel() }

    override fun findById(id: String): CompetitorWatchlist? =
        repository.findById(id).orElse(null)?.toModel()

    override fun findByTier(tier: String): List<CompetitorWatchlist> =
        repository.findByTier(tier)
            .sortedBy { it.createdAt }
            .map { it.toModel() }

    override fun findByNameIgnoreCase(name: String): CompetitorWatchlist? {
        // name 또는 aliases JSON 문자열에 대소문자 무시로 매칭되는 첫 번째 항목을 반환한다.
        val normalizedName = name.trim().lowercase()
        return repository.findAll()
            .firstOrNull { entity ->
                entity.name.lowercase() == normalizedName ||
                    runCatching {
                        mapper.readValue<List<String>>(entity.aliases)
                            .any { it.lowercase() == normalizedName }
                    }.getOrDefault(false)
            }
            ?.toModel()
    }

    override fun findByNamesIgnoreCase(names: List<String>): List<CompetitorWatchlist> {
        // 빈 입력이면 DB 쿼리 없이 반환한다.
        if (names.isEmpty()) return emptyList()
        // 한 번만 lowercase 변환하여 단일 쿼리로 조회한다 (N+1 제거).
        val normalizedNames = names.map { it.trim().lowercase() }
        return repository.findByNormalizedNamesIn(normalizedNames).map { it.toModel() }
    }

    override fun save(watchlist: CompetitorWatchlist): CompetitorWatchlist {
        val now = Instant.now()
        val id = watchlist.id.ifBlank { UUID.randomUUID().toString() }
        val entity = CompetitorWatchlistEntity(
            id = id,
            name = watchlist.name,
            aliases = mapper.writeValueAsString(watchlist.aliases),
            tier = watchlist.tier,
            isActive = watchlist.isActive,
            createdAt = now,
            updatedAt = now
        )
        return repository.save(entity).toModel()
    }

    override fun update(watchlist: CompetitorWatchlist): CompetitorWatchlist {
        val entity = repository.findById(watchlist.id).orElseThrow {
            NotFoundException("Competitor watchlist not found: ${watchlist.id}")
        }
        // 변경 가능한 필드를 갱신한다.
        entity.name = watchlist.name
        entity.aliases = mapper.writeValueAsString(watchlist.aliases)
        entity.excludeKeywords = mapper.writeValueAsString(watchlist.excludeKeywords)
        entity.tier = watchlist.tier
        entity.isActive = watchlist.isActive
        entity.updatedAt = Instant.now()
        return repository.save(entity).toModel()
    }

    override fun delete(id: String) {
        repository.deleteById(id)
    }

    private fun CompetitorWatchlistEntity.toModel() = CompetitorWatchlist(
        id = id,
        name = name,
        aliases = runCatching { mapper.readValue<List<String>>(aliases) }.getOrDefault(emptyList()),
        excludeKeywords = runCatching { mapper.readValue<List<String>>(excludeKeywords) }.getOrDefault(emptyList()),
        tier = tier,
        isActive = isActive,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
