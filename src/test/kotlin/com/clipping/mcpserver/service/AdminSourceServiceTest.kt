package com.clipping.mcpserver.service

import com.clipping.mcpserver.service.source.SourceVerificationService
import com.clipping.mcpserver.error.ConflictException
import com.clipping.mcpserver.error.InvalidInputException
import com.clipping.mcpserver.model.Category
import com.clipping.mcpserver.model.RssSource
import com.clipping.mcpserver.model.SourceLegalBasis
import com.clipping.mcpserver.model.SourceRegionType
import com.clipping.mcpserver.security.UrlSafetyValidator
import com.clipping.mcpserver.store.AuditLogStore
import com.clipping.mcpserver.store.CategoryStore
import com.clipping.mcpserver.store.RssSourceStore
import com.clipping.mcpserver.error.NotFoundException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.net.URI
import java.time.Instant
import java.time.LocalDate

class AdminSourceServiceTest {

    @Test
    fun `findAllPaged should clamp unsafe pagination before delegating to store`() {
        val sourceStore = mockk<RssSourceStore>()
        every { sourceStore.findAll(null, "ai", null, 0, 100) } returns emptyList()
        val service = AdminSourceService(
            sourceStore,
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk<AuditLogStore>(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            EntityRevisionRecorder(mockk(relaxed = true)),
            passthroughAuditActorResolver()
        )

        val result = service.findAllPaged(search = "ai", offset = -100, limit = 10_000)

        result shouldBe emptyList()
        verify(exactly = 1) { sourceStore.findAll(null, "ai", null, 0, 100) }
    }

    @Test
    fun `findAllPaged should preserve valid pagination`() {
        val sourceStore = mockk<RssSourceStore>()
        every { sourceStore.findAll("cat-1", null, null, 20, 30) } returns emptyList()
        val service = AdminSourceService(
            sourceStore,
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk<AuditLogStore>(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            EntityRevisionRecorder(mockk(relaxed = true)),
            passthroughAuditActorResolver()
        )

        val result = service.findAllPaged(categoryId = "cat-1", offset = 20, limit = 30)

        result shouldBe emptyList()
        verify(exactly = 1) { sourceStore.findAll("cat-1", null, null, 20, 30) }
    }

    @Test
    fun `updateSource should throw conflict when updatedAt mismatch`() {
        val sourceStore = mockk<RssSourceStore>()
        val verificationService = mockk<SourceVerificationService>()
        val urlSafetyValidator = mockk<UrlSafetyValidator>()
        val categoryStore = mockk<CategoryStore>()
        val source = source(id = "src-1", updatedAt = Instant.parse("2026-03-02T00:00:00Z"))

        every { sourceStore.findById("src-1") } returns source
        every { sourceStore.updateWithExpectedUpdatedAt(any(), any()) } returns null

        val service = AdminSourceService(sourceStore, verificationService, urlSafetyValidator, categoryStore, mockk<AuditLogStore>(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), EntityRevisionRecorder(mockk(relaxed = true)), passthroughAuditActorResolver())
        val exception = shouldThrow<ConflictException> {
            service.updateSource(
                id = "src-1",
                name = "Updated source",
                url = null,
                sourceRegionRaw = null,
                emoji = null,
                isActive = true,
                categoryId = null,
                legalBasisRaw = null,
                summaryAllowed = null,
                fulltextAllowed = null,
                reviewNotes = null,
                expectedUpdatedAt = Instant.parse("2026-03-01T23:59:59Z")
            )
        }

        exception.message shouldBe "소스가 다른 관리자에 의해 변경되었습니다. 새로고침 후 다시 저장해주세요."
        verify(exactly = 1) { sourceStore.updateWithExpectedUpdatedAt(any(), any()) }
        verify(exactly = 0) { sourceStore.update(any()) }
    }

    @Test
    fun `approveSource should throw conflict when updatedAt mismatch`() {
        val sourceStore = mockk<RssSourceStore>()
        val verificationService = mockk<SourceVerificationService>()
        val urlSafetyValidator = mockk<UrlSafetyValidator>()
        val categoryStore = mockk<CategoryStore>()
        val source = source(id = "src-1", updatedAt = Instant.parse("2026-03-02T00:00:00Z"))

        every { sourceStore.findById("src-1") } returns source
        every { sourceStore.updateWithExpectedUpdatedAt(any(), any()) } returns null

        val service = AdminSourceService(sourceStore, verificationService, urlSafetyValidator, categoryStore, mockk<AuditLogStore>(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), EntityRevisionRecorder(mockk(relaxed = true)), passthroughAuditActorResolver())
        val exception = shouldThrow<ConflictException> {
            service.approveSource(
                id = "src-1",
                approved = true,
                approvedBy = "admin",
                legalBasisRaw = null,
                summaryAllowed = null,
                fulltextAllowed = null,
                reviewNotes = null,
                expectedUpdatedAt = Instant.parse("2026-03-01T23:59:59Z")
            )
        }

        exception.message shouldBe "소스 승인 상태가 다른 관리자에 의해 변경되었습니다. 새로고침 후 다시 시도해주세요."
        verify(exactly = 1) { sourceStore.updateWithExpectedUpdatedAt(any(), any()) }
        verify(exactly = 0) { sourceStore.update(any()) }
    }

    @Test
    fun `createSource should reject unknown category`() {
        val sourceStore = mockk<RssSourceStore>()
        val verificationService = mockk<SourceVerificationService>()
        val urlSafetyValidator = mockk<UrlSafetyValidator>()
        val categoryStore = mockk<CategoryStore>()

        every { categoryStore.findById("missing-cat") } returns null

        val service = AdminSourceService(sourceStore, verificationService, urlSafetyValidator, categoryStore, mockk<AuditLogStore>(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), EntityRevisionRecorder(mockk(relaxed = true)), passthroughAuditActorResolver())
        val exception = shouldThrow<InvalidInputException> {
            service.createSource(
                name = "Source",
                url = "https://example.com/rss",
                sourceRegionRaw = null,
                emoji = null,
                categoryId = "missing-cat",
                legalBasisRaw = null,
                summaryAllowed = null,
                fulltextAllowed = null,
                reviewNotes = null
            )
        }

        exception.message shouldBe "Category not found: missing-cat"
        verify(exactly = 0) { urlSafetyValidator.validatePublicHttpUrl(any()) }
        verify(exactly = 0) { sourceStore.save(any()) }
    }

    @Test
    fun `updateSource should reject blank category id`() {
        val sourceStore = mockk<RssSourceStore>()
        val verificationService = mockk<SourceVerificationService>()
        val urlSafetyValidator = mockk<UrlSafetyValidator>()
        val categoryStore = mockk<CategoryStore>()
        val source = source(id = "src-1", updatedAt = Instant.parse("2026-03-02T00:00:00Z"))

        every { sourceStore.findById("src-1") } returns source

        val service = AdminSourceService(sourceStore, verificationService, urlSafetyValidator, categoryStore, mockk<AuditLogStore>(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), EntityRevisionRecorder(mockk(relaxed = true)), passthroughAuditActorResolver())
        val exception = shouldThrow<InvalidInputException> {
            service.updateSource(
                id = "src-1",
                name = null,
                url = null,
                sourceRegionRaw = null,
                emoji = null,
                isActive = null,
                categoryId = "   ",
                legalBasisRaw = null,
                summaryAllowed = null,
                fulltextAllowed = null,
                reviewNotes = null,
                expectedUpdatedAt = null
            )
        }

        exception.message shouldBe "categoryId must not be blank"
        verify(exactly = 0) { sourceStore.update(any()) }
        verify(exactly = 0) { sourceStore.updateWithExpectedUpdatedAt(any(), any()) }
    }

    @Nested
    inner class `정책 강화 테스트` {

        private val sourceStore = mockk<RssSourceStore>()
        private val verificationService = mockk<SourceVerificationService>()
        private val urlSafetyValidator = mockk<UrlSafetyValidator>()
        private val categoryStore = mockk<CategoryStore>()
        private val auditLogStore = mockk<AuditLogStore>(relaxed = true)
        private val service = AdminSourceService(sourceStore, verificationService, urlSafetyValidator, categoryStore, auditLogStore, mockk(relaxed = true), mockk(relaxed = true), EntityRevisionRecorder(mockk(relaxed = true)), passthroughAuditActorResolver())

        private val category = Category(id = "cat-1", name = "테스트 카테고리")

        /** createSource 호출을 위한 공통 mock 설정 */
        private fun stubCreateSourceDeps(
            categoryId: String = "cat-1",
            existingSources: List<RssSource> = emptyList(),
            url: String = "https://example.com/rss"
        ) {
            every { categoryStore.findById(categoryId) } returns category.copy(id = categoryId)
            every { sourceStore.listByCategoryId(categoryId) } returns existingSources
            every { urlSafetyValidator.validatePublicHttpUrl(url) } returns URI(url)
            every { sourceStore.existsByCategoryIdAndUrl(categoryId, url) } returns false
            every { sourceStore.save(any()) } answers { firstArg<RssSource>().copy(id = "new-id") }
        }

        @Test
        fun `카테고리당 소스 10개 초과 시 거부한다`() {
            // 이미 10개의 소스가 존재하는 상황을 설정한다.
            val existingSources = (1..10).map { i ->
                source(id = "src-$i", updatedAt = Instant.now())
            }
            every { categoryStore.findById("cat-1") } returns category
            every { sourceStore.listByCategoryId("cat-1") } returns existingSources

            val exception = shouldThrow<InvalidInputException> {
                service.createSource(
                    name = "새 소스",
                    url = "https://example.com/rss",
                    sourceRegionRaw = null,
                    emoji = null,
                    categoryId = "cat-1",
                    legalBasisRaw = null,
                    summaryAllowed = null,
                    fulltextAllowed = null,
                    reviewNotes = null
                )
            }

            exception.message shouldContain "최대 10개"
            verify(exactly = 0) { sourceStore.save(any()) }
        }

        @Test
        fun `같은 카테고리에 이미 등록된 소스 URL이면 ConflictException을 던진다`() {
            stubCreateSourceDeps()
            every { sourceStore.existsByCategoryIdAndUrl("cat-1", "https://example.com/rss") } returns true

            val exception = shouldThrow<ConflictException> {
                service.createSource(
                    name = "중복 소스",
                    url = "https://example.com/rss",
                    sourceRegionRaw = null,
                    emoji = null,
                    categoryId = "cat-1",
                    legalBasisRaw = null,
                    summaryAllowed = null,
                    fulltextAllowed = null,
                    reviewNotes = null
                )
            }

            exception.message shouldBe "이미 등록된 소스 URL입니다: https://example.com/rss"
            verify(exactly = 0) { sourceStore.save(any()) }
        }

        @Test
        fun `fulltextAllowed가 true이면 거부한다`() {
            stubCreateSourceDeps()

            val exception = shouldThrow<InvalidInputException> {
                service.createSource(
                    name = "소스",
                    url = "https://example.com/rss",
                    sourceRegionRaw = null,
                    emoji = null,
                    categoryId = "cat-1",
                    legalBasisRaw = null,
                    summaryAllowed = null,
                    fulltextAllowed = true,
                    reviewNotes = null
                )
            }

            exception.message shouldContain "fulltextAllowed=true is not allowed"
            verify(exactly = 0) { sourceStore.save(any()) }
        }

        @Test
        fun `PROHIBITED 법적 근거는 요약과 원문을 모두 차단한다`() {
            stubCreateSourceDeps()

            service.createSource(
                name = "금지 소스",
                url = "https://example.com/rss",
                sourceRegionRaw = null,
                emoji = null,
                categoryId = "cat-1",
                legalBasisRaw = "PROHIBITED",
                summaryAllowed = true,
                fulltextAllowed = false,
                reviewNotes = null
            )

            // 저장된 소스의 정책 필드를 캡처해 검증한다.
            val captured = slot<RssSource>()
            verify { sourceStore.save(capture(captured)) }
            captured.captured.legalBasis shouldBe SourceLegalBasis.PROHIBITED
            captured.captured.summaryAllowed shouldBe false
            captured.captured.fulltextAllowed shouldBe false
        }

        @Test
        fun `잘못된 legalBasis 문자열은 거부한다`() {
            stubCreateSourceDeps()

            val exception = shouldThrow<InvalidInputException> {
                service.createSource(
                    name = "소스",
                    url = "https://example.com/rss",
                    sourceRegionRaw = null,
                    emoji = null,
                    categoryId = "cat-1",
                    legalBasisRaw = "INVALID_BASIS",
                    summaryAllowed = null,
                    fulltextAllowed = null,
                    reviewNotes = null
                )
            }

            exception.message shouldContain "Unsupported legalBasis"
            verify(exactly = 0) { sourceStore.save(any()) }
        }

        @Test
        fun `잘못된 sourceRegion 문자열은 거부한다`() {
            stubCreateSourceDeps()

            val exception = shouldThrow<InvalidInputException> {
                service.createSource(
                    name = "소스",
                    url = "https://example.com/rss",
                    sourceRegionRaw = "INVALID_REGION",
                    emoji = null,
                    categoryId = "cat-1",
                    legalBasisRaw = null,
                    summaryAllowed = null,
                    fulltextAllowed = null,
                    reviewNotes = null
                )
            }

            exception.message shouldContain "Unsupported sourceRegion"
            verify(exactly = 0) { sourceStore.save(any()) }
        }

        @Test
        fun `빈 이름은 거부한다`() {
            val exception = shouldThrow<InvalidInputException> {
                service.createSource(
                    name = "   ",
                    url = "https://example.com/rss",
                    sourceRegionRaw = null,
                    emoji = null,
                    categoryId = "cat-1",
                    legalBasisRaw = null,
                    summaryAllowed = null,
                    fulltextAllowed = null,
                    reviewNotes = null
                )
            }

            // InputSanitizer가 필수 입력 거부 메시지를 한국어로 일관화한다.
            exception.message shouldContain "소스 이름"
            verify(exactly = 0) { sourceStore.save(any()) }
        }

        @Test
        fun `kr 도메인은 DOMESTIC으로 분류한다`() {
            val krUrl = "https://news.example.co.kr/rss"
            stubCreateSourceDeps(url = krUrl)

            service.createSource(
                name = "국내 소스",
                url = krUrl,
                sourceRegionRaw = null,
                emoji = null,
                categoryId = "cat-1",
                legalBasisRaw = null,
                summaryAllowed = null,
                fulltextAllowed = null,
                reviewNotes = null
            )

            // 저장된 소스의 지역 분류를 캡처해 검증한다.
            val captured = slot<RssSource>()
            verify { sourceStore.save(capture(captured)) }
            captured.captured.sourceRegion shouldBe SourceRegionType.DOMESTIC
        }

        @Test
        fun `crawlApproved=false이면 verificationStatus가 PENDING으로 생성된다`() {
            stubCreateSourceDeps()

            service.createSource(
                name = "미승인 소스",
                url = "https://example.com/rss",
                sourceRegionRaw = null,
                emoji = null,
                categoryId = "cat-1",
                legalBasisRaw = null,
                summaryAllowed = null,
                fulltextAllowed = null,
                reviewNotes = null,
                crawlApproved = false
            )

            val captured = slot<RssSource>()
            verify { sourceStore.save(capture(captured)) }
            captured.captured.verificationStatus shouldBe "PENDING"
        }

        @Test
        fun `crawlApproved=true이면 verificationStatus가 VERIFIED로 생성된다`() {
            stubCreateSourceDeps()

            service.createSource(
                name = "승인 소스",
                url = "https://example.com/rss",
                sourceRegionRaw = null,
                emoji = null,
                categoryId = "cat-1",
                legalBasisRaw = null,
                summaryAllowed = null,
                fulltextAllowed = null,
                reviewNotes = null,
                crawlApproved = true,
                approvedBy = "admin"
            )

            val captured = slot<RssSource>()
            verify { sourceStore.save(capture(captured)) }
            captured.captured.verificationStatus shouldBe "VERIFIED"
            captured.captured.crawlApproved shouldBe true
        }
    }

    @Nested
    inner class `getSourceAnalytics` {

        @Test
        fun `정상 조회 시 일별 수집 통계를 반환한다`() {
            val sourceStore = mockk<RssSourceStore>()
            val src = source(id = "src-1", updatedAt = Instant.parse("2026-04-16T00:00:00Z"))

            every { sourceStore.findById("src-1") } returns src
            every { sourceStore.countDailyArticlesBySource("src-1", any()) } returns listOf(
                LocalDate.of(2026, 4, 16) to 12,
                LocalDate.of(2026, 4, 15) to 8
            )

            val service = AdminSourceService(
                sourceStore, mockk(), mockk(), mockk(), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), EntityRevisionRecorder(mockk(relaxed = true)), passthroughAuditActorResolver()
            )
            val result = service.getSourceAnalytics("src-1", 30)

            result.sourceId shouldBe "src-1"
            result.sourceName shouldBe "테스트 소스"
            result.days shouldBe 30
            result.totalArticles shouldBe 20
            result.avgArticlesPerDay shouldBe 0.7
            result.reliabilityScore shouldBe 80
            result.dailyArticleCounts shouldHaveSize 2
            result.dailyArticleCounts[0].date shouldBe "2026-04-16"
            result.dailyArticleCounts[0].count shouldBe 12
        }

        @Test
        fun `존재하지 않는 소스 조회 시 NotFoundException 발생`() {
            val sourceStore = mockk<RssSourceStore>()
            every { sourceStore.findById("missing") } returns null

            val service = AdminSourceService(
                sourceStore, mockk(), mockk(), mockk(), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), EntityRevisionRecorder(mockk(relaxed = true)), passthroughAuditActorResolver()
            )

            shouldThrow<NotFoundException> {
                service.getSourceAnalytics("missing", 30)
            }
        }

        @Test
        fun `days가 범위를 벗어나면 1~90으로 클램핑한다`() {
            val sourceStore = mockk<RssSourceStore>()
            val src = source(id = "src-1", updatedAt = Instant.parse("2026-04-16T00:00:00Z"))

            every { sourceStore.findById("src-1") } returns src
            every { sourceStore.countDailyArticlesBySource("src-1", any()) } returns emptyList()

            val service = AdminSourceService(
                sourceStore, mockk(), mockk(), mockk(), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), EntityRevisionRecorder(mockk(relaxed = true)), passthroughAuditActorResolver()
            )

            // days=0 → 클램핑 → 1
            val result1 = service.getSourceAnalytics("src-1", 0)
            result1.days shouldBe 1

            // days=200 → 클램핑 → 90
            val result2 = service.getSourceAnalytics("src-1", 200)
            result2.days shouldBe 90
        }
    }

    private fun source(id: String, updatedAt: Instant): RssSource =
        RssSource(
            id = id,
            name = "테스트 소스",
            url = "https://example.com/rss",
            emoji = null,
            isActive = true,
            crawlApproved = false,
            approvedBy = null,
            approvedAt = null,
            legalBasis = SourceLegalBasis.QUOTATION_ONLY,
            summaryAllowed = true,
            fulltextAllowed = false,
            termsReviewedAt = Instant.parse("2026-03-01T00:00:00Z"),
            reviewNotes = "검토 완료",
            verificationStatus = "VERIFIED",
            reliabilityScore = 80,
            lastCrawlError = null,
            crawlFailCount = 0,
            categoryId = "cat-1",
            createdAt = Instant.parse("2026-03-01T00:00:00Z"),
            updatedAt = updatedAt
        )

    @Nested
    inner class `입력 제약 검증` {

        private val sourceStore = mockk<RssSourceStore>()
        private val verificationService = mockk<SourceVerificationService>()
        private val urlSafetyValidator = mockk<UrlSafetyValidator>()
        private val categoryStore = mockk<CategoryStore>()
        private val service = AdminSourceService(
            sourceStore, verificationService, urlSafetyValidator, categoryStore,
            mockk<AuditLogStore>(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            EntityRevisionRecorder(mockk(relaxed = true)),
            passthroughAuditActorResolver()
        )

        @Test
        fun `소스 이름이 200자 초과면 InvalidInputException을 던진다`() {
            shouldThrow<InvalidInputException> {
                service.createSource(
                    name = "a".repeat(201),
                    url = "https://example.com/rss",
                    sourceRegionRaw = null,
                    emoji = null,
                    categoryId = "cat-1",
                    legalBasisRaw = null,
                    summaryAllowed = null,
                    fulltextAllowed = null,
                    reviewNotes = null
                )
            }
        }

        @Test
        fun `검토 메모가 1000자 초과면 InvalidInputException을 던진다`() {
            shouldThrow<InvalidInputException> {
                service.createSource(
                    name = "정상 소스",
                    url = "https://example.com/rss",
                    sourceRegionRaw = null,
                    emoji = null,
                    categoryId = "cat-1",
                    legalBasisRaw = null,
                    summaryAllowed = null,
                    fulltextAllowed = null,
                    reviewNotes = "a".repeat(1001)
                )
            }
        }

        @Test
        fun `업데이트 시 소스 이름이 200자 초과면 InvalidInputException을 던진다`() {
            val existing = source(id = "src-1", updatedAt = Instant.parse("2026-03-02T00:00:00Z"))
            every { sourceStore.findById("src-1") } returns existing

            shouldThrow<InvalidInputException> {
                service.updateSource(
                    id = "src-1",
                    name = "a".repeat(201),
                    url = null,
                    sourceRegionRaw = null,
                    emoji = null,
                    isActive = null,
                    categoryId = null,
                    legalBasisRaw = null,
                    summaryAllowed = null,
                    fulltextAllowed = null,
                    reviewNotes = null,
                    expectedUpdatedAt = null
                )
            }
        }
    }
}

/** 테스트에서 `AuditActorResolver`를 principal → actorId passthrough 로 동작시키는 헬퍼. */
private fun passthroughAuditActorResolver(): AuditActorResolver =
    mockk<AuditActorResolver>().apply {
        every { resolve(any()) } answers {
            val arg = firstArg<String?>()
            ResolvedActor(id = arg, name = arg ?: "system")
        }
    }
