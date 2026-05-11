package com.ohmyclipping.service.query

import com.ohmyclipping.service.dto.admin.ReviewPolicyStatus
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * ReviewPolicyQueryHelper integration test.
 *
 * - 공유 H2 스키마를 사용하므로 카테고리별 고유 prefix 로 범위 한정 + BeforeEach 로 정리한다
 *   (AGENTS.md 5.1 delta/scope 규칙).
 * - categoryId 를 필터로 씌워 타 테스트 잔존 row 오염을 차단한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ReviewPolicyQueryHelperTest {

    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var helper: ReviewPolicyQueryHelper

    // VARCHAR(36) 한도 내에서 고유 prefix (13자 이내).
    private val fixturePrefix = "rpq${UUID.randomUUID().toString().take(8)}-"
    private val now: Instant = Instant.now()

    @BeforeEach
    fun clean() {
        jdbc.update("DELETE FROM clipping_review_items WHERE category_id LIKE ?", "$fixturePrefix%")
        jdbc.update("DELETE FROM batch_summaries WHERE id LIKE ?", "$fixturePrefix%")
        jdbc.update("DELETE FROM rss_items WHERE id LIKE ?", "$fixturePrefix%")
        jdbc.update("DELETE FROM clipping_category_rules WHERE category_id LIKE ?", "$fixturePrefix%")
        jdbc.update("DELETE FROM batch_categories WHERE id LIKE ?", "$fixturePrefix%")
    }

    private fun seedCategory(suffix: String, name: String, isActive: Boolean = true) {
        jdbc.update(
            """INSERT INTO batch_categories (id, name, is_active, created_at, updated_at, system_updated_at)
               VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)""",
            "$fixturePrefix$suffix", name, isActive
        )
    }

    private fun seedRule(categorySuffix: String, autoApprove: Double?, reviewThreshold: Double = 0.35) {
        // include_threshold 는 review_threshold 보다 크거나 같아야 한다는 CHECK 제약이 있으므로
        // 기본값 0.55 를 유지하되 review_threshold 만 파라미터화 한다.
        jdbc.update(
            """INSERT INTO clipping_category_rules
                (category_id, include_keywords, exclude_keywords, risk_tags,
                 include_threshold, review_threshold, uncertain_to_review, auto_exclude_enabled,
                 auto_approve_threshold, revision, updated_at, system_updated_at)
               VALUES (?, '[]', '[]', '[]', 0.55, ?, TRUE, TRUE, ?, 1,
                 CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)""",
            "$fixturePrefix$categorySuffix", reviewThreshold, autoApprove
        )
    }

    private fun seedSummary(
        suffix: String,
        categorySuffix: String,
        importance: Float,
        eventType: String?,
        createdAt: Instant = now
    ) {
        val itemId = "$fixturePrefix$suffix-item"
        val categoryId = "$fixturePrefix$categorySuffix"
        // rss_items.category_id NOT NULL (V3), rss_source_id nullable since V12.
        jdbc.update(
            """INSERT INTO rss_items (id, rss_source_id, title, link, published_at,
                is_processed, category_id, created_at)
               VALUES (?, NULL, 'x', ?, ?, TRUE, ?, ?)""",
            itemId,
            "https://x/$itemId",
            Timestamp.from(createdAt),
            categoryId,
            Timestamp.from(createdAt)
        )
        jdbc.update(
            """INSERT INTO batch_summaries (id, category_id, rss_item_id,
                original_title, translated_title, summary, source_link,
                created_at, is_sent_to_slack, importance_score, event_type)
               VALUES (?, ?, ?, 'orig', 'kr', 'sum', 'https://x/sum', ?, FALSE, ?, ?)""",
            "$fixturePrefix$suffix", categoryId, itemId,
            Timestamp.from(createdAt), importance, eventType
        )
    }

    private fun seedReviewItem(
        summarySuffix: String,
        categorySuffix: String,
        status: String,
        reviewedBy: String? = null,
        reviewedAt: Instant? = null
    ) {
        jdbc.update(
            """INSERT INTO clipping_review_items (summary_id, category_id, status,
                reviewed_by, reviewed_at, created_at, updated_at)
               VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)""",
            "$fixturePrefix$summarySuffix",
            "$fixturePrefix$categorySuffix",
            status,
            reviewedBy,
            reviewedAt?.let { Timestamp.from(it) }
        )
    }

    private fun resultFor(categorySuffix: String): ReviewPolicyStatus? =
        helper.getPolicyStatus().firstOrNull { it.categoryId == "$fixturePrefix$categorySuffix" }

    /** 테스트 가독성용: 반드시 존재해야 하는 경우 바로 !! 로 꺼내는 헬퍼. */
    private fun requireResult(categorySuffix: String): ReviewPolicyStatus =
        resultFor(categorySuffix) ?: error("Expected result for $categorySuffix")

    @Nested
    inner class `카테고리별 대기 REVIEW 와 7일 처리 집계` {

        @Test
        fun `pending REVIEW 3건과 INCLUDE 2건을 올바르게 집계한다`() {
            seedCategory("c1", "카테고리 1")
            seedRule("c1", autoApprove = 0.5)

            // 5 summaries: 3 REVIEW (pending), 2 INCLUDE (processed in last 7 days)
            seedSummary("s1", "c1", 0.8f, "release")
            seedSummary("s2", "c1", 0.7f, "release")
            seedSummary("s3", "c1", 0.6f, "acquisition")
            seedSummary("s4", "c1", 0.9f, "acquisition")
            seedSummary("s5", "c1", 0.4f, null)

            seedReviewItem("s1", "c1", "REVIEW", reviewedBy = null, reviewedAt = null)
            seedReviewItem("s2", "c1", "REVIEW", reviewedBy = null, reviewedAt = null)
            seedReviewItem("s3", "c1", "REVIEW", reviewedBy = null, reviewedAt = null)
            // 최근 7일 내 처리
            seedReviewItem("s4", "c1", "INCLUDE", reviewedBy = "policy-auto", reviewedAt = now.minusSeconds(3600))
            seedReviewItem("s5", "c1", "INCLUDE", reviewedBy = "admin-user", reviewedAt = now.minusSeconds(7200))

            val row = requireResult("c1")
            row.categoryName shouldBe "카테고리 1"
            row.pendingReviewCount shouldBe 3
            row.last7DaysProcessed shouldBe 2
            row.last7DaysAutoApproved shouldBe 1
            row.last7DaysManuallyReviewed shouldBe 1
        }

        @Test
        fun `7일 밖 처리 항목은 last7Days 합계에서 제외된다`() {
            seedCategory("c2", "Old Processing")
            seedRule("c2", autoApprove = null)

            seedSummary("s1", "c2", 0.5f, null)
            seedSummary("s2", "c2", 0.5f, null)

            // 8일 전에 처리된 항목 — 통계에서 제외되어야 한다.
            val eightDaysAgo = now.minusSeconds(8L * 86400L)
            seedReviewItem("s1", "c2", "INCLUDE", reviewedBy = "admin-user", reviewedAt = eightDaysAgo)
            // 범위 내 1건만 카운트
            seedReviewItem("s2", "c2", "EXCLUDE", reviewedBy = "admin-user", reviewedAt = now.minusSeconds(3600))

            val row = requireResult("c2")
            row.last7DaysProcessed shouldBe 1
            row.last7DaysManuallyReviewed shouldBe 1
            row.last7DaysAutoApproved shouldBe 0
        }

        @Test
        fun `마지막 reviewed_at 은 카테고리 내 최댓값을 반환한다`() {
            seedCategory("clast", "Last Reviewed")
            seedRule("clast", autoApprove = 0.5)

            seedSummary("s1", "clast", 0.5f, null)
            seedSummary("s2", "clast", 0.5f, null)
            val earlier = now.minusSeconds(7200)
            val later = now.minusSeconds(3600)
            seedReviewItem("s1", "clast", "INCLUDE", reviewedBy = "admin-user", reviewedAt = earlier)
            seedReviewItem("s2", "clast", "EXCLUDE", reviewedBy = "admin-user", reviewedAt = later)

            val row = requireResult("clast")
            val lastReviewedAt = row.lastReviewedAt
            lastReviewedAt.shouldNotBeNull()
            // epochSecond 단위까지만 비교 — DB 저장 과정에서 마이크로초 손실 가능
            lastReviewedAt.epochSecond shouldBe later.epochSecond
        }
    }

    @Nested
    inner class `threshold NULL 처리` {

        @Test
        fun `rule 이 등록되지 않은 카테고리는 autoApproveThreshold 와 reviewThreshold 가 null`() {
            seedCategory("cNoRule", "No Rule")
            // rule 을 의도적으로 생성하지 않음
            seedSummary("s1", "cNoRule", 0.5f, null)
            seedReviewItem("s1", "cNoRule", "REVIEW", reviewedBy = null, reviewedAt = null)

            val row = requireResult("cNoRule")
            row.autoApproveThreshold.shouldBeNull()
            row.reviewThreshold.shouldBeNull()
            row.pendingReviewCount shouldBe 1
        }

        @Test
        fun `autoApproveThreshold 가 NULL 인 rule 은 null 로 반환`() {
            seedCategory("cAutoNull", "Auto Null")
            seedRule("cAutoNull", autoApprove = null, reviewThreshold = 0.4)
            seedSummary("s1", "cAutoNull", 0.5f, null)
            seedReviewItem("s1", "cAutoNull", "REVIEW", reviewedBy = null, reviewedAt = null)

            val row = requireResult("cAutoNull")
            row.autoApproveThreshold.shouldBeNull()
            row.reviewThreshold shouldBe 0.4
        }
    }

    @Nested
    inner class `event_type 분포 집계` {

        @Test
        fun `event_type 별 건수를 Map 으로 반환한다`() {
            seedCategory("cEvt", "Event Dist")
            seedRule("cEvt", autoApprove = 0.5)

            seedSummary("s1", "cEvt", 0.5f, "release")
            seedSummary("s2", "cEvt", 0.5f, "release")
            seedSummary("s3", "cEvt", 0.5f, "acquisition")
            seedReviewItem("s1", "cEvt", "REVIEW", reviewedBy = null, reviewedAt = null)
            seedReviewItem("s2", "cEvt", "REVIEW", reviewedBy = null, reviewedAt = null)
            seedReviewItem("s3", "cEvt", "INCLUDE", reviewedBy = "admin-user", reviewedAt = now.minusSeconds(60))

            val row = requireResult("cEvt")
            row.eventTypeDistribution["release"] shouldBe 2
            row.eventTypeDistribution["acquisition"] shouldBe 1
        }

        @Test
        fun `NULL 과 빈 문자열 event_type 은 NULL 키로 합쳐진다`() {
            seedCategory("cNull", "Null Event")
            seedRule("cNull", autoApprove = 0.5)

            seedSummary("s1", "cNull", 0.5f, null)
            seedSummary("s2", "cNull", 0.5f, "")
            seedSummary("s3", "cNull", 0.5f, "release")
            seedReviewItem("s1", "cNull", "REVIEW", reviewedBy = null, reviewedAt = null)
            seedReviewItem("s2", "cNull", "REVIEW", reviewedBy = null, reviewedAt = null)
            seedReviewItem("s3", "cNull", "REVIEW", reviewedBy = null, reviewedAt = null)

            val row = requireResult("cNull")
            row.eventTypeDistribution["NULL"] shouldBe 2
            row.eventTypeDistribution["release"] shouldBe 1
        }
    }

    @Nested
    inner class `avgScore 집계` {

        @Test
        fun `리뷰 항목에 연결된 요약 importance_score 의 평균을 반환한다`() {
            seedCategory("cAvg", "Avg Score")
            seedRule("cAvg", autoApprove = 0.5)

            // avg = (0.4 + 0.6 + 0.8) / 3 = 0.6
            seedSummary("s1", "cAvg", 0.4f, null)
            seedSummary("s2", "cAvg", 0.6f, null)
            seedSummary("s3", "cAvg", 0.8f, null)
            seedReviewItem("s1", "cAvg", "REVIEW", reviewedBy = null, reviewedAt = null)
            seedReviewItem("s2", "cAvg", "INCLUDE", reviewedBy = "admin-user", reviewedAt = now.minusSeconds(60))
            seedReviewItem("s3", "cAvg", "EXCLUDE", reviewedBy = "admin-user", reviewedAt = now.minusSeconds(60))

            val row = requireResult("cAvg")
            // float 비교 — Double 변환 후 epsilon 0.01 으로 근사 비교
            row.avgScore.toDouble() shouldBe 0.6.plusOrMinus(0.01)
        }

        @Test
        fun `리뷰 항목이 없으면 avgScore 는 0`() {
            seedCategory("cEmpty", "Empty")
            seedRule("cEmpty", autoApprove = 0.5)

            val row = requireResult("cEmpty")
            row.avgScore shouldBe 0.0f
            row.pendingReviewCount shouldBe 0
            row.last7DaysProcessed shouldBe 0
            row.eventTypeDistribution shouldBe emptyMap<String, Int>()
        }
    }

    @Nested
    inner class `비활성 카테고리 제외` {

        @Test
        fun `is_active FALSE 카테고리는 결과에서 제외된다`() {
            seedCategory("cInactive", "Inactive", isActive = false)
            seedRule("cInactive", autoApprove = 0.5)
            seedSummary("s1", "cInactive", 0.5f, null)
            seedReviewItem("s1", "cInactive", "REVIEW", reviewedBy = null, reviewedAt = null)

            resultFor("cInactive").shouldBeNull()
        }
    }

    @Nested
    inner class `여러 카테고리 동시 집계` {

        @Test
        fun `카테고리 2개가 모두 반환되며 서로 데이터를 오염시키지 않는다`() {
            seedCategory("cA", "A")
            seedCategory("cB", "B")
            seedRule("cA", autoApprove = 0.5)
            seedRule("cB", autoApprove = null)

            seedSummary("sA1", "cA", 0.9f, "release")
            seedSummary("sB1", "cB", 0.1f, "funding")
            seedReviewItem("sA1", "cA", "REVIEW", reviewedBy = null, reviewedAt = null)
            seedReviewItem("sB1", "cB", "REVIEW", reviewedBy = null, reviewedAt = null)

            val all = helper.getPolicyStatus()
                .filter { it.categoryId.startsWith(fixturePrefix) }
                .map { it.categoryId }

            all shouldContainAll listOf("${fixturePrefix}cA", "${fixturePrefix}cB")

            requireResult("cA").eventTypeDistribution["release"] shouldBe 1
            requireResult("cB").eventTypeDistribution["funding"] shouldBe 1
            // cA 의 event_type 에 funding 이 섞여 들어가면 버그
            requireResult("cA").eventTypeDistribution.containsKey("funding") shouldBe false
        }
    }

    @Nested
    inner class `getScoreDistribution` {

        @Test
        fun `10 개 버킷으로 점수를 나눈다`() {
            seedCategory("cDist1", "Dist 1")
            // 각 버킷에 한 건씩 — 0.05 / 0.15 / 0.55 / 0.75 / 0.95
            seedSummary("sd1", "cDist1", 0.05f, null)
            seedSummary("sd2", "cDist1", 0.15f, null)
            seedSummary("sd3", "cDist1", 0.55f, null)
            seedSummary("sd4", "cDist1", 0.75f, null)
            seedSummary("sd5", "cDist1", 0.95f, null)

            val dist = helper.getScoreDistribution(
                categoryId = "${fixturePrefix}cDist1",
                days = 7,
            )

            dist.buckets.size shouldBe 10
            dist.buckets[0].count shouldBe 1  // 0.0-0.1
            dist.buckets[1].count shouldBe 1  // 0.1-0.2
            dist.buckets[5].count shouldBe 1  // 0.5-0.6
            dist.buckets[7].count shouldBe 1  // 0.7-0.8
            dist.buckets[9].count shouldBe 1  // 0.9-1.0
            dist.totalCount shouldBe 5
        }

        @Test
        fun `특정 카테고리 필터가 적용된다`() {
            seedCategory("cDistA", "A")
            seedCategory("cDistB", "B")
            seedSummary("sDistA", "cDistA", 0.8f, null)
            seedSummary("sDistB", "cDistB", 0.3f, null)

            val distA = helper.getScoreDistribution(
                categoryId = "${fixturePrefix}cDistA",
                days = 7,
            )
            distA.totalCount shouldBe 1
            distA.buckets[8].count shouldBe 1  // 0.8-0.9
            // 다른 카테고리 버킷은 비어 있어야 함
            distA.buckets[3].count shouldBe 0
        }

        @Test
        fun `categoryId null 이면 전체 카테고리 집계`() {
            seedCategory("cDistAll1", "All1")
            seedCategory("cDistAll2", "All2")
            seedSummary("sAll1", "cDistAll1", 0.25f, null)
            seedSummary("sAll2", "cDistAll2", 0.25f, null)

            val dist = helper.getScoreDistribution(categoryId = null, days = 7)
            // 공유 H2 에 타 테스트 잔존 row 오염 가능 → 델타 기반 ≥ 2 검증
            dist.buckets[2].count shouldBeGreaterThanOrEqual 2
            dist.totalCount shouldBeGreaterThanOrEqual 2
        }

        @Test
        fun `대상 기간 밖 summary 는 제외`() {
            seedCategory("cDistOld", "Old")
            seedSummary(
                "sOld",
                "cDistOld",
                0.5f,
                null,
                createdAt = now.minusSeconds(30L * 86400L),
            )

            val dist = helper.getScoreDistribution(
                categoryId = "${fixturePrefix}cDistOld",
                days = 7,
            )
            dist.totalCount shouldBe 0
        }

        @Test
        fun `summary 0 건이면 buckets 는 10 개지만 count 모두 0`() {
            seedCategory("cDistEmpty", "Empty")

            val dist = helper.getScoreDistribution(
                categoryId = "${fixturePrefix}cDistEmpty",
                days = 7,
            )
            dist.buckets.size shouldBe 10
            dist.buckets.all { it.count == 0 } shouldBe true
            dist.totalCount shouldBe 0
            dist.meanScore shouldBe 0f
            dist.medianScore shouldBe 0f
        }

        @Test
        fun `mean 과 median 은 Float 로 반환`() {
            seedCategory("cDistStats", "Stats")
            // 평균 = (0.2+0.4+0.6+0.8)/4 = 0.5
            seedSummary("sStat1", "cDistStats", 0.2f, null)
            seedSummary("sStat2", "cDistStats", 0.4f, null)
            seedSummary("sStat3", "cDistStats", 0.6f, null)
            seedSummary("sStat4", "cDistStats", 0.8f, null)

            val dist = helper.getScoreDistribution(
                categoryId = "${fixturePrefix}cDistStats",
                days = 7,
            )
            dist.meanScore shouldBe 0.5f.plusOrMinus(0.01f)
            // 버킷 중심점 근사 — 0.5 근처(±0.15)
            dist.medianScore shouldBe 0.45f.plusOrMinus(0.15f)
        }

        @Test
        fun `버킷 range 라벨 포맷은 소수점 1 자리 두 개를 하이픈으로 연결`() {
            seedCategory("cDistLabel", "Label")

            val dist = helper.getScoreDistribution(
                categoryId = "${fixturePrefix}cDistLabel",
                days = 7,
            )
            dist.buckets[0].range shouldBe "0.0-0.1"
            dist.buckets[5].range shouldBe "0.5-0.6"
            dist.buckets[9].range shouldBe "0.9-1.0"
        }
    }
}

