package com.clipping.mcpserver.service

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.time.Instant

/**
 * DUAL_SECTION digest 의 "상단 범례 (legend)" 노출 횟수를 카테고리별로 추적한다.
 *
 * UX 요구:
 *  - DUAL_SECTION 모드 발송 시 legend 는 **최초 3회까지만 full** 형태, 이후엔 footer link 만 유지 (D7).
 *  - 카운터는 `category_feature_flags.dual_legend_display_count` 에 누적, `dual_legend_shown_at` 에 최신 기록.
 *  - 동시에 같은 카테고리로 두 번의 digest 가 발송되는 race 는 드물지만, race-safe upsert 로 방어한다.
 */
@Service
class CategoryDigestStateService(private val jdbc: JdbcTemplate) {

    /**
     * 현재까지 노출된 legend 횟수. 행이 없거나 컬럼이 NULL 이면 0.
     */
    fun getLegendDisplayCount(categoryId: String): Int {
        return jdbc.query(
            "SELECT dual_legend_display_count FROM category_feature_flags WHERE category_id = ?",
            { rs, _ -> rs.getInt("dual_legend_display_count") },
            categoryId
        ).firstOrNull() ?: 0
    }

    /**
     * 노출 카운터를 1 증가시키고 `dual_legend_shown_at` 을 현재 시각으로 갱신.
     *
     * race-safe: UPDATE → row 가 없으면 INSERT; INSERT 가 `DataIntegrityViolationException` 이면
     * 상대 스레드가 먼저 INSERT 완료한 것이므로 UPDATE 재시도.
     *
     * @return 증가 후 카운트
     */
    fun incrementLegendDisplayCount(categoryId: String): Int {
        val updated = jdbc.update(
            """
            UPDATE category_feature_flags
               SET dual_legend_display_count = dual_legend_display_count + 1,
                   dual_legend_shown_at = CURRENT_TIMESTAMP,
                   updated_at = CURRENT_TIMESTAMP
             WHERE category_id = ?
            """.trimIndent(),
            categoryId
        )
        if (updated == 0) {
            try {
                jdbc.update(
                    """
                    INSERT INTO category_feature_flags(
                        category_id, account_based_digest_enabled,
                        dual_legend_display_count, dual_legend_shown_at
                    )
                    VALUES (?, FALSE, 1, CURRENT_TIMESTAMP)
                    """.trimIndent(),
                    categoryId
                )
                return 1
            } catch (e: DataIntegrityViolationException) {
                // 동시 INSERT race — 상대가 먼저 INSERT 했으니 UPDATE 로 수렴.
                jdbc.update(
                    """
                    UPDATE category_feature_flags
                       SET dual_legend_display_count = dual_legend_display_count + 1,
                           dual_legend_shown_at = CURRENT_TIMESTAMP,
                           updated_at = CURRENT_TIMESTAMP
                     WHERE category_id = ?
                    """.trimIndent(),
                    categoryId
                )
            }
        }
        return getLegendDisplayCount(categoryId)
    }

    /**
     * legend 를 full 로 보여줘야 하는지 (처음 3회까지).
     * 3 이상이면 false — footer link 만 렌더.
     */
    fun shouldShowFullLegend(categoryId: String): Boolean {
        return getLegendDisplayCount(categoryId) < FULL_LEGEND_THRESHOLD
    }

    /**
     * 최근 legend 노출 시각. 한 번도 노출 안 됐으면 null.
     */
    fun getLegendLastShownAt(categoryId: String): Instant? {
        return jdbc.query(
            "SELECT dual_legend_shown_at FROM category_feature_flags WHERE category_id = ?",
            { rs, _ -> rs.getTimestamp("dual_legend_shown_at")?.toInstant() },
            categoryId
        ).firstOrNull()
    }

    companion object {
        /** full legend 를 보여줄 최대 횟수. 이 값 이상 노출되면 축약. */
        const val FULL_LEGEND_THRESHOLD = 3
    }
}
