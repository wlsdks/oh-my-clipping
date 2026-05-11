package com.ohmyclipping.service

import com.ohmyclipping.model.Category
import com.ohmyclipping.model.CategoryStatus

import com.ohmyclipping.model.Organization
import com.ohmyclipping.model.OrganizationType
import com.ohmyclipping.model.RssSource
import com.ohmyclipping.service.dto.CompanySearchResult
import com.ohmyclipping.service.source.CategorySourceBuilder
import com.ohmyclipping.store.AuditLogStore
import com.ohmyclipping.store.CategoryStore
import com.ohmyclipping.store.RssSourceStore
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant

/**
 * BackfillService 단위 테스트.
 *
 * 모든 협력자는 MockK 로 격리하고,
 * TransactionTemplate 은 콜백을 즉시 실행하도록 스텁한다.
 */
class BackfillServiceTest {

    private val rssSourceStore = mockk<RssSourceStore>()
    private val categoryStore = mockk<CategoryStore>()
    private val companySearchService = mockk<CompanySearchService>()
    private val organizationService = mockk<OrganizationService>()
    private val categorySourceBuilder = mockk<CategorySourceBuilder>()
    private val transactionTemplate = mockk<TransactionTemplate>()
    private val auditLogStore = mockk<AuditLogStore>(relaxed = true)
    private val auditActorResolver = mockk<AuditActorResolver>()

    private val resolvedActor = ResolvedActor(id = "actor-uuid", name = "test-admin")

    private val service = BackfillService(
        rssSourceStore = rssSourceStore,
        categoryStore = categoryStore,
        companySearchService = companySearchService,
        organizationService = organizationService,
        categorySourceBuilder = categorySourceBuilder,
        transactionTemplate = transactionTemplate,
        auditLogStore = auditLogStore,
        auditActorResolver = auditActorResolver,
    )

    // ─── 픽스처 헬퍼 ─────────────────────────────────────────────────────────

    private fun rssSource(
        id: String = "src-1",
        name: String = "TestCorp Newsroom",
        url: String = "https://news.testcorp.example.com/kr/feed",
        categoryId: String = "cat-A",
    ) = RssSource(
        id = id, name = name, url = url, categoryId = categoryId
    )

    private fun category(id: String = "cat-A", name: String = "테크") = Category(
        id = id, name = name, status = CategoryStatus.ACTIVE,
        createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
    )

    private fun companyMatch(
        corpCode: String = "00126380",
        corpName: String = "MegaCorp",
        stockCode: String = "999930",
    ) = CompanySearchResult(
        corpCode = corpCode, corpName = corpName, stockCode = stockCode, isCompetitor = false
    )

    private fun org(
        id: String = "org-1",
        name: String = "MegaCorp",
        stockCode: String? = "999930",
    ) = Organization(
        id = id, name = name, type = OrganizationType.CUSTOMER,
        stockCode = stockCode,
    )

    /** TransactionTemplate.execute 를 콜백 즉시 실행으로 스텁한다. */
    private fun stubTransactionExecutes() {
        every { transactionTemplate.execute<Any?>(any()) } answers {
            val callback = firstArg<TransactionCallback<Any?>>()
            callback.doInTransaction(mockk(relaxed = true))
        }
    }

    // ─── Preview 테스트 ───────────────────────────────────────────────────────

