package com.ohmyclipping.store

import com.ohmyclipping.model.DigestDiffLog
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Shadow mode 기간 동안 legacy digest 와 새 account-based digest 의 결과를 나란히 기록.
 *
 * - 한 카테고리-하루 조합 당 1 row — `uq_ddl_category_date` UNIQUE 제약으로 보호.
 * - 중복 insert 는 조용히 무시 (idempotent): `DataIntegrityViolationException` catch 해서 debug log 만 남김.
 * - 읽기는 `findByCategoryAndDateRange` 로 관리자 diff 페이지 (D10) 에서 범위 조회.
 */
@Repository
class DigestDiffLogStore(private val jdbc: JdbcTemplate) {

    private val log = KotlinLogging.logger {}

    /**
     * 새 diff row 를 삽입한다. 이미 같은 (categoryId, digestDate) 로 저장됐다면 조용히 건너뜀.
     */
    fun insertIfAbsent(
        categoryId: String,
        digestDate: LocalDate,
        legacySummary: String?,
        newSummary: String?,
        newMode: String?,
        sectionsCount: Int,
        articlesCount: Int,
        crossMatchCount: Int,
    ) {
        try {
            jdbc.update(
                """
                INSERT INTO digest_diff_log(
                    id, category_id, digest_date, legacy_summary, new_summary, new_mode,
                    sections_count, articles_count, cross_match_count
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                UUID.randomUUID().toString(),
                categoryId,
                java.sql.Date.valueOf(digestDate),
                legacySummary,
                newSummary,
                newMode,
                sectionsCount,
                articlesCount,
                crossMatchCount,
            )
        } catch (e: DataIntegrityViolationException) {
            // UNIQUE(category_id, digest_date) 위반 — 오늘 이미 기록됨. idempotent 하게 무시.
            log.debug { "digest_diff_log already exists for $categoryId @ $digestDate" }
        }
    }

    /**
     * 지정 카테고리의 from~to (양끝 포함) 범위 내 diff 행들을 최신 순으로 반환.
     */
    fun findByCategoryAndDateRange(
        categoryId: String,
        from: LocalDate,
        to: LocalDate,
    ): List<DigestDiffLog> {
        return jdbc.query(
            """
            SELECT id, category_id, digest_date, legacy_summary, new_summary, new_mode,
                   sections_count, articles_count, cross_match_count, created_at
              FROM digest_diff_log
             WHERE category_id = ?
               AND digest_date >= ?
               AND digest_date <= ?
             ORDER BY digest_date DESC, created_at DESC
            """.trimIndent(),
            { rs, _ -> mapRow(rs) },
            categoryId,
            java.sql.Date.valueOf(from),
            java.sql.Date.valueOf(to),
        )
    }

    /**
     * 지정 카테고리의 from~to 범위 내 diff 행들을 DB 페이지네이션으로 조회한다.
     */
    fun findByCategoryAndDateRange(
        categoryId: String,
        from: LocalDate,
        to: LocalDate,
        offset: Int,
        limit: Int,
    ): List<DigestDiffLog> {
        return jdbc.query(
            """
            SELECT id, category_id, digest_date, legacy_summary, new_summary, new_mode,
                   sections_count, articles_count, cross_match_count, created_at
              FROM digest_diff_log
             WHERE category_id = ?
               AND digest_date >= ?
               AND digest_date <= ?
             ORDER BY digest_date DESC, created_at DESC
             LIMIT ? OFFSET ?
            """.trimIndent(),
            { rs, _ -> mapRow(rs) },
            categoryId,
            java.sql.Date.valueOf(from),
            java.sql.Date.valueOf(to),
            limit,
            offset,
        )
    }

    /**
     * 지정 카테고리의 from~to 범위 내 diff 행 수를 반환한다.
     */
    fun countByCategoryAndDateRange(
        categoryId: String,
        from: LocalDate,
        to: LocalDate,
    ): Int {
        return jdbc.queryForObject(
            """
            SELECT COUNT(*)
              FROM digest_diff_log
             WHERE category_id = ?
               AND digest_date >= ?
               AND digest_date <= ?
            """.trimIndent(),
            Int::class.java,
            categoryId,
            java.sql.Date.valueOf(from),
            java.sql.Date.valueOf(to),
        ) ?: 0
    }

    private fun mapRow(rs: java.sql.ResultSet): DigestDiffLog =
        DigestDiffLog(
            id = rs.getString("id"),
            categoryId = rs.getString("category_id"),
            digestDate = rs.getDate("digest_date").toLocalDate(),
            legacySummary = rs.getString("legacy_summary"),
            newSummary = rs.getString("new_summary"),
            newMode = rs.getString("new_mode"),
            sectionsCount = rs.getInt("sections_count"),
            articlesCount = rs.getInt("articles_count"),
            crossMatchCount = rs.getInt("cross_match_count"),
            createdAt = rs.getTimestamp("created_at")?.toInstant() ?: Instant.EPOCH,
        )
}
