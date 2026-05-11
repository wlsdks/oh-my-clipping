package com.clipping.mcpserver.store

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import java.sql.Timestamp
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * 사용자 구독 요청 통계 스냅샷의 실제 SQL 집계를 검증한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class JpaUserClippingRequestStoreStatsSnapshotTest {

    @Autowired lateinit var store: UserClippingRequestStore

    @Autowired lateinit var jdbc: JdbcTemplate

    private val now: Instant = Instant.parse("2026-05-01T00:00:00Z")

    @BeforeEach
    fun cleanup() {
        // getStatsSnapshot 은 전체 테이블 집계 API 라 user/topic scope 가 불가능하다.
        // 따라서 (1) cleanup 으로 isolation 을 확보하고 (2) baseline snapshot 으로 delta-based
        // 비교를 한다 — LESSONS L-006 (공유 H2 잔존 row 보호) 양쪽 모두 만족.
        jdbc.update("DELETE FROM clipping_user_requests")
        jdbc.update("DELETE FROM admin_users WHERE id LIKE 'stats-user-%'")
        jdbc.update(
            """
                INSERT INTO admin_users
                    (id, username, password_hash, role, approval_status, created_at, updated_at)
                VALUES (?, ?, ?, 'USER', 'APPROVED', ?, ?)
            """.trimIndent(),
            "stats-user-1",
            "stats-user-1@example.com",
            "hash",
            Timestamp.from(now),
            Timestamp.from(now)
        )
    }

    @Test
    fun `통계 스냅샷은 전체 row 로드 없이 DB 집계 결과를 반환한다`() {
        val cutoff = now.minus(7, ChronoUnit.DAYS)
        val before = store.getStatsSnapshot(cutoff)

        insertRequest("stats-pending-1", "AI", "PENDING")
        insertRequest("stats-approved-1", "AI", "APPROVED", reviewedAt = now.minus(1, ChronoUnit.DAYS))
        insertRequest("stats-approved-2", "  AI  ", "APPROVED", reviewedAt = now.minus(2, ChronoUnit.DAYS))
        insertRequest(
            "stats-rejected-1",
            "보안",
            "REJECTED",
            reviewNote = "중복",
            reviewedAt = now.minus(3, ChronoUnit.DAYS)
        )
        insertRequest(
            "stats-rejected-2",
            "핀테크",
            "REJECTED",
            reviewNote = "중복",
            reviewedAt = now.minus(10, ChronoUnit.DAYS)
        )

        val after = store.getStatsSnapshot(cutoff)

        (after.pendingCount - before.pendingCount) shouldBe 1
        (after.approvedCount - before.approvedCount) shouldBe 2
        (after.rejectedCount - before.rejectedCount) shouldBe 2
        (after.totalCount - before.totalCount) shouldBe 5
        // 우리 데이터(2건의 APPROVED: createdAt now-4d, reviewedAt now-1d/now-2d → 72h+48h)만
        // 평균에 기여 → 60.0h. (cleanup 으로 다른 row 없음.)
        after.avgApprovalHours shouldBe 60.0
        // AI 토픽이 3건 (이번 PR delta) — 이전 데이터는 cleanup 으로 0건.
        after.topTopics.first() shouldBe UserClippingRequestCountRow("AI", 3)
        // "중복" 사유 2건 (이번 PR delta).
        after.rejectionReasons.first { it.name == "중복" }.count shouldBe 2
        // weekly: APPROVED 2건 + cutoff 7일 안의 REJECTED 1건 = 3건 신규.
        (after.weeklyProcessedCount - before.weeklyProcessedCount) shouldBe 3
    }

    @Test
    fun `반려 사유가 공백이면 사유 랭킹에서 제외한다`() {
        val cutoff = now.minus(7, ChronoUnit.DAYS)
        val before = store.getStatsSnapshot(cutoff)

        insertRequest(
            "stats-rejected-blank",
            "보안",
            "REJECTED",
            reviewNote = "   ",
            reviewedAt = now.minus(1, ChronoUnit.DAYS)
        )

        val after = store.getStatsSnapshot(cutoff)

        // 공백 사유는 ranking 에 추가되면 안 된다 → delta 0.
        (after.rejectionReasons.size - before.rejectionReasons.size) shouldBe 0
    }

    @Test
    fun `요청명이 공백이면 토픽 랭킹에서 제외한다`() {
        val cutoff = now.minus(7, ChronoUnit.DAYS)
        val before = store.getStatsSnapshot(cutoff)

        insertRequest("stats-topic-blank", "   ", "PENDING")
        insertRequest("stats-topic-valid", "AI", "PENDING")

        val after = store.getStatsSnapshot(cutoff)

        // 공백 토픽은 누락, "AI" 만 카운트 +1 — scoped delta check.
        val aiBefore = before.topTopics.firstOrNull { it.name == "AI" }?.count ?: 0
        val aiAfter = after.topTopics.first { it.name == "AI" }.count
        (aiAfter - aiBefore) shouldBe 1
        // 공백 토픽은 추가되지 않음 — 다른 새 라벨이 등장하지 않아야 한다.
        val newLabels = after.topTopics.map { it.name }.toSet() -
            before.topTopics.map { it.name }.toSet()
        newLabels shouldBe setOf("AI")
    }

    @Test
    fun `주간 처리 수는 cutoff 와 같은 reviewed_at 을 제외하고 이후만 집계한다`() {
        val cutoff = now.minus(7, ChronoUnit.DAYS)
        val before = store.getStatsSnapshot(cutoff)

        insertRequest("stats-approved-at-cutoff", "AI", "APPROVED", reviewedAt = cutoff)
        insertRequest("stats-rejected-after-cutoff", "AI", "REJECTED", reviewedAt = cutoff.plusSeconds(1))

        val after = store.getStatsSnapshot(cutoff)

        // cutoff 와 같은 시각은 제외 → +1 만 증가해야 한다.
        (after.weeklyProcessedCount - before.weeklyProcessedCount) shouldBe 1
    }

    @Test
    fun `승인 리드타임 평균은 reviewed_at이 created_at보다 빠른 깨진 row를 제외한다`() {
        // cleanup 으로 다른 APPROVED row 없음 → avg 는 우리가 insert 한 valid row 만 반영.
        val createdAt = now.minus(4, ChronoUnit.DAYS)
        insertRequest("stats-approved-valid", "AI", "APPROVED", reviewedAt = createdAt.plus(2, ChronoUnit.HOURS), createdAt = createdAt)
        insertRequest("stats-approved-corrupt", "AI", "APPROVED", reviewedAt = createdAt.minus(1, ChronoUnit.HOURS), createdAt = createdAt)

        val snapshot = store.getStatsSnapshot(now.minus(7, ChronoUnit.DAYS))

        // 깨진 row 제외 → valid row 만 → 2.0h.
        snapshot.avgApprovalHours shouldBe 2.0
    }

    private fun insertRequest(
        id: String,
        requestName: String,
        status: String,
        reviewNote: String? = null,
        reviewedAt: Instant? = null,
        createdAt: Instant = now.minus(4, ChronoUnit.DAYS),
    ) {
        jdbc.update(
            """
                INSERT INTO clipping_user_requests
                    (
                        id, requester_user_id, request_name, source_name, source_url, slack_channel_id,
                        persona_name, persona_prompt, status, review_note, reviewed_at, created_at, updated_at
                    )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            id,
            "stats-user-1",
            requestName,
            "테스트 소스",
            "https://example.com/$id",
            "C123",
            "테스트 페르소나",
            "요약",
            status,
            reviewNote,
            reviewedAt?.let(Timestamp::from),
            Timestamp.from(createdAt),
            Timestamp.from(now)
        )
    }
}