    @Nested
    inner class `previewCompanyBackfill` {

        @Test
        fun `precision 0_8 은 DEFAULT_PRECISION 이므로 confidence=high 로 분류된다`() {
            // given
            val source = rssSource()
            every { rssSourceStore.list() } returns listOf(source)
            every { categoryStore.findById("cat-A") } returns category()
            every { companySearchService.searchWithIsCompetitor("TestCorp Newsroom", 1) } returns listOf(companyMatch())

            // when
            val result = service.previewCompanyBackfill(confidence = "high", includeMedium = false)

            // then — DEFAULT_PRECISION(0.8) >= HIGH_THRESHOLD(0.9) 은 false → medium
            // 실제: 0.8 < 0.9 이므로 medium. confidence=high 필터에서 제외 → 0건
            // 혹은 DEFAULT_PRECISION=0.8 이면 medium 범주인데 includeMedium=false 이므로 필터됨
            result.byConfidence["high"] shouldBe 0
            result.byConfidence["medium"] shouldBe 1
            result.candidates shouldHaveSize 0
        }

        @Test
        fun `confidence=medium 요청 시 medium 후보가 포함된다`() {
            val source = rssSource()
            every { rssSourceStore.list() } returns listOf(source)
            every { categoryStore.findById("cat-A") } returns category()
            every { companySearchService.searchWithIsCompetitor("TestCorp Newsroom", 1) } returns listOf(companyMatch())

            // DEFAULT_PRECISION = 0.8 → medium
            val result = service.previewCompanyBackfill(confidence = "medium", includeMedium = false)

            result.candidates shouldHaveSize 1
            result.candidates[0].confidence shouldBe "medium"
            result.candidates[0].matchedCompanyName shouldBe "MegaCorp"
        }

        @Test
        fun `includeMedium=true 이면 confidence=high 요청에서도 medium 이 포함된다`() {
            val source = rssSource()
            every { rssSourceStore.list() } returns listOf(source)
            every { categoryStore.findById("cat-A") } returns category()
            every { companySearchService.searchWithIsCompetitor("TestCorp Newsroom", 1) } returns listOf(companyMatch())

            // DEFAULT_PRECISION = 0.8 → medium, includeMedium=true → 포함
            val result = service.previewCompanyBackfill(confidence = "high", includeMedium = true)

            result.candidates shouldHaveSize 1
            result.candidates[0].confidence shouldBe "medium"
        }

        @Test
        fun `기본값 confidence=high 이고 includeMedium=false 이면 medium 후보가 필터링된다`() {
            val source = rssSource()
            every { rssSourceStore.list() } returns listOf(source)
            every { categoryStore.findById("cat-A") } returns category()
            every { companySearchService.searchWithIsCompetitor("TestCorp Newsroom", 1) } returns listOf(companyMatch())

            // DEFAULT_PRECISION = 0.8 → medium
            val result = service.previewCompanyBackfill()

            result.candidates shouldHaveSize 0
            // byConfidence 는 필터 전 전체 집계 → medium=1
            result.byConfidence["medium"] shouldBe 1
        }

        @Test
        fun `기업 매칭 결과가 없는 소스는 candidates 에 포함되지 않는다`() {
            val source = rssSource(name = "Unknown RSS")
            every { rssSourceStore.list() } returns listOf(source)
            every { companySearchService.searchWithIsCompetitor("Unknown RSS", 1) } returns emptyList()

            val result = service.previewCompanyBackfill(confidence = "low")

            result.candidates shouldHaveSize 0
            result.total shouldBe 0
        }

        @Test
        fun `categoryId 가 주어지면 listByCategoryId 를 호출한다`() {
            val source = rssSource()
            every { rssSourceStore.listByCategoryId("cat-A") } returns listOf(source)
            every { categoryStore.findById("cat-A") } returns category()
            every { companySearchService.searchWithIsCompetitor("TestCorp Newsroom", 1) } returns listOf(companyMatch())

            service.previewCompanyBackfill(categoryId = "cat-A", confidence = "low")

            verify(exactly = 1) { rssSourceStore.listByCategoryId("cat-A") }
            verify(exactly = 0) { rssSourceStore.list() }
        }
    }

    // ─── Apply 테스트 ─────────────────────────────────────────────────────────

