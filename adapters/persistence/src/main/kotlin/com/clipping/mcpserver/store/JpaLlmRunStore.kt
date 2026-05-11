package com.clipping.mcpserver.store

import com.clipping.mcpserver.entity.LlmRunEntity
import com.clipping.mcpserver.model.Language
import com.clipping.mcpserver.model.LlmRun
import com.clipping.mcpserver.model.RssItem
import com.clipping.mcpserver.repository.LlmRunRepository
import com.clipping.mcpserver.repository.RssItemRepository
import org.springframework.context.annotation.Primary
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * LLM 실행 이력 JPA 구현. JdbcLlmRunStore를 대체한다.
 */
@Repository
@Primary
class JpaLlmRunStore(
    private val repository: LlmRunRepository,
    private val jdbc: JdbcTemplate,
    private val rssItemRepository: RssItemRepository
) : LlmRunStore {

    override fun save(run: LlmRun): LlmRun {
        val id = run.id.ifBlank { UUID.randomUUID().toString() }
        val saved = run.copy(id = id, createdAt = Instant.now())
        // 기사 기반 실행은 rss_item이 속한 카테고리와 일치해야 한다.
        validateRunCategoryConsistency(saved)
        // saveAndFlush로 즉시 DB에 반영하여 JdbcTemplate 기반 조회와 일관성을 보장한다.
        repository.saveAndFlush(saved.toEntity())
        return saved
    }

    override fun findByCreatedAtBetween(from: Instant, to: Instant, categoryId: String?): List<LlmRun> {
        val entities = if (categoryId.isNullOrBlank()) {
            repository.findByCreatedAtBetween(from, to)
        } else {
            repository.findByCreatedAtBetweenAndCategoryId(from, to, categoryId)
        }
        return entities.map { it.toModel() }.sortedBy { it.createdAt }
    }

    @Transactional
    override fun deleteOlderThan(cutoff: Instant): Int =
        repository.deleteByCreatedAtBefore(cutoff)

    override fun sumTokensBySource(cutoff: Instant): Map<String, Triple<Int, Long, Long>> {
        // rss_items 조인으로 소스별 토큰 사용량을 집계한다.
        val rows = jdbc.queryForList(
            """
            SELECT ri.rss_source_id AS source_id,
                   COUNT(*) AS request_count,
                   COALESCE(SUM(lr.tokens_in), 0) AS tokens_in,
                   COALESCE(SUM(lr.tokens_out), 0) AS tokens_out
            FROM llm_runs lr
            JOIN rss_items ri ON ri.id = lr.rss_item_id
            WHERE lr.created_at >= ? AND lr.rss_item_id IS NOT NULL
            GROUP BY ri.rss_source_id
            """.trimIndent(),
            java.sql.Timestamp.from(cutoff)
        )
        return rows
            .mapNotNull { row ->
                val sourceId = row["source_id"] as? String ?: return@mapNotNull null
                val requestCount = (row["request_count"] as Number).toInt()
                val tokensIn = (row["tokens_in"] as Number).toLong()
                val tokensOut = (row["tokens_out"] as Number).toLong()
                sourceId to Triple(requestCount, tokensIn, tokensOut)
            }
            .toMap()
    }

    override fun sumCharsBetween(from: Instant, to: Instant): Pair<Long, Long> {
        val result = repository.sumCharsBetween(from, to)
        // JPQL 멀티컬럼 SELECT는 단일 행을 Object[]로 래핑해 반환한다.
        // result 자체가 [Object[sum1, sum2]] 형태이므로 내부 배열을 풀어야 한다.
        val row = if (result.isNotEmpty() && result[0] is Array<*>) {
            @Suppress("UNCHECKED_CAST")
            result[0] as Array<Any?>
        } else {
            result
        }
        val inputTotal = (row[0] as? Number)?.toLong() ?: 0L
        val outputTotal = (row[1] as? Number)?.toLong() ?: 0L
        return inputTotal to outputTotal
    }

    override fun sumBillableTokensBetween(from: Instant, to: Instant, categoryId: String?): Pair<Long, Long> {
        val params = mutableListOf<Any>(
            java.sql.Timestamp.from(from),
            java.sql.Timestamp.from(to)
        )
        val categoryCondition = if (categoryId.isNullOrBlank()) {
            ""
        } else {
            params += categoryId
            "AND category_id = ?"
        }
        // 예산 가드는 원본 row가 아니라 집계 토큰만 필요하므로 DB에서 합산한다.
        val row = jdbc.queryForMap(
            """
            SELECT
                COALESCE(SUM(COALESCE(tokens_in, CEILING(input_chars / 4.0))), 0) AS tokens_in,
                COALESCE(SUM(COALESCE(tokens_out, CEILING(output_chars / 4.0))), 0) AS tokens_out
            FROM llm_runs
            WHERE created_at >= ? AND created_at <= ?
              $categoryCondition
            """.trimIndent(),
            *params.toTypedArray()
        )
        val tokensIn = (row["tokens_in"] as? Number)?.toLong() ?: 0L
        val tokensOut = (row["tokens_out"] as? Number)?.toLong() ?: 0L
        return tokensIn to tokensOut
    }

    // ── private helpers ──

    private fun validateRunCategoryConsistency(run: LlmRun) {
        val rssItemId = run.rssItemId ?: return
        val linkedItem = findLinkedItem(rssItemId)
        if (!run.isLinkedTo(linkedItem)) {
            throw DataIntegrityViolationException(
                "llm_run category mismatch: runCategory=${run.categoryId}, rssItemCategory=${linkedItem.categoryId}, rssItemId=$rssItemId"
            )
        }
    }

    /** JPA 리포지토리로 rss_item을 조회하여 같은 영속성 컨텍스트에서 일관된 데이터를 참조한다. */
    private fun findLinkedItem(rssItemId: String): RssItem {
        val entity = rssItemRepository.findById(rssItemId).orElse(null)
            ?: throw DataIntegrityViolationException("rss_item not found for llm_run: rssItemId=$rssItemId")
        return RssItem(
            id = entity.id,
            title = entity.title,
            link = entity.link,
            language = Language.FOREIGN,
            categoryId = entity.categoryId
        )
    }

    private fun LlmRunEntity.toModel() = LlmRun(
        id = id,
        categoryId = categoryId,
        rssItemId = rssItemId,
        model = model,
        promptVersion = promptVersion,
        inputHash = inputHash,
        inputChars = inputChars,
        outputChars = outputChars,
        status = status,
        errorMessage = errorMessage,
        durationMs = durationMs,
        tokensIn = tokensIn,
        tokensOut = tokensOut,
        createdAt = createdAt
    )

    private fun LlmRun.toEntity() = LlmRunEntity(
        id = id,
        categoryId = categoryId,
        rssItemId = rssItemId,
        model = model,
        promptVersion = promptVersion,
        inputHash = inputHash,
        inputChars = inputChars,
        outputChars = outputChars,
        status = status,
        errorMessage = errorMessage,
        durationMs = durationMs,
        tokensIn = tokensIn,
        tokensOut = tokensOut,
        createdAt = createdAt
    )
}
