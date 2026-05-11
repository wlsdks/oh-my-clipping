package com.ohmyclipping.store

import com.ohmyclipping.entity.TrendSnapshotEntity
import com.ohmyclipping.model.TrendPeriodType
import com.ohmyclipping.model.TrendRegionType
import com.ohmyclipping.model.TrendSnapshot
import com.ohmyclipping.model.TrendSnapshotStatus
import com.ohmyclipping.repository.TrendSnapshotRepository
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID

/**
 * 트렌드 스냅샷 JPA 구현. JdbcTrendSnapshotStore를 대체한다.
 * 동적 다중 필터 조합 쿼리는 JdbcTemplate을 사용한다.
 */
@Repository
@Primary
class JpaTrendSnapshotStore(
    private val repository: TrendSnapshotRepository,
    private val jdbc: JdbcTemplate
) : TrendSnapshotStore {

    private val mapper = jacksonObjectMapper()

    override fun findById(id: String): TrendSnapshot? =
        repository.findById(id).orElse(null)?.toModel()

    /**
     * 동적 필터 조합으로 스냅샷 목록을 조회한다.
     * 필터 4개의 조합이 다양하므로 JdbcTemplate으로 동적 SQL을 구성한다.
     */
    override fun list(
        periodType: TrendPeriodType?,
        categoryId: String?,
        regionType: TrendRegionType?,
        status: TrendSnapshotStatus?,
        limit: Int
    ): List<TrendSnapshot> {
        val safeLimit = limit.coerceIn(1, 300)
        val conditions = mutableListOf<String>()
        val args = mutableListOf<Any>()

        periodType?.let { conditions += "period_type = ?"; args += it.name }
        categoryId?.let { conditions += "category_id = ?"; args += it }
        regionType?.let { conditions += "region_type = ?"; args += it.name }
        status?.let { conditions += "status = ?"; args += it.name }

        val whereClause = if (conditions.isEmpty()) "" else "WHERE ${conditions.joinToString(" AND ")}"
        args += safeLimit
        return jdbc.query(
            """
            SELECT * FROM clipping_trend_snapshots
            $whereClause
            ORDER BY created_at DESC
            LIMIT ?
            """.trimIndent(),
            { rs, _ -> mapListRowOrNull(rs) },
            *args.toTypedArray()
        ).mapNotNull { it }
    }

    override fun save(snapshot: TrendSnapshot): TrendSnapshot {
        val now = Instant.now()
        val id = snapshot.id.ifBlank { UUID.randomUUID().toString() }

        val existing = repository.findById(id).orElse(null)
        if (existing != null) {
            // 기존 스냅샷을 갱신한다.
            existing.periodType = snapshot.periodType.name
            existing.snapshotFrom = snapshot.snapshotFrom
            existing.snapshotTo = snapshot.snapshotTo
            existing.categoryId = snapshot.categoryId
            existing.categoryName = snapshot.categoryName
            existing.regionType = snapshot.regionType.name
            existing.title = snapshot.title
            existing.summary = snapshot.summary
            existing.keySignals = mapper.writeValueAsString(snapshot.keySignals)
            existing.actionItems = mapper.writeValueAsString(snapshot.actionItems)
            existing.sourceCount = snapshot.sourceCount
            existing.itemCount = snapshot.itemCount
            existing.status = snapshot.status.name
            existing.templateType = snapshot.templateType
            existing.generatedBy = snapshot.generatedBy
            existing.publishedAt = snapshot.publishedAt
            existing.updatedAt = now
            return repository.save(existing).toModel()
        }

        // 새 스냅샷을 생성한다.
        val entity = TrendSnapshotEntity(
            id = id,
            periodType = snapshot.periodType.name,
            snapshotFrom = snapshot.snapshotFrom,
            snapshotTo = snapshot.snapshotTo,
            categoryId = snapshot.categoryId,
            categoryName = snapshot.categoryName,
            regionType = snapshot.regionType.name,
            title = snapshot.title,
            summary = snapshot.summary,
            keySignals = mapper.writeValueAsString(snapshot.keySignals),
            actionItems = mapper.writeValueAsString(snapshot.actionItems),
            sourceCount = snapshot.sourceCount,
            itemCount = snapshot.itemCount,
            status = snapshot.status.name,
            templateType = snapshot.templateType,
            generatedBy = snapshot.generatedBy,
            publishedAt = snapshot.publishedAt,
            createdAt = snapshot.createdAt,
            updatedAt = now
        )
        return repository.save(entity).toModel()
    }

    private fun TrendSnapshotEntity.toModel() = TrendSnapshot(
        id = id,
        periodType = TrendPeriodType.valueOf(periodType),
        snapshotFrom = snapshotFrom,
        snapshotTo = snapshotTo,
        categoryId = categoryId,
        categoryName = categoryName,
        regionType = TrendRegionType.valueOf(regionType),
        title = title,
        summary = summary,
        keySignals = parseJsonList(keySignals),
        actionItems = parseJsonList(actionItems),
        sourceCount = sourceCount,
        itemCount = itemCount,
        status = TrendSnapshotStatus.valueOf(status),
        templateType = templateType ?: "DETAILED",
        generatedBy = generatedBy,
        publishedAt = publishedAt,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun parseJsonList(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { mapper.readValue<List<String>>(raw) }.getOrDefault(emptyList())
    }

    private fun mapListRowOrNull(rs: ResultSet): TrendSnapshot? {
        val id = rs.getString("id") ?: return null
        val periodType = parseEnum<TrendPeriodType>(rs.getString("period_type")) ?: return null
        val snapshotFrom = rs.getDate("snapshot_from")?.toLocalDate() ?: return null
        val snapshotTo = rs.getDate("snapshot_to")?.toLocalDate() ?: return null
        val regionType = parseEnum<TrendRegionType>(rs.getString("region_type")) ?: return null
        val status = parseEnum<TrendSnapshotStatus>(rs.getString("status")) ?: return null
        val createdAt = rs.getTimestamp("created_at")?.toInstant() ?: Instant.EPOCH
        val updatedAt = rs.getTimestamp("updated_at")?.toInstant() ?: createdAt

        return TrendSnapshot(
            id = id,
            periodType = periodType,
            snapshotFrom = snapshotFrom,
            snapshotTo = snapshotTo,
            categoryId = rs.getString("category_id"),
            categoryName = rs.getString("category_name") ?: "",
            regionType = regionType,
            title = rs.getString("title") ?: "",
            summary = rs.getString("summary") ?: "",
            keySignals = parseJsonList(rs.getString("key_signals")),
            actionItems = parseJsonList(rs.getString("action_items")),
            sourceCount = rs.getInt("source_count"),
            itemCount = rs.getInt("item_count"),
            status = status,
            templateType = rs.getString("template_type") ?: "DETAILED",
            generatedBy = rs.getString("generated_by"),
            publishedAt = rs.getTimestamp("published_at")?.toInstant(),
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    private inline fun <reified T : Enum<T>> parseEnum(value: String?): T? =
        if (value.isNullOrBlank()) {
            null
        } else {
            runCatching { enumValueOf<T>(value) }.getOrNull()
        }
}
