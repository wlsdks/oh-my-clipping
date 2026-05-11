package com.clipping.mcpserver.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

private val log = KotlinLogging.logger {}

/**
 * `expected_review_at` 컬럼(V120 추가) 의 초기 backfill 을 수행한다.
 *
 * 배경:
 *   - V120 migration 은 vendor-neutral 을 위해 schema 만 추가하고 backfill 은 JDBC 로 넘긴다.
 *   - H2 와 PostgreSQL 의 날짜 산술 문법 차이(`DATEADD` vs `INTERVAL '180 days'`) 를 피하기 위해
 *     Java `Duration.ofDays(180)` 으로 계산 후 prepared statement 로 전달한다.
 *
 * 실행 시점:
 *   - `ApplicationReadyEvent` 에서 한 번만 실행. 멱등한 UPDATE 이므로 재시작 시 no-op.
 *   - 실패해도 서버 시작은 성공시킨다 (경고 로그만 남긴다 — 데이터 무결성에 치명적이지 않음).
 */
@Component
class ExpectedReviewAtBackfillRunner @Autowired constructor(
    private val jdbc: JdbcTemplate
) {
    companion object {
        /** 저작권 재검토 주기(일) — [AdminSourceService.COMPLIANCE_REVIEW_PERIOD_DAYS] 와 일치 */
        const val REVIEW_PERIOD_DAYS = 180L
    }

    @EventListener(ApplicationReadyEvent::class)
    fun backfillOnStartup() {
        // expected_review_at 이 NULL 이지만 terms_reviewed_at 이 존재하는 레코드만 업데이트한다.
        // 계산식: expected = terms_reviewed_at + 180일
        // vendor-neutral SQL: prepared statement 로 Java 계산값을 전달할 수 없으므로
        // 한 번의 UPDATE 로는 불가 → 대상 행을 조회 후 개별 UPDATE 한다.
        val targets = jdbc.queryForList(
            """
            SELECT id, terms_reviewed_at
            FROM rss_sources
            WHERE terms_reviewed_at IS NOT NULL
              AND expected_review_at IS NULL
            """.trimIndent()
        )
        if (targets.isEmpty()) {
            log.info { "expected_review_at backfill: no rows need backfill" }
            return
        }
        val periodSeconds = REVIEW_PERIOD_DAYS * 86_400
        var updated = 0
        for (row in targets) {
            val id = row["id"] as? String ?: continue
            val termsReviewedAt = when (val raw = row["terms_reviewed_at"]) {
                is java.sql.Timestamp -> raw.toInstant()
                else -> null
            } ?: continue
            val expected = java.sql.Timestamp.from(termsReviewedAt.plusSeconds(periodSeconds))
            // 개별 UPDATE — 대상이 수십/수백 건이므로 N+1 이 되어도 단일 트랜잭션에서 문제 없다.
            val affected = jdbc.update(
                "UPDATE rss_sources SET expected_review_at = ? WHERE id = ? AND expected_review_at IS NULL",
                expected, id
            )
            updated += affected
        }
        log.info { "expected_review_at backfill: $updated rows updated (${targets.size} candidates)" }
    }
}
