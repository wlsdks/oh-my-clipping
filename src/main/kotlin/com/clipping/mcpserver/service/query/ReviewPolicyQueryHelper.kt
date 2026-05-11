package com.clipping.mcpserver.service.query

import com.clipping.mcpserver.service.dto.ReviewPolicyStatus
import com.clipping.mcpserver.service.dto.ScoreDistribution
import com.clipping.mcpserver.service.dto.ScoreDistributionBucket
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.Instant
import java.util.Locale

/**
 * 관리자 대시보드 `/admin/review-queue` 에 표시할 카테고리별 리뷰 정책 현황을 집계하는 helper.
 *
 * 단일 `getPolicyStatus()` 호출로 모든 활성 카테고리의 현황을 반환한다. 내부적으로 두 번의
 * JDBC 질의를 수행한다:
 *  1) 카테고리 × 리뷰 정책 메트릭 (pending, 7일 처리율, auto/manual 구분, avg score, last reviewed)
 *  2) 카테고리별 `batch_summaries.event_type` 분포 — 1번 결과의 categoryId 목록을 IN 조건으로 한 번에.
 *
 * H2 (MODE=PostgreSQL) 와 PostgreSQL 양쪽에서 동작하도록 `INTERVAL` 리터럴 대신 파라미터 바인딩된
 * [Timestamp] 를 사용한다 (AGENTS.md §1.5 방언 규칙).
 */
