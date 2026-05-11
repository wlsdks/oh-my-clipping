package com.ohmyclipping.analytics

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.sql.Timestamp
import java.time.Instant

/**
 * 재방문 집계 helper (on-demand).
 *
 * 재방문 정의 (Phase 3 Discovery §6.4):
 *   같은 (user_id, summary_id) 에 대해 최초 클릭 이후 **24시간 이후 ~ 30일 이내** 에
 *   추가 클릭이 발생하면 **1건** 으로 카운트한다.
 *   한 조합이 여러 번 추가 클릭돼도 1 로만 센다 (조합별 1 카운트).
 *
 * 아직 dashboard 가 없으므로 SQL 실행 용도 (후속 PR4 가 재사용).
 */
@Component
class ReVisitQueryHelper(
    private val jdbc: JdbcTemplate,
) {

    /**
     * [from, to) 범위 내 발생한 "첫 클릭 + 조건 만족 추가 클릭" 쌍의 개수.
     *
     * @param from 윈도우 시작 (inclusive)
     * @param to 윈도우 끝 (exclusive)
     */
    fun countRevisits(from: Instant, to: Instant): Int {
        // H2/Postgres 양쪽 호환: JDBC escape 의 TIMESTAMPADD 사용
        //   - H2: {fn TIMESTAMPADD(SQL_TSI_HOUR, n, ts)} 지원
        //   - Postgres JDBC 드라이버도 JDBC escape 해석
        // 두 dialect 모두 동일 쿼리로 동작.
        //
        // 알고리즘:
        //   1. first_clicks: (user, summary) 별 [from, to) 범위 내 최초 클릭 시각
        //   2. revisits: first_at 의 24h 이후 ~ 30일 이내에 추가 클릭이 하나라도 있으면 1
        //   3. COUNT: distinct (user, summary) 조합 개수
        val sql = """
            WITH first_clicks AS (
                SELECT user_id, summary_id, MIN(created_at) AS first_at
                FROM user_events
                WHERE event_type = 'article_click'
                  AND created_at >= ? AND created_at < ?
                GROUP BY user_id, summary_id
            ),
            revisits AS (
                SELECT DISTINCT f.user_id, f.summary_id
                FROM first_clicks f
                JOIN user_events ue
                  ON ue.user_id = f.user_id
                  AND ue.summary_id = f.summary_id
                  AND ue.event_type = 'article_click'
                  AND ue.created_at > {fn TIMESTAMPADD(SQL_TSI_HOUR, 24, f.first_at)}
                  AND ue.created_at <= {fn TIMESTAMPADD(SQL_TSI_DAY, 30, f.first_at)}
            )
            SELECT COUNT(*) FROM revisits
        """.trimIndent()

        return jdbc.queryForObject(
            sql,
            Int::class.java,
            Timestamp.from(from),
            Timestamp.from(to)
        ) ?: 0
    }
}
