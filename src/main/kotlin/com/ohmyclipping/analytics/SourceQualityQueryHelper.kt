package com.ohmyclipping.analytics

import com.ohmyclipping.service.dto.SourceQualityRow
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Component
import java.sql.Timestamp
import java.time.Instant

/**
 * RSS 소스별 클릭률/👍률 집계 helper.
 *
 * 분모 = is_sent_to_slack = TRUE 인 기사 수 (발송된 것만).
 * 분자 (클릭) = DISTINCT (user_id, summary_id) — 같은 사용자 반복 클릭 1회로 카운트.
 * 수동 URL (rss_items.rss_source_id IS NULL) 은 "(수동 URL)" 버킷으로 집계.
 *
 * 상태 라벨:
 *   - click_rate > 15%  → "normal"
 *   - click_rate < 5%   → "review"
 *   - 그 사이            → "default"
 *   - 발송 < 10 (samples 부족) → "default" 로 폴백
 */
@Component
class SourceQualityQueryHelper(
    private val jdbc: JdbcTemplate,
) {

    fun sourceQuality(from: Instant, to: Instant): List<SourceQualityRow> {
        // H2/Postgres 호환: 문자열 연결은 `||` 사용. distinct (user, summary) 튜플로 중복 카운트 방지.
        // `src_is_active` / `src_updated_at` 은 비활성 소스 기본 숨김 필터 및 optimistic-concurrency
        // (activate/deactivate mutation 의 expectedUpdatedAt) 를 위해 carry. 수동 URL 은 rs.* 가 NULL 이라
        // nullable-safe cast (§1.3) 로 fallback (true / EPOCH) 처리한다.
        val sql = """
            SELECT
              rs.id AS source_id,
              COALESCE(rs.name, '(수동 URL)') AS source_name,
              rs.is_active AS src_is_active,
              rs.updated_at AS src_updated_at,
              COUNT(DISTINCT bs.id) AS delivered,
              COUNT(DISTINCT CASE WHEN ue.user_id IS NOT NULL
                                  THEN ue.user_id || ':' || ue.summary_id END) AS unique_user_clicks,
              SUM(CASE WHEN sf.feedback_type = 'LIKE' THEN 1 ELSE 0 END) AS likes,
              SUM(CASE WHEN sf.feedback_type = 'DISLIKE' THEN 1 ELSE 0 END) AS dislikes
            FROM batch_summaries bs
            JOIN rss_items ri ON ri.id = bs.rss_item_id
            LEFT JOIN rss_sources rs ON rs.id = ri.rss_source_id
            LEFT JOIN user_events ue
              ON ue.summary_id = bs.id AND ue.event_type = 'article_click'
            LEFT JOIN summary_feedback sf ON sf.summary_id = bs.id
            WHERE bs.is_sent_to_slack = TRUE
              AND bs.created_at >= ? AND bs.created_at < ?
            GROUP BY rs.id, rs.name, rs.is_active, rs.updated_at
        """.trimIndent()

        return jdbc.query(sql, RowMapper { rs, _ ->
            val delivered = rs.getLong("delivered")
            val clicks = rs.getLong("unique_user_clicks")
            val likes = rs.getLong("likes")
            val dislikes = rs.getLong("dislikes")
            val clickRate = if (delivered > 0) roundPct(100.0 * clicks / delivered) else null
            val denomFb = likes + dislikes
            val likeRate = if (denomFb > 0) roundPct(100.0 * likes / denomFb) else null

            // 샘플 10건 미만은 신뢰할 수 없으므로 default 로 폴백
            val status = when {
                delivered < 10 -> "default"
                clickRate == null -> "default"
                clickRate > 15.0 -> "normal"
                clickRate < 5.0 -> "review"
                else -> "default"
            }

            // 수동 URL 경로는 rs.* 가 NULL — null-safe cast 로 받아 명시적 fallback.
            val isActive = (rs.getObject("src_is_active") as? Boolean) ?: true
            val updatedAt = rs.getTimestamp("src_updated_at")?.toInstant() ?: Instant.EPOCH

            SourceQualityRow(
                sourceId = rs.getString("source_id"),
                sourceName = rs.getString("source_name"),
                delivered = delivered,
                uniqueUserClicks = clicks,
                clickRatePct = clickRate,
                likes = likes,
                dislikes = dislikes,
                likeRatePct = likeRate,
                statusLabel = status,
                isActive = isActive,
                updatedAt = updatedAt,
            )
        }, Timestamp.from(from), Timestamp.from(to))
    }

    private fun roundPct(value: Double): Double = Math.round(value * 100) / 100.0
}
