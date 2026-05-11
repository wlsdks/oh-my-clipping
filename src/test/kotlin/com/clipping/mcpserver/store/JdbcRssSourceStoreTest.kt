package com.clipping.mcpserver.store

import com.clipping.mcpserver.model.Category
import com.clipping.mcpserver.model.RssSource
import com.clipping.mcpserver.model.SourceComplianceStatus
import com.clipping.mcpserver.model.SourceLegalBasis
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class JdbcRssSourceStoreTest {

    @Autowired lateinit var sourceStore: RssSourceStore
    @Autowired lateinit var categoryStore: CategoryStore
    @Autowired lateinit var jdbc: JdbcTemplate

    private lateinit var category: Category

    @BeforeEach
    fun setup() {
        category = categoryStore.save(Category(id = "", name = "TestCat"))
    }

    @Test
    fun `should save source with approval fields`() {
        val source = sourceStore.save(
            RssSource(id = "", name = "Test", url = "https://test.com/rss", categoryId = category.id)
        )
        source.crawlApproved shouldBe false
        source.verificationStatus shouldBe "PENDING"
        source.reliabilityScore shouldBe 50
        source.crawlFailCount shouldBe 0
        source.legalBasis shouldBe SourceLegalBasis.QUOTATION_ONLY
        source.summaryAllowed shouldBe true
        source.fulltextAllowed shouldBe false
    }

    @Test
    fun `should update approval`() {
        val source = sourceStore.save(
            RssSource(id = "", name = "Test", url = "https://test.com/rss", categoryId = category.id)
        )
        sourceStore.updateApproval(source.id, true, "admin")
        val updated = sourceStore.findById(source.id)!!
        updated.crawlApproved shouldBe true
        updated.approvedBy shouldBe "admin"
        updated.approvedAt shouldNotBe null
    }

    @Test
    fun `should update verification status`() {
        val source = sourceStore.save(
            RssSource(id = "", name = "Test", url = "https://test.com/rss", categoryId = category.id)
        )
        sourceStore.updateVerificationStatus(source.id, "VERIFIED")
        val updated = sourceStore.findById(source.id)!!
        updated.verificationStatus shouldBe "VERIFIED"
    }

    @Test
    fun `should increment fail count`() {
        val source = sourceStore.save(
            RssSource(id = "", name = "Test", url = "https://test.com/rss", categoryId = category.id)
        )
        sourceStore.incrementFailCount(source.id, "Connection timeout")
        val updated = sourceStore.findById(source.id)!!
        updated.crawlFailCount shouldBe 1
        updated.lastCrawlError shouldBe "Connection timeout"
    }

    @Test
    fun `should reset fail count`() {
        val source = sourceStore.save(
            RssSource(id = "", name = "Test", url = "https://test.com/rss", categoryId = category.id)
        )
        sourceStore.incrementFailCount(source.id, "Error")
        sourceStore.resetFailCount(source.id)
        val updated = sourceStore.findById(source.id)!!
        updated.crawlFailCount shouldBe 0
        updated.lastCrawlError shouldBe null
    }

    @Test
    fun `listApproved should return only approved and active sources`() {
        val initialApproved = sourceStore.listApproved().size
        sourceStore.save(
            RssSource(id = "", name = "Unapproved", url = "https://a.com/rss", categoryId = category.id)
        )
        val approved = sourceStore.save(
            RssSource(id = "", name = "Approved", url = "https://b.com/rss", categoryId = category.id)
        )
        sourceStore.updateApproval(approved.id, true, "admin")
        sourceStore.updateVerificationStatus(approved.id, "VERIFIED")

        val result = sourceStore.listApproved()
        result.size shouldBe initialApproved + 1
        result.any { it.name == "Approved" } shouldBe true
    }

    @Test
    fun `listApproved should exclude prohibited or summary denied sources`() {
        val prohibited = sourceStore.save(
            RssSource(
                id = "",
                name = "Prohibited",
                url = "https://p.com/rss",
                categoryId = category.id,
                legalBasis = SourceLegalBasis.PROHIBITED
            )
        )
        sourceStore.updateApproval(prohibited.id, true, "admin")
        sourceStore.updateVerificationStatus(prohibited.id, "VERIFIED")

        val noSummary = sourceStore.save(
            RssSource(
                id = "",
                name = "NoSummary",
                url = "https://ns.com/rss",
                categoryId = category.id,
                summaryAllowed = false
            )
        )
        sourceStore.updateApproval(noSummary.id, true, "admin")
        sourceStore.updateVerificationStatus(noSummary.id, "VERIFIED")

        val approved = sourceStore.save(
            RssSource(id = "", name = "Allowed", url = "https://ok.com/rss", categoryId = category.id)
        )
        sourceStore.updateApproval(approved.id, true, "admin")
        sourceStore.updateVerificationStatus(approved.id, "VERIFIED")

        val result = sourceStore.listApproved(category.id)
        result.any { it.id == approved.id } shouldBe true
        result.any { it.id == prohibited.id } shouldBe false
        result.any { it.id == noSummary.id } shouldBe false
    }

    @Test
    fun `update should change basic fields`() {
        val source = sourceStore.save(
            RssSource(id = "", name = "Original", url = "https://test.com/rss", categoryId = category.id)
        )
        sourceStore.update(source.copy(name = "Updated", url = "https://new.com/rss"))
        val updated = sourceStore.findById(source.id)!!
        updated.name shouldBe "Updated"
        updated.url shouldBe "https://new.com/rss"
    }

    @Test
    fun `findByUrlAndCategoryId returns source when both match`() {
        val saved = sourceStore.save(
            RssSource(id = "", name = "Reuse", url = "https://reuse.example/rss", categoryId = category.id)
        )

        val found = sourceStore.findByUrlAndCategoryId("https://reuse.example/rss", category.id)

        found?.id shouldBe saved.id
    }

    @Test
    fun `findByUrlAndCategoryId returns null when url not found`() {
        val found = sourceStore.findByUrlAndCategoryId("https://nope.example/rss", category.id)
        found shouldBe null
    }

    @Test
    fun `findByUrlAndCategoryId returns null when category differs`() {
        val otherCategory = categoryStore.save(Category(id = "", name = "OtherCat"))
        sourceStore.save(
            RssSource(id = "", name = "X", url = "https://shared.example/rss", categoryId = category.id)
        )

        val found = sourceStore.findByUrlAndCategoryId("https://shared.example/rss", otherCategory.id)

        found shouldBe null
    }

    // --- Scheduler-safe 경로 회귀 테스트: updated_at 보존, system_updated_at만 갱신 ---

    @Test
    fun `updateVerificationStatus preserves updated_at`() {
        val source = sourceStore.save(
            RssSource(id = "", name = "VerifyTest", url = "https://vv.com/rss", categoryId = category.id)
        )
        val originalUpdatedAt = source.updatedAt

        sourceStore.updateVerificationStatus(source.id, "VERIFIED")

        val after = sourceStore.findById(source.id)!!
        after.verificationStatus shouldBe "VERIFIED"
        after.updatedAt shouldBe originalUpdatedAt
        (after.systemUpdatedAt >= source.systemUpdatedAt) shouldBe true
    }

    @Test
    fun `incrementFailCount preserves updated_at`() {
        val source = sourceStore.save(
            RssSource(id = "", name = "FailTest", url = "https://ft.com/rss", categoryId = category.id)
        )
        val originalUpdatedAt = source.updatedAt

        sourceStore.incrementFailCount(source.id, "boom")

        val after = sourceStore.findById(source.id)!!
        after.crawlFailCount shouldBe 1
        after.updatedAt shouldBe originalUpdatedAt
    }

    @Test
    fun `resetFailCount preserves updated_at`() {
        val source = sourceStore.save(
            RssSource(id = "", name = "ResetTest", url = "https://rt.com/rss", categoryId = category.id)
        )
        sourceStore.incrementFailCount(source.id, "err")
        val snapshot = sourceStore.findById(source.id)!!

        sourceStore.resetFailCount(source.id)

        val after = sourceStore.findById(source.id)!!
        after.crawlFailCount shouldBe 0
        // increment 후에도 updated_at은 보존됐어야 하므로, reset 이후에도 원본과 같다.
        after.updatedAt shouldBe snapshot.updatedAt
    }

    @Test
    fun `deactivate and reactivate preserve updated_at`() {
        val source = sourceStore.save(
            RssSource(id = "", name = "ToggleTest", url = "https://tt.com/rss", categoryId = category.id)
        )
        val originalUpdatedAt = source.updatedAt

        sourceStore.deactivate(source.id)
        val afterDeactivate = sourceStore.findById(source.id)!!
        afterDeactivate.isActive shouldBe false
        afterDeactivate.updatedAt shouldBe originalUpdatedAt

        sourceStore.reactivate(source.id)
        val afterReactivate = sourceStore.findById(source.id)!!
        afterReactivate.isActive shouldBe true
        afterReactivate.updatedAt shouldBe originalUpdatedAt
    }

    @Test
    fun `updateApproval preserves updated_at`() {
        val source = sourceStore.save(
            RssSource(id = "", name = "ApprovalTest", url = "https://at.com/rss", categoryId = category.id)
        )
        val originalUpdatedAt = source.updatedAt

        sourceStore.updateApproval(source.id, true, "admin")

        val after = sourceStore.findById(source.id)!!
        after.crawlApproved shouldBe true
        after.updatedAt shouldBe originalUpdatedAt
    }

    @Test
    fun `updateReliabilityScores preserves updated_at`() {
        val source = sourceStore.save(
            RssSource(id = "", name = "ReliabilityTest", url = "https://rs.com/rss", categoryId = category.id)
        )
        val originalUpdatedAt = source.updatedAt

        sourceStore.updateReliabilityScores(mapOf(source.id to 90))

        val after = sourceStore.findById(source.id)!!
        after.reliabilityScore shouldBe 90
        after.updatedAt shouldBe originalUpdatedAt
    }

    @Test
    fun `complianceStatus filter EXPIRED returns only expired sources`() {
        val now = Instant.now()
        // 만료된 소스 (expected_review_at 이 1일 전)
        val expired = sourceStore.save(
            RssSource(
                id = "", name = "Expired", url = "https://e.com/rss", categoryId = category.id,
                termsReviewedAt = now.minusSeconds(181L * 86_400),
                expectedReviewAt = now.minusSeconds(86_400),
                crawlApproved = true
            )
        )
        // 만료 임박 (10일 후)
        sourceStore.save(
            RssSource(
                id = "", name = "Soon", url = "https://s.com/rss", categoryId = category.id,
                termsReviewedAt = now.minusSeconds(170L * 86_400),
                expectedReviewAt = now.plusSeconds(10L * 86_400),
                crawlApproved = true
            )
        )
        // 정상 (100일 후)
        sourceStore.save(
            RssSource(
                id = "", name = "Valid", url = "https://v.com/rss", categoryId = category.id,
                termsReviewedAt = now.minusSeconds(80L * 86_400),
                expectedReviewAt = now.plusSeconds(100L * 86_400),
                crawlApproved = true
            )
        )

        val results = sourceStore.findAll(
            complianceStatus = SourceComplianceStatus.EXPIRED,
            offset = 0,
            limit = 100
        )

        results.map { it.id } shouldBe listOf(expired.id)
    }

    @Test
    fun `complianceStatus filter EXPIRING_SOON returns within 30 days`() {
        val now = Instant.now()
        // 만료 임박 (10일 후)
        val soon = sourceStore.save(
            RssSource(
                id = "", name = "Soon", url = "https://s.com/rss", categoryId = category.id,
                termsReviewedAt = now.minusSeconds(170L * 86_400),
                expectedReviewAt = now.plusSeconds(10L * 86_400),
                crawlApproved = true
            )
        )
        // 정상 (100일 후)
        sourceStore.save(
            RssSource(
                id = "", name = "Valid", url = "https://v.com/rss", categoryId = category.id,
                termsReviewedAt = now.minusSeconds(80L * 86_400),
                expectedReviewAt = now.plusSeconds(100L * 86_400),
                crawlApproved = true
            )
        )

        val results = sourceStore.findAll(
            complianceStatus = SourceComplianceStatus.EXPIRING_SOON,
            offset = 0,
            limit = 100
        )

        results.map { it.id } shouldBe listOf(soon.id)
    }

    @Test
    fun `complianceStatus filter NEVER_REVIEWED returns approved but never reviewed sources`() {
        // 미검토 (termsReviewedAt = null)
        val never = sourceStore.save(
            RssSource(
                id = "", name = "Never", url = "https://n.com/rss", categoryId = category.id,
                termsReviewedAt = null,
                expectedReviewAt = null,
                crawlApproved = true
            )
        )
        // 검토 기록 있음
        sourceStore.save(
            RssSource(
                id = "", name = "Reviewed", url = "https://r.com/rss", categoryId = category.id,
                termsReviewedAt = Instant.now(),
                expectedReviewAt = Instant.now().plusSeconds(180L * 86_400),
                crawlApproved = true
            )
        )

        // 타 테스트 클래스가 rss_sources 에 커밋한 잔존 row 와 격리하기 위해
        // 이 테스트가 만든 category 에 속한 결과만 비교한다.
        val results = sourceStore.findAll(
            complianceStatus = SourceComplianceStatus.NEVER_REVIEWED,
            offset = 0,
            limit = 100
        ).filter { it.categoryId == category.id }

        results.map { it.id } shouldBe listOf(never.id)
    }

    @Test
    fun `countComplianceAttention aggregates expired, soon, and never_reviewed`() {
        val now = Instant.now()
        // 타 테스트 클래스가 커밋한 잔존 row 의 영향을 배제하기 위해
        // 이 테스트가 추가하는 증가분(delta) 만 검증한다.
        val before = sourceStore.countComplianceAttention(now)
        sourceStore.save(
            RssSource(
                id = "", name = "Expired", url = "https://e.com/rss", categoryId = category.id,
                termsReviewedAt = now.minusSeconds(181L * 86_400),
                expectedReviewAt = now.minusSeconds(86_400),
                crawlApproved = true
            )
        )
        sourceStore.save(
            RssSource(
                id = "", name = "Soon", url = "https://s.com/rss", categoryId = category.id,
                termsReviewedAt = now.minusSeconds(170L * 86_400),
                expectedReviewAt = now.plusSeconds(10L * 86_400),
                crawlApproved = true
            )
        )
        sourceStore.save(
            RssSource(
                id = "", name = "Never", url = "https://n.com/rss", categoryId = category.id,
                termsReviewedAt = null,
                expectedReviewAt = null,
                crawlApproved = true
            )
        )
        // 정상은 집계에서 제외
        sourceStore.save(
            RssSource(
                id = "", name = "Valid", url = "https://v.com/rss", categoryId = category.id,
                termsReviewedAt = now.minusSeconds(80L * 86_400),
                expectedReviewAt = now.plusSeconds(100L * 86_400),
                crawlApproved = true
            )
        )

        val after = sourceStore.countComplianceAttention(now)
        (after - before) shouldBe 3
    }
}
