package com.ohmyclipping.store

import com.ohmyclipping.entity.SummaryFeedbackEntity
import com.ohmyclipping.error.InvalidInputException
import com.ohmyclipping.model.SummaryFeedback
import com.ohmyclipping.model.SummaryFeedbackHotSummary
import com.ohmyclipping.repository.SummaryFeedbackRepository
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

private const val FEEDBACK_TYPE_LIKE = "LIKE"
private const val FEEDBACK_TYPE_NEUTRAL = "NEUTRAL"
private const val FEEDBACK_TYPE_DISLIKE = "DISLIKE"

/**
 * 요약 피드백 JPA 구현. JdbcSummaryFeedbackStore를 대체한다.
 * findWeeklyHot은 가중 점수 집계 쿼리가 복잡하므로 JdbcTemplate을 병행한다.
 */
@Repository
@Primary
class JpaSummaryFeedbackStore(
    private val repository: SummaryFeedbackRepository,
    private val jdbc: JdbcTemplate
) : SummaryFeedbackStore {

    private val hotRowMapper = RowMapper<SummaryFeedbackHotSummary?> { rs, _ ->
        val summaryId = rs.getString("summary_id") ?: return@RowMapper null
        val lastFeedbackAt = rs.getTimestamp("last_feedback_at")?.toInstant() ?: return@RowMapper null
        SummaryFeedbackHotSummary(
            summaryId = summaryId,
            title = rs.getString("title") ?: "",
            sourceLink = rs.getString("source_link") ?: "",
            likeCount = rs.getInt("like_count"),
            neutralCount = rs.getInt("neutral_count"),
            dislikeCount = rs.getInt("dislike_count"),
            totalCount = rs.getInt("total_count"),
            score = rs.getDouble("score"),
            lastFeedbackAt = lastFeedbackAt
        )
    }

    override fun upsert(feedback: SummaryFeedback): SummaryFeedback {
        val safeType = normalizeType(feedback.feedbackType)
        // 기존 피드백이 있으면 갱신한다.
        val existing = repository.findBySummaryIdAndUserId(feedback.summaryId, feedback.userId)
        if (existing != null) {
            existing.feedbackType = safeType
            repository.save(existing)
            return feedback.copy(id = existing.id, feedbackType = safeType)
        }
        // 새 피드백을 생성한다.
        val id = feedback.id.ifBlank { UUID.randomUUID().toString() }
        val entity = SummaryFeedbackEntity(
            id = id,
            summaryId = feedback.summaryId,
            feedbackType = safeType,
            userId = feedback.userId,
            createdAt = feedback.createdAt
        )
        repository.save(entity)
        return feedback.copy(id = id, feedbackType = safeType)
    }

    override fun deleteBySummaryIdAndUserId(summaryId: String, userId: String): Boolean {
        // 존재 조회 후 삭제 — 감사 목적의 존재 여부 반환을 위해 2단 처리한다.
        val existing = repository.findBySummaryIdAndUserId(summaryId, userId) ?: return false
        repository.delete(existing)
        return true
    }

    /**
     * 가중 점수 기반 인기 요약 목록을 조회한다.
     * LIKE*2 - DISLIKE + NEUTRAL/10 공식을 사용한다.
     */
    override fun findWeeklyHot(
        from: Instant,
        to: Instant,
        limit: Int,
        categoryId: String?
    ): List<SummaryFeedbackHotSummary> {
        val safeLimit = limit.coerceIn(1, 100)
        return if (categoryId.isNullOrBlank()) {
            jdbc.query(
                """
                SELECT
                  f.summary_id,
                  COALESCE(bs.translated_title, bs.original_title) AS title,
                  bs.source_link,
                  SUM(CASE WHEN f.feedback_type = ? THEN 1 ELSE 0 END) AS like_count,
                  SUM(CASE WHEN f.feedback_type = ? THEN 1 ELSE 0 END) AS neutral_count,
                  SUM(CASE WHEN f.feedback_type = ? THEN 1 ELSE 0 END) AS dislike_count,
                  COUNT(*) AS total_count,
                  (
                    SUM(CASE WHEN f.feedback_type = ? THEN 1 ELSE 0 END) * 2
                    - SUM(CASE WHEN f.feedback_type = ? THEN 1 ELSE 0 END)
                    + (SUM(CASE WHEN f.feedback_type = ? THEN 1 ELSE 0 END) * 1.0) / 10.0
                  ) AS score,
                  MAX(f.created_at) AS last_feedback_at
                FROM summary_feedback f
                INNER JOIN batch_summaries bs ON bs.id = f.summary_id
                WHERE f.created_at >= ? AND f.created_at < ?
                GROUP BY f.summary_id, COALESCE(bs.translated_title, bs.original_title), bs.source_link
                ORDER BY score DESC, total_count DESC
                LIMIT ?
                """.trimIndent(),
                hotRowMapper,
                FEEDBACK_TYPE_LIKE,
                FEEDBACK_TYPE_NEUTRAL,
                FEEDBACK_TYPE_DISLIKE,
                FEEDBACK_TYPE_LIKE,
                FEEDBACK_TYPE_DISLIKE,
                FEEDBACK_TYPE_NEUTRAL,
                java.sql.Timestamp.from(from),
                java.sql.Timestamp.from(to),
                safeLimit
            ).mapNotNull { it }
        } else {
            jdbc.query(
                """
                SELECT
                  f.summary_id,
                  COALESCE(bs.translated_title, bs.original_title) AS title,
                  bs.source_link,
                  SUM(CASE WHEN f.feedback_type = ? THEN 1 ELSE 0 END) AS like_count,
                  SUM(CASE WHEN f.feedback_type = ? THEN 1 ELSE 0 END) AS neutral_count,
                  SUM(CASE WHEN f.feedback_type = ? THEN 1 ELSE 0 END) AS dislike_count,
                  COUNT(*) AS total_count,
                  (
                    SUM(CASE WHEN f.feedback_type = ? THEN 1 ELSE 0 END) * 2
                    - SUM(CASE WHEN f.feedback_type = ? THEN 1 ELSE 0 END)
                    + (SUM(CASE WHEN f.feedback_type = ? THEN 1 ELSE 0 END) * 1.0) / 10.0
                  ) AS score,
                  MAX(f.created_at) AS last_feedback_at
                FROM summary_feedback f
                INNER JOIN batch_summaries bs ON bs.id = f.summary_id
                WHERE f.created_at >= ? AND f.created_at < ? AND bs.category_id = ?
                GROUP BY f.summary_id, COALESCE(bs.translated_title, bs.original_title), bs.source_link
                ORDER BY score DESC, total_count DESC
                LIMIT ?
                """.trimIndent(),
                hotRowMapper,
                FEEDBACK_TYPE_LIKE,
                FEEDBACK_TYPE_NEUTRAL,
                FEEDBACK_TYPE_DISLIKE,
                FEEDBACK_TYPE_LIKE,
                FEEDBACK_TYPE_DISLIKE,
                FEEDBACK_TYPE_NEUTRAL,
                java.sql.Timestamp.from(from),
                java.sql.Timestamp.from(to),
                categoryId,
                safeLimit
            ).mapNotNull { it }
        }
    }

    override fun findByUserId(userId: String, limit: Int): List<SummaryFeedback> {
        // export 안전망 — limit 이 0 이하이면 빈 목록.
        if (limit <= 0) return emptyList()
        val pageable = org.springframework.data.domain.PageRequest.of(0, limit)
        return repository.findByUserIdOrderByCreatedAtDesc(userId, pageable).map { entity ->
            SummaryFeedback(
                id = entity.id,
                summaryId = entity.summaryId,
                feedbackType = entity.feedbackType,
                userId = entity.userId,
                createdAt = entity.createdAt
            )
        }
    }

    private fun normalizeType(value: String): String {
        return when (value.uppercase()) {
            FEEDBACK_TYPE_LIKE,
            FEEDBACK_TYPE_NEUTRAL,
            FEEDBACK_TYPE_DISLIKE -> value.uppercase()
            // 허용되지 않은 피드백 타입 입력은 도메인 예외로 처리
            else -> throw InvalidInputException("Unsupported feedback type: $value")
        }
    }
}