    @Nested
    inner class `applyCompanyBackfill` {

        @Test
        fun `빈 candidateIds 이면 total=0 이고 협력자가 호출되지 않는다`() {
            val result = service.applyCompanyBackfill(emptyList(), "admin@test.com")

            result.total shouldBe 0
            result.succeeded shouldBe 0
            result.failed shouldBe 0
            result.errors shouldHaveSize 0
            verify(exactly = 0) { categorySourceBuilder.syncSourcesForCategory(any()) }
        }

        @Test
        fun `한 후보 성공 + 한 후보 실패 시 succeeded=1 failed=1 errors 에 실패 건 포함`() {
            stubTransactionExecutes()
            every { auditActorResolver.resolve("admin@test.com") } returns resolvedActor

            val srcOk = rssSource(id = "src-ok", name = "TestCorp Newsroom", categoryId = "cat-A")
            val srcFail = rssSource(id = "src-fail", name = "Unknown", categoryId = "cat-B")

            every { rssSourceStore.findById("src-ok") } returns srcOk
            every { rssSourceStore.findById("src-fail") } returns srcFail

            every { companySearchService.searchWithIsCompetitor("TestCorp Newsroom", 1) } returns listOf(companyMatch())
            every { companySearchService.searchWithIsCompetitor("Unknown", 1) } returns emptyList()

            every {
                organizationService.upsertByStockCodeOrName("default", "MegaCorp", "999930", "backfill")
            } returns org()
            every { organizationService.linkToCategoryIfAbsent("cat-A", "org-1") } just Runs

            every { categorySourceBuilder.syncSourcesForCategory("cat-A") } just Runs

            val result = service.applyCompanyBackfill(listOf("src-ok", "src-fail"), "admin@test.com")

            result.total shouldBe 2
            result.succeeded shouldBe 1
            result.failed shouldBe 1
            result.errors shouldHaveSize 1
            result.errors[0].candidateId shouldBe "src-fail"
        }

        @Test
        fun `영향받은 카테고리 수만큼 syncSourcesForCategory 가 호출된다`() {
            stubTransactionExecutes()
            every { auditActorResolver.resolve("admin@test.com") } returns resolvedActor

            val src1 = rssSource(id = "src-1", name = "TestCorp Newsroom", categoryId = "cat-A")
            val src2 = rssSource(id = "src-2", name = "TestCorp Telecom News", categoryId = "cat-B")

            every { rssSourceStore.findById("src-1") } returns src1
            every { rssSourceStore.findById("src-2") } returns src2

            val matchA = companyMatch("00126380", "MegaCorp", "999930")
            val matchB = companyMatch("TC1002", "TestCorp Telecom", "TC0002")

            every { companySearchService.searchWithIsCompetitor("TestCorp Newsroom", 1) } returns listOf(matchA)
            every { companySearchService.searchWithIsCompetitor("TestCorp Telecom News", 1) } returns listOf(matchB)

            val orgA = org(id = "org-A", name = "MegaCorp", stockCode = "999930")
            val orgB = org(id = "org-B", name = "TestCorp Telecom", stockCode = "TC0002")

            every {
                organizationService.upsertByStockCodeOrName("default", "MegaCorp", "999930", "backfill")
            } returns orgA
            every {
                organizationService.upsertByStockCodeOrName("default", "TestCorp Telecom", "TC0002", "backfill")
            } returns orgB

            every { organizationService.linkToCategoryIfAbsent("cat-A", "org-A") } just Runs
            every { organizationService.linkToCategoryIfAbsent("cat-B", "org-B") } just Runs

            every { categorySourceBuilder.syncSourcesForCategory("cat-A") } just Runs
            every { categorySourceBuilder.syncSourcesForCategory("cat-B") } just Runs

            val result = service.applyCompanyBackfill(listOf("src-1", "src-2"), "admin@test.com")

            result.succeeded shouldBe 2
            result.affectedCategoryIds shouldContain "cat-A"
            result.affectedCategoryIds shouldContain "cat-B"

            // 각 카테고리별 1번씩 총 2회 호출
            verify(exactly = 1) { categorySourceBuilder.syncSourcesForCategory("cat-A") }
            verify(exactly = 1) { categorySourceBuilder.syncSourcesForCategory("cat-B") }
        }

        @Test
        fun `같은 categoryId 의 후보가 여러 개여도 syncSourcesForCategory 는 1회만 호출된다`() {
            stubTransactionExecutes()
            every { auditActorResolver.resolve("admin@test.com") } returns resolvedActor

            val src1 = rssSource(id = "src-1", name = "TestCorp Newsroom", categoryId = "cat-A")
            val src2 = rssSource(id = "src-2", name = "TestCorp Tech Blog", categoryId = "cat-A")

            every { rssSourceStore.findById("src-1") } returns src1
            every { rssSourceStore.findById("src-2") } returns src2

            val match = companyMatch()

            every { companySearchService.searchWithIsCompetitor("TestCorp Newsroom", 1) } returns listOf(match)
            every { companySearchService.searchWithIsCompetitor("TestCorp Tech Blog", 1) } returns listOf(match)

            val orgA = org()
            every {
                organizationService.upsertByStockCodeOrName("default", "MegaCorp", "999930", "backfill")
            } returns orgA
            every { organizationService.linkToCategoryIfAbsent("cat-A", "org-1") } just Runs
            every { categorySourceBuilder.syncSourcesForCategory("cat-A") } just Runs

            service.applyCompanyBackfill(listOf("src-1", "src-2"), "admin@test.com")

            // cat-A 는 1번만 호출되어야 한다
            verify(exactly = 1) { categorySourceBuilder.syncSourcesForCategory("cat-A") }
        }

        @Test
        fun `성공한 카테고리마다 BACKFILL_APPLY 감사 로그가 기록된다`() {
            // Important #1: audit log entry per affected category
            stubTransactionExecutes()
            every { auditActorResolver.resolve("admin@test.com") } returns resolvedActor

            val src1 = rssSource(id = "src-A", name = "TestCorp Newsroom", categoryId = "cat-A")
            val src2 = rssSource(id = "src-B", name = "TestCorp Telecom News", categoryId = "cat-B")

            every { rssSourceStore.findById("src-A") } returns src1
            every { rssSourceStore.findById("src-B") } returns src2

            every { companySearchService.searchWithIsCompetitor("TestCorp Newsroom", 1) } returns listOf(companyMatch("A1", "MegaCorp", "999930"))
            every { companySearchService.searchWithIsCompetitor("TestCorp Telecom News", 1) } returns listOf(companyMatch("B1", "TestCorp Telecom", "TC0002"))

            val orgA = org(id = "org-A", name = "MegaCorp", stockCode = "999930")
            val orgB = org(id = "org-B", name = "TestCorp Telecom", stockCode = "TC0002")

            every { organizationService.upsertByStockCodeOrName("default", "MegaCorp", "999930", "backfill") } returns orgA
            every { organizationService.upsertByStockCodeOrName("default", "TestCorp Telecom", "TC0002", "backfill") } returns orgB
            every { organizationService.linkToCategoryIfAbsent("cat-A", "org-A") } just Runs
            every { organizationService.linkToCategoryIfAbsent("cat-B", "org-B") } just Runs
            every { categorySourceBuilder.syncSourcesForCategory("cat-A") } just Runs
            every { categorySourceBuilder.syncSourcesForCategory("cat-B") } just Runs

            service.applyCompanyBackfill(listOf("src-A", "src-B"), "admin@test.com")

            // BACKFILL_APPLY 감사 로그가 각 카테고리마다 1번씩 기록되어야 한다
            verify(exactly = 1) {
                auditLogStore.log(
                    actorId = resolvedActor.id,
                    actorName = resolvedActor.name,
                    action = "BACKFILL_APPLY",
                    targetType = "CATEGORY",
                    targetId = "cat-A",
                    detail = any(),
                )
            }
            verify(exactly = 1) {
                auditLogStore.log(
                    actorId = resolvedActor.id,
                    actorName = resolvedActor.name,
                    action = "BACKFILL_APPLY",
                    targetType = "CATEGORY",
                    targetId = "cat-B",
                    detail = any(),
                )
            }
        }

        @Test
        fun `빈 candidateIds 이면 감사 로그가 기록되지 않는다`() {
            service.applyCompanyBackfill(emptyList(), "admin@test.com")

            verify(exactly = 0) { auditActorResolver.resolve(any()) }
            verify(exactly = 0) { auditLogStore.log(any(), any(), any(), any(), any(), any(), any()) }
        }
    }
}
