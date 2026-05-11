package com.clipping.mcpserver.service

import com.clipping.mcpserver.service.source.SourceVerificationService
import com.clipping.mcpserver.error.ConflictException
import com.clipping.mcpserver.model.RssSource
import com.clipping.mcpserver.model.SourceLegalBasis
import com.clipping.mcpserver.model.SourceRegionType
import com.clipping.mcpserver.security.UrlSafetyValidator
import com.clipping.mcpserver.store.AuditLogStore
import com.clipping.mcpserver.store.CategoryStore
import com.clipping.mcpserver.store.RssSourceStore
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * `AdminSourceService.updateSource` 에서 소스 재활성화 시
 * `crawlFailCount` 를 0으로 리셋하고 revision 이력에 그 사실이
 * 포함되는지를 검증한다.
 */
class AdminSourceServiceReactivateTest {

    private val sourceStore = mockk<RssSourceStore>()
    private val verificationService = mockk<SourceVerificationService>()
    private val urlSafetyValidator = mockk<UrlSafetyValidator>()
    private val categoryStore = mockk<CategoryStore>()
    private val auditLogStore = mockk<AuditLogStore>(relaxed = true)
    private val entityRevisionRecorder = mockk<EntityRevisionRecorder>(relaxed = true)

    private val service = AdminSourceService(
        sourceStore,
        verificationService,
        urlSafetyValidator,
        categoryStore,
        auditLogStore,
        mockk(relaxed = true),
        mockk(relaxed = true),
        entityRevisionRecorder,
        passthroughAuditActorResolverForReactivate()
    )

    private fun inactiveSource(
        id: String = "src-1",
        crawlFailCount: Int = 7,
        updatedAt: Instant = Instant.parse("2026-04-19T00:00:00Z")
    ): RssSource = RssSource(
        id = id,
        name = "비활성 소스",
        url = "https://example.com/rss",
        emoji = null,
        isActive = false,
        crawlApproved = false,
        approvedBy = null,
        approvedAt = null,
        legalBasis = SourceLegalBasis.QUOTATION_ONLY,
        summaryAllowed = true,
        fulltextAllowed = false,
        termsReviewedAt = Instant.parse("2026-03-01T00:00:00Z"),
        reviewNotes = null,
        verificationStatus = "VERIFIED",
        reliabilityScore = 80,
        lastCrawlError = null,
        crawlFailCount = crawlFailCount,
        sourceRegion = SourceRegionType.GLOBAL,
        categoryId = "cat-1",
        createdAt = Instant.parse("2026-03-01T00:00:00Z"),
        updatedAt = updatedAt
    )

    @Nested
    inner class `재활성화 동작` {

        @Test
        fun `is_active false에서 true 전환 시 crawl_fail_count를 0으로 리셋한다`() {
            val existing = inactiveSource(crawlFailCount = 7)
            every { sourceStore.findById("src-1") } returns existing
            // 낙관적 잠금이 아닌 일반 update 경로를 사용한다.
            val updateCaptor = slot<RssSource>()
            every { sourceStore.update(capture(updateCaptor)) } answers {
                updateCaptor.captured
            }

            service.updateSource(
                id = "src-1",
                name = null,
                url = null,
                sourceRegionRaw = null,
                emoji = null,
                isActive = true,
                categoryId = null,
                legalBasisRaw = null,
                summaryAllowed = null,
                fulltextAllowed = null,
                reviewNotes = null,
                expectedUpdatedAt = null,
                actorUsername = "admin"
            )

            updateCaptor.captured.isActive shouldBe true
            updateCaptor.captured.crawlFailCount shouldBe 0
            verify(exactly = 1) { sourceStore.update(any()) }
        }

        @Test
        fun `이미 active 소스의 name만 변경 시 crawl_fail_count를 건드리지 않는다`() {
            // 이미 활성 상태 + 누적 실패 카운트가 있는 소스
            val existing = inactiveSource().copy(isActive = true, crawlFailCount = 5)
            every { sourceStore.findById("src-1") } returns existing
            val updateCaptor = slot<RssSource>()
            every { sourceStore.update(capture(updateCaptor)) } answers {
                updateCaptor.captured
            }

            service.updateSource(
                id = "src-1",
                name = "새로운 이름",
                url = null,
                sourceRegionRaw = null,
                emoji = null,
                isActive = true,
                categoryId = null,
                legalBasisRaw = null,
                summaryAllowed = null,
                fulltextAllowed = null,
                reviewNotes = null,
                expectedUpdatedAt = null,
                actorUsername = "admin"
            )

            updateCaptor.captured.name shouldBe "새로운 이름"
            updateCaptor.captured.crawlFailCount shouldBe 5
        }

        @Test
        fun `active 소스를 비활성화할 때는 crawl_fail_count를 보존한다`() {
            // true → false 방향 전환은 리셋 대상이 아니다.
            val existing = inactiveSource().copy(isActive = true, crawlFailCount = 4)
            every { sourceStore.findById("src-1") } returns existing
            val updateCaptor = slot<RssSource>()
            every { sourceStore.update(capture(updateCaptor)) } answers {
                updateCaptor.captured
            }

            service.updateSource(
                id = "src-1",
                name = null,
                url = null,
                sourceRegionRaw = null,
                emoji = null,
                isActive = false,
                categoryId = null,
                legalBasisRaw = null,
                summaryAllowed = null,
                fulltextAllowed = null,
                reviewNotes = null,
                expectedUpdatedAt = null,
                actorUsername = "admin"
            )

            updateCaptor.captured.isActive shouldBe false
            updateCaptor.captured.crawlFailCount shouldBe 4
        }
    }