@Component
class ReviewPolicyQueryHelper(
    private val jdbc: JdbcTemplate,
) {

    /**
     * 활성 카테고리(`is_active = TRUE`) 전체의 리뷰 정책 현황을 반환한다.
     *
     * - 리뷰 항목이 전혀 없는 카테고리도 포함(pending=0, avgScore=0.0f, eventTypeDistribution={}).
     * - `clipping_category_rules` 가 없는 카테고리는 `autoApproveThreshold`, `reviewThreshold` 가 모두 null.
     * - 7일 기준점은 질의 시점 기준 `now - 7 days`. 타임존 고정된 Instant 로 파라미터화.
     */
    @Transactional(readOnly = true)
    fun getPolicyStatus(): List<ReviewPolicyStatus> {
        // 7일 기준 시각. INTERVAL 리터럴은 H2 미지원이라 파라미터 바인딩.
        val sevenDaysAgo = Timestamp.from(Instant.now().minusSeconds(SEVEN_DAYS_SECONDS))

        // 카테고리 + 규칙 + pending + 7일 처리 서브쿼리 조인. 좌측 JOIN 으로 빈 카테고리도 포함.
        val sql = """
            SELECT
              c.id AS category_id,
              c.name AS category_name,
              cr.auto_approve_threshold AS auto_approve_threshold,
              cr.review_threshold AS review_threshold,
              COALESCE(pr.pending_count, 0) AS pending_count,
              COALESCE(p7.total_processed, 0) AS total_processed,
              COALESCE(p7.auto_approved, 0) AS auto_approved,
              COALESCE(p7.manually_reviewed, 0) AS manually_reviewed,
              COALESCE(avg_s.avg_score, 0) AS avg_score,
              last_r.last_reviewed_at AS last_reviewed_at
            FROM batch_categories c
            LEFT JOIN clipping_category_rules cr ON cr.category_id = c.id
            LEFT JOIN (
                SELECT ri.category_id, COUNT(*) AS pending_count
                FROM clipping_review_items ri
                WHERE ri.status = 'REVIEW'
                GROUP BY ri.category_id
            ) pr ON pr.category_id = c.id
            LEFT JOIN (
                SELECT
                  ri.category_id,
                  COUNT(*) AS total_processed,
                  SUM(CASE WHEN ri.reviewed_by = 'policy-auto' THEN 1 ELSE 0 END) AS auto_approved,
                  SUM(CASE WHEN ri.reviewed_by IS NOT NULL
                             AND ri.reviewed_by <> 'policy-auto' THEN 1 ELSE 0 END) AS manually_reviewed
                FROM clipping_review_items ri
                WHERE ri.reviewed_at IS NOT NULL
                  AND ri.reviewed_at >= ?
                  AND ri.status <> 'REVIEW'
                GROUP BY ri.category_id
            ) p7 ON p7.category_id = c.id
            LEFT JOIN (
                SELECT ri.category_id, AVG(bs.importance_score) AS avg_score
                FROM clipping_review_items ri
                JOIN batch_summaries bs ON bs.id = ri.summary_id
                GROUP BY ri.category_id
            ) avg_s ON avg_s.category_id = c.id
            LEFT JOIN (
                SELECT ri.category_id, MAX(ri.reviewed_at) AS last_reviewed_at
                FROM clipping_review_items ri
                WHERE ri.reviewed_at IS NOT NULL
                GROUP BY ri.category_id
            ) last_r ON last_r.category_id = c.id
            WHERE c.is_active = TRUE
        """.trimIndent()

        data class PartialRow(
            val categoryId: String,
            val categoryName: String,
            val autoApproveThreshold: Double?,
            val reviewThreshold: Double?,
            val pendingReviewCount: Int,
            val last7DaysProcessed: Int,
            val last7DaysAutoApproved: Int,
            val last7DaysManuallyReviewed: Int,
            val avgScore: Float,
            val lastReviewedAt: Instant?,
        )

        // nullable 컬럼은 getObject 후 as? 로 안전하게 캐스팅 (AGENTS.md §1.3 Nullable guard).
        val partials = jdbc.query(sql, RowMapper { rs, _ ->
            PartialRow(
                categoryId = rs.getString("category_id"),
                categoryName = rs.getString("category_name"),
                autoApproveThreshold = (rs.getObject("auto_approve_threshold") as? Number)?.toDouble(),
                reviewThreshold = (rs.getObject("review_threshold") as? Number)?.toDouble(),
                pendingReviewCount = rs.getInt("pending_count"),
                last7DaysProcessed = rs.getInt("total_processed"),
                last7DaysAutoApproved = rs.getInt("auto_approved"),
                last7DaysManuallyReviewed = rs.getInt("manually_reviewed"),
                avgScore = (rs.getObject("avg_score") as? Number)?.toFloat() ?: 0.0f,
                lastReviewedAt = rs.getTimestamp("last_reviewed_at")?.toInstant(),
            )
        }, sevenDaysAgo)

        if (partials.isEmpty()) return emptyList()

        // 카테고리별 event_type 분포를 한 번의 질의로 수집 — N+1 회피.
        val distributionByCategory = fetchEventTypeDistribution(partials.map { it.categoryId })

        return partials.map { p ->
            ReviewPolicyStatus(
                categoryId = p.categoryId,
                categoryName = p.categoryName,
                autoApproveThreshold = p.autoApproveThreshold,
                reviewThreshold = p.reviewThreshold,
                pendingReviewCount = p.pendingReviewCount,
                last7DaysProcessed = p.last7DaysProcessed,
                last7DaysAutoApproved = p.last7DaysAutoApproved,
                last7DaysManuallyReviewed = p.last7DaysManuallyReviewed,
                avgScore = p.avgScore,
                eventTypeDistribution = distributionByCategory[p.categoryId] ?: emptyMap(),
                lastReviewedAt = p.lastReviewedAt,
            )
        }
    }

    /**
     * `batch_summaries.importance_score` 의 10 버킷 히스토그램을 집계한다.
     *
     * 대상 기간은 `now - [days] days` 이후 생성된 summary 이며, [categoryId] 가 지정되면 해당
     * 카테고리만, null 이면 전체 카테고리를 대상으로 한다. summary 가 0 건이어도 버킷 배열은
     * 항상 10 개(`count=0`) 를 반환한다.
     *
     * 버킷 경계는 `FLOOR(importance_score * 10)` — `[0.0, 0.1), [0.1, 0.2), …, [0.9, 1.0]` 로
     * 분할하며, `importance_score = 1.0` 은 9 번 버킷(0.9-1.0) 에 포함되도록 `coerceIn(0, 9)` 로
     * 정규화한다. 중앙값은 버킷 누적 합산 후 해당 버킷의 중심점 `(idx + 0.5) / 10` 을 반환한다.
     *
     * @param categoryId 특정 카테고리 필터. null 이면 전체 집계.
     * @param days 대상 기간(일 단위, 현재부터 과거).
     */
    @Transactional(readOnly = true)
    fun getScoreDistribution(categoryId: String?, days: Int): ScoreDistribution {
        // INTERVAL 리터럴은 H2 미지원 — Timestamp 로 바인딩 (AGENTS.md §1.5 방언 규칙).
        val since = Timestamp.from(Instant.now().minusSeconds(days.toLong() * ONE_DAY_SECONDS))

        // 버킷 집계 질의 — categoryId 여부에 따라 WHERE 절과 파라미터를 분기.
        val bucketSql = buildBucketQuery(categoryId)
        val bucketArgs: Array<Any> =
            if (categoryId != null) arrayOf(since, categoryId) else arrayOf(since)

        val counts = IntArray(BUCKET_COUNT)
        var total = 0
        jdbc.query(bucketSql, { rs ->
            // importance_score = 1.0 → FLOOR = 10 이 되므로 9 로 클램프.
            val bucketIdx = rs.getInt("bucket_idx").coerceIn(0, BUCKET_COUNT - 1)
            val cnt = rs.getInt("cnt")
            counts[bucketIdx] += cnt
            total += cnt
        }, *bucketArgs)

        val buckets = counts.mapIndexed { i, c ->
            ScoreDistributionBucket(range = formatBucketRange(i), count = c)
        }

        // 평균 — AVG 는 H2 에서 DOUBLE, Postgres 에서 FLOAT 이므로 Number 로 받아 toFloat 변환.
        val meanSql = buildMeanQuery(categoryId)
        val meanArgs: Array<Any> =
            if (categoryId != null) arrayOf(since, categoryId) else arrayOf(since)
        val mean = jdbc.queryForObject(meanSql, Number::class.java, *meanArgs)?.toFloat() ?: 0.0f

        val median = computeMedian(total, counts)

        return ScoreDistribution(
            buckets = buckets,
            totalCount = total,
            medianScore = median,
            meanScore = mean,
        )
    }

    /**
     * 카테고리 ID 목록 하나로 event_type 분포를 IN 쿼리 하나에 집계한다.
     *
     * - 빈 문자열과 NULL 은 모두 "NULL" 버킷으로 합친다 (`COALESCE(NULLIF(...),'NULL')`).
     * - 리뷰 항목(clipping_review_items) 과 join 된 요약만 카운트 — 전체 요약 아님.
     *
     * @return categoryId → (event_type label → count) 중첩 Map
     */
    private fun fetchEventTypeDistribution(
        categoryIds: List<String>
    ): Map<String, Map<String, Int>> {
        if (categoryIds.isEmpty()) return emptyMap()

        // IN (?,?,...) 플레이스홀더를 카테고리 수만큼 생성
        val placeholders = categoryIds.joinToString(",") { "?" }
        val sql = """
            SELECT
              ri.category_id AS category_id,
              COALESCE(NULLIF(bs.event_type, ''), 'NULL') AS event_type_label,
              COUNT(*) AS cnt
            FROM clipping_review_items ri
            JOIN batch_summaries bs ON bs.id = ri.summary_id
            WHERE ri.category_id IN ($placeholders)
            GROUP BY ri.category_id, COALESCE(NULLIF(bs.event_type, ''), 'NULL')
        """.trimIndent()

        val result = mutableMapOf<String, MutableMap<String, Int>>()
        jdbc.query(sql, { rs ->
            val categoryId = rs.getString("category_id")
            val label = rs.getString("event_type_label")
            val count = rs.getInt("cnt")
            result.getOrPut(categoryId) { mutableMapOf() }[label] = count
        }, *categoryIds.toTypedArray())

        return result
    }

    /**
     * 점수 분포 집계용 버킷 질의 SQL 을 생성한다.
     *
     * `categoryId` 가 있으면 WHERE 절에 해당 컬럼 비교를 추가하고, 없으면 created_at 조건만 남긴다.
     * `FLOOR(importance_score * 10)` 는 H2 (MODE=PostgreSQL) / PostgreSQL 모두에서 동작한다.
     */
    private fun buildBucketQuery(categoryId: String?): String = """
        SELECT FLOOR(importance_score * 10) AS bucket_idx, COUNT(*) AS cnt
        FROM batch_summaries
        WHERE created_at >= ?
          ${if (categoryId != null) "AND category_id = ?" else ""}
        GROUP BY FLOOR(importance_score * 10)
        ORDER BY bucket_idx
    """.trimIndent()

    /**
     * 평균 점수 질의 SQL 을 생성한다. 집계 대상이 비어있어도 COALESCE 로 0 을 반환한다.
     */
    private fun buildMeanQuery(categoryId: String?): String = """
        SELECT COALESCE(AVG(importance_score), 0) AS avg_score
        FROM batch_summaries
        WHERE created_at >= ?
          ${if (categoryId != null) "AND category_id = ?" else ""}
    """.trimIndent()

    /**
     * 버킷 라벨 생성 — `"0.0-0.1"`, `"0.9-1.0"` 등 표시용. Locale.US 로 고정해 `,` 소수점 방지.
     */
    private fun formatBucketRange(index: Int): String {
        val lo = index / 10.0
        val hi = (index + 1) / 10.0
        return "%.1f-%.1f".format(Locale.US, lo, hi)
    }

    /**
     * 버킷 누적 합산 기반 중앙값 근사.
     *
     * 전체 건수 0 이면 `0f` 반환. 그 외에는 정렬된 관찰값의 lower-median 위치
     * (`(total - 1) / 2`, 0 기반) 가 포함된 버킷의 중심점 `(idx + 0.5) / 10` 을 반환한다.
     *
     * 짝수 개 관찰에서 통계적 중앙값은 두 중간값의 평균이지만, 히스토그램 버킷 단위로 근사할 때는
     * 하위 중간값의 버킷 중심점을 취하는 것이 표준 관례다 (`R`, `numpy` 모두 `lower` 옵션 제공).
     * UI 게이지 표시 용도에는 정확 중앙값보다 계산 비용이 훨씬 저렴하다.
     */
    private fun computeMedian(total: Int, counts: IntArray): Float {
        if (total == 0) return 0.0f
        // lower-median 위치. 짝수 개 관찰에서 두 중간 중 앞쪽을 선택.
        val midPos = (total - 1) / 2
        var cumulative = 0
        for ((i, c) in counts.withIndex()) {
            cumulative += c
            if (cumulative >= midPos + 1) {
                return (i + 0.5f) / BUCKET_COUNT.toFloat()
            }
        }
        // 이론적으로 도달 불가 — 안전 반환값.
        return 1.0f
    }

    companion object {
        private const val SEVEN_DAYS_SECONDS: Long = 7L * 24L * 60L * 60L
        private const val ONE_DAY_SECONDS: Long = 24L * 60L * 60L
        private const val BUCKET_COUNT: Int = 10
    }
}