    @Nested
    inner class `Revision 이력 기록` {

        @Test
        fun `재활성화 시 changedFields에 isActive와 crawlFailCount가 모두 포함된다`() {
            val existing = inactiveSource(crawlFailCount = 3)
            every { sourceStore.findById("src-1") } returns existing
            every { sourceStore.update(any()) } answers { firstArg() }
            val changedFieldsCaptor = slot<List<String>>()
            every {
                entityRevisionRecorder.record(
                    resourceType = any(),
                    resourceId = any(),
                    editorId = any(),
                    editorDisplayName = any(),
                    changedFields = capture(changedFieldsCaptor),
                    entity = any()
                )
            } returns mockk(relaxed = true)

            service.updateSource(
                id = "src-1",
                name = null,
                url = null,
                sourceRegionRaw = null,
                emoji = null,
                isActive = true,
                categoryId = null,
                legalBasisRaw = null,
                summaryAllowed = null,
                fulltextAllowed = null,
                reviewNotes = null,
                expectedUpdatedAt = null,
                actorUsername = "admin"
            )

            // 재활성화 diff — isActive 플래그와 리셋된 crawlFailCount가 함께 기록되어야 한다.
            changedFieldsCaptor.captured shouldContain "isActive"
            changedFieldsCaptor.captured shouldContain "crawlFailCount"
        }

        @Test
        fun `이미 active 소스의 name만 변경하면 changedFields에 crawlFailCount가 없다`() {
            val existing = inactiveSource().copy(isActive = true, crawlFailCount = 5)
            every { sourceStore.findById("src-1") } returns existing
            every { sourceStore.update(any()) } answers { firstArg() }
            val changedFieldsCaptor = slot<List<String>>()
            every {
                entityRevisionRecorder.record(
                    resourceType = any(),
                    resourceId = any(),
                    editorId = any(),
                    editorDisplayName = any(),
                    changedFields = capture(changedFieldsCaptor),
                    entity = any()
                )
            } returns mockk(relaxed = true)

            service.updateSource(
                id = "src-1",
                name = "이름 변경만",
                url = null,
                sourceRegionRaw = null,
                emoji = null,
                isActive = true,
                categoryId = null,
                legalBasisRaw = null,
                summaryAllowed = null,
                fulltextAllowed = null,
                reviewNotes = null,
                expectedUpdatedAt = null,
                actorUsername = "admin"
            )

            changedFieldsCaptor.captured shouldContain "name"
            changedFieldsCaptor.captured shouldNotContain "crawlFailCount"
            changedFieldsCaptor.captured shouldNotContain "isActive"
        }
    }

    @Nested
    inner class `기존 optimistic lock 동작 유지` {

        @Test
        fun `재활성화 중 expectedUpdatedAt이 맞지 않으면 ConflictException을 던진다`() {
            val existing = inactiveSource(updatedAt = Instant.parse("2026-04-19T10:00:00Z"))
            every { sourceStore.findById("src-1") } returns existing
            every { sourceStore.updateWithExpectedUpdatedAt(any(), any()) } returns null

            shouldThrow<ConflictException> {
                service.updateSource(
                    id = "src-1",
                    name = null,
                    url = null,
                    sourceRegionRaw = null,
                    emoji = null,
                    isActive = true,
                    categoryId = null,
                    legalBasisRaw = null,
                    summaryAllowed = null,
                    fulltextAllowed = null,
                    reviewNotes = null,
                    expectedUpdatedAt = Instant.parse("2026-04-19T09:00:00Z"),
                    actorUsername = "admin"
                )
            }

            verify(exactly = 1) { sourceStore.updateWithExpectedUpdatedAt(any(), any()) }
            verify(exactly = 0) { sourceStore.update(any()) }
        }
    }
}

/** 테스트 스코프에서 `AuditActorResolver` 를 passthrough 로 동작시키는 로컬 헬퍼. */
private fun passthroughAuditActorResolverForReactivate(): AuditActorResolver =
    mockk<AuditActorResolver>().apply {
        every { resolve(any()) } answers {
            val arg = firstArg<String?>()
            ResolvedActor(id = arg, name = arg ?: "system")
        }
    }
