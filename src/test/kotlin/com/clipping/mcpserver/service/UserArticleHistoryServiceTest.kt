package com.clipping.mcpserver.service

import com.clipping.mcpserver.content.ArticleContentExtractor
import com.clipping.mcpserver.content.ExtractedArticle
import com.clipping.mcpserver.error.NotFoundException
import com.clipping.mcpserver.model.AccountRole
import com.clipping.mcpserver.model.AdminUser
import com.clipping.mcpserver.model.BatchSummary
import com.clipping.mcpserver.model.Category
import com.clipping.mcpserver.model.Language
import com.clipping.mcpserver.model.OriginalContent
import com.clipping.mcpserver.model.UserClippingRequest
import com.clipping.mcpserver.model.UserClippingRequestStatus
import com.clipping.mcpserver.store.BatchSummaryStore
import com.clipping.mcpserver.store.CategoryStore
import com.clipping.mcpserver.store.DeliveryLogStore
import com.clipping.mcpserver.store.OriginalContentStore
import com.clipping.mcpserver.store.AdminUserStore
import com.clipping.mcpserver.store.BookmarkedArticleStore
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * UserArticleHistoryService 의 getArticleDetail 핵심 로직을 검증한다.
 * - 원문 자동 추출+저장
 * - 연관 뉴스 다른 카테고리 보충
 */
class UserArticleHistoryServiceTest {

    private val requestService = mockk<UserClippingRequestService>()
    private val batchSummaryStore = mockk<BatchSummaryStore>()
    private val categoryStore = mockk<CategoryStore>()
    private val bookmarkedArticleStore = mockk<BookmarkedArticleStore>()
    private val originalContentStore = mockk<OriginalContentStore>()
    private val articleContentExtractor = mockk<ArticleContentExtractor>()
    private val adminUserStore = mockk<AdminUserStore>()
    private val deliveryLogStore = mockk<DeliveryLogStore>(relaxed = true)

    private lateinit var service: UserArticleHistoryService

    private val username = "user-1"
    private val categoryId = "cat-1"
    private val categoryId2 = "cat-2"
    private val summaryId = "sum-1"
    private val rssItemId = "rss-item-1"
    private val sourceLink = "https://example.com/article"

    private val summary = BatchSummary(
        id = summaryId,
        originalTitle = "Test Article",
        summary = "Summary text",
        sourceLink = sourceLink,
        categoryId = categoryId,
        rssItemId = rssItemId,
        isSentToSlack = true,
        createdAt = Instant.now()
    )

    @BeforeEach
    fun setUp() {
        service = UserArticleHistoryService(
            requestService, batchSummaryStore, categoryStore,
            bookmarkedArticleStore, originalContentStore, articleContentExtractor, adminUserStore,
            deliveryLogStore
        )

        // 기본 stub: 사용자가 categoryId, categoryId2에 접근 가능
        every { requestService.listOwnRequests(username) } returns listOf(
            makeApprovedRequest("req-1", categoryId),
            makeApprovedRequest("req-2", categoryId2)
        )
        every { batchSummaryStore.findById(summaryId) } returns summary
        every { categoryStore.findById(categoryId) } returns Category(
            id = categoryId, name = "AI/테크"
        )
        every { categoryStore.findById(categoryId2) } returns Category(
            id = categoryId2, name = "정책/규제"
        )
        every {
            bookmarkedArticleStore.findBookmarkedSummaryIds("user-id-1", listOf(summaryId))
        } returns emptySet()
        every {
            bookmarkedArticleStore.findBookmarkedSummaryIds("user-id-1", any())
        } returns emptySet()
        every { adminUserStore.findByUsername(username) } returns AdminUser(
            id = "user-id-1",
            username = username,
            passwordHash = "hashed",
            role = AccountRole.USER
        )
    }

    @Nested
    inner class OriginalContentAutoExtraction {

        @Test
        fun `원문이 DB에 있으면 그대로 반환한다`() {
            // DB에 원문이 존재하는 상황
            every { originalContentStore.findByRssItemId(rssItemId) } returns OriginalContent(
                id = "oc-1", rssItemId = rssItemId, sourceLink = sourceLink,
                title = "Original", markdown = "# Existing content"
            )
            stubEmptyRelated()

            val detail = service.getArticleDetail(username, summaryId)

            detail.shouldNotBeNull()
            detail.originalContent shouldBe "# Existing content"
            // 추출기를 호출하지 않는다.
            verify(exactly = 0) { articleContentExtractor.extract(any()) }
        }

        @Test
        fun `원문이 DB에 없으면 소스 링크에서 실시간 추출하여 저장한다`() {
            // DB에 원문 없음
            every { originalContentStore.findByRssItemId(rssItemId) } returns null
            // 추출 성공
            every { articleContentExtractor.extract(sourceLink) } returns ExtractedArticle(
                title = "Extracted", content = "# Extracted content", language = Language.KOREAN
            )
            every { originalContentStore.save(any()) } answers {
                firstArg<OriginalContent>().copy(id = "oc-new")
            }
            stubEmptyRelated()

            val detail = service.getArticleDetail(username, summaryId)

            detail.shouldNotBeNull()
            detail.originalContent shouldBe "# Extracted content"
            verify(exactly = 1) { originalContentStore.save(any()) }
        }

        @Test
        fun `추출 실패 시 null을 반환하고 에러를 전파하지 않는다`() {
            every { originalContentStore.findByRssItemId(rssItemId) } returns null
            // 추출 실패 (예외)
            every { articleContentExtractor.extract(sourceLink) } throws RuntimeException("Network error")
            stubEmptyRelated()

            val detail = service.getArticleDetail(username, summaryId)

            detail.shouldNotBeNull()
            detail.originalContent shouldBe null
        }

        @Test
        fun `추출기가 null을 반환하면 null을 유지한다`() {
            every { originalContentStore.findByRssItemId(rssItemId) } returns null
            every { articleContentExtractor.extract(sourceLink) } returns null
            stubEmptyRelated()

            val detail = service.getArticleDetail(username, summaryId)

            detail.shouldNotBeNull()
            detail.originalContent shouldBe null
            verify(exactly = 0) { originalContentStore.save(any()) }
        }
    }

    @Nested
    inner class RelatedArticlesCrossCategoryFill {

        @Test
        fun `같은 카테고리에서 5개 채우면 다른 카테고리 조회하지 않는다`() {
            every { originalContentStore.findByRssItemId(rssItemId) } returns null
            every { articleContentExtractor.extract(any()) } returns null

            // 같은 카테고리에서 6개(자기 제외 5개) 반환
            val sameCatArticles = (1..6).map { makeSummary("rel-$it", categoryId) }
            every {
                batchSummaryStore.findSentArticles(
                    categoryIds = listOf(categoryId), limit = 6
                )
            } returns sameCatArticles

            val detail = service.getArticleDetail(username, summaryId)

            detail.shouldNotBeNull()
            detail.relatedArticles.size shouldBe 5
            // 다른 카테고리 조회를 하지 않는다.
            verify(exactly = 0) {
                batchSummaryStore.findSentArticles(
                    categoryIds = listOf(categoryId2), limit = any()
                )
            }
        }

        @Test
        fun `같은 카테고리에서 부족하면 다른 카테고리에서 보충한다`() {
            every { originalContentStore.findByRssItemId(rssItemId) } returns null
            every { articleContentExtractor.extract(any()) } returns null

            // 같은 카테고리에서 2개만 반환 (자기 제외 1개)
            val sameCatArticles = listOf(makeSummary("rel-1", categoryId))
            every {
                batchSummaryStore.findSentArticles(
                    categoryIds = listOf(categoryId), limit = 6
                )
            } returns sameCatArticles

            // 다른 카테고리에서 보충
            val otherCatArticles = (1..4).map { makeSummary("other-$it", categoryId2) }
            every {
                batchSummaryStore.findSentArticles(
                    categoryIds = listOf(categoryId2), limit = any()
                )
            } returns otherCatArticles

            val detail = service.getArticleDetail(username, summaryId)

            detail.shouldNotBeNull()
            detail.relatedArticles.size shouldBe 5
            // 다른 카테고리 기사는 올바른 카테고리명을 가진다.
            val otherCatItems = detail.relatedArticles.filter {
                it.categoryId == categoryId2
            }
            otherCatItems.size shouldBe 4
            otherCatItems.forEach {
                it.categoryName shouldBe "정책/규제"
            }
        }

        @Test
        fun `연관 기사가 전혀 없으면 빈 리스트를 반환한다`() {
            every { originalContentStore.findByRssItemId(rssItemId) } returns null
            every { articleContentExtractor.extract(any()) } returns null

            every {
                batchSummaryStore.findSentArticles(
                    categoryIds = listOf(categoryId), limit = 6
                )
            } returns emptyList()
            every {
                batchSummaryStore.findSentArticles(
                    categoryIds = listOf(categoryId2), limit = any()
                )
            } returns emptyList()

            val detail = service.getArticleDetail(username, summaryId)

            detail.shouldNotBeNull()
            detail.relatedArticles.size shouldBe 0
        }
    }

    @Nested
    inner class SearchArticlesPagination {

        @Test
        fun `승인 카테고리가 없으면 빈 페이지도 page와 size를 안전 범위로 보정한다`() {
            every { requestService.listOwnRequests(username) } returns emptyList()

            val result = service.searchArticles(
                requesterUsername = username,
                categoryId = null,
                keyword = null,
                dateFrom = null,
                dateTo = null,
                bookmarkedOnly = false,
                page = -3,
                size = 500,
            )

            result.page shouldBe 0
            result.size shouldBe 50
            result.totalCount shouldBe 0
            result.items shouldBe emptyList()
        }

        @Test
        fun `매우 큰 page 입력도 음수 offset으로 overflow되지 않는다`() {
            val offsetSlot = slot<Int>()
            every {
                batchSummaryStore.countSentArticles(
                    categoryIds = listOf(categoryId, categoryId2),
                    keyword = null,
                    dateFrom = null,
                    dateTo = null
                )
            } returns 1
            every {
                batchSummaryStore.findSentArticles(
                    categoryIds = listOf(categoryId, categoryId2),
                    keyword = null,
                    dateFrom = null,
                    dateTo = null,
                    offset = capture(offsetSlot),
                    limit = 50
                )
            } returns emptyList()

            val result = service.searchArticles(
                requesterUsername = username,
                categoryId = null,
                keyword = null,
                dateFrom = null,
                dateTo = null,
                bookmarkedOnly = false,
                page = Int.MAX_VALUE,
                size = 50,
            )

            offsetSlot.captured shouldBe Int.MAX_VALUE
            result.page shouldBe Int.MAX_VALUE
            result.size shouldBe 50
        }
    }

    @Nested
    inner class CategoryNameMapping {

        @Test
        fun `응답 DTO에 approvedCategoryName이 포함된다`() {
            // UserClippingRequestResponseMapper에 대한 테스트는 통합 테스트로 검증
            // 서비스 단에서는 카테고리명이 올바르게 매핑되는지 확인
            every { originalContentStore.findByRssItemId(rssItemId) } returns null
            every { articleContentExtractor.extract(any()) } returns null
            stubEmptyRelated()

            val detail = service.getArticleDetail(username, summaryId)

            detail.shouldNotBeNull()
            detail.categoryName shouldBe "AI/테크"
        }
    }

    @Nested
    inner class BookmarkAccessControl {

        @Test
        fun `접근 가능한 기사는 북마크를 토글한다`() {
            every { bookmarkedArticleStore.toggle("user-id-1", summary) } returns true

            val result = service.toggleBookmark(username, summaryId)

            result shouldBe true
            verify(exactly = 1) { bookmarkedArticleStore.toggle("user-id-1", summary) }
        }

        @Test
        fun `접근 권한이 없는 기사는 북마크를 거부한다`() {
            every { requestService.listOwnRequests(username) } returns emptyList()

            val error = io.kotest.assertions.throwables.shouldThrow<NotFoundException> {
                service.toggleBookmark(username, summaryId)
            }

            error.message shouldBe "기사를 찾을 수 없습니다: $summaryId"
            verify(exactly = 0) { bookmarkedArticleStore.toggle(any(), any()) }
        }

        @Test
        fun `존재하지 않는 기사는 북마크를 거부한다`() {
            every { batchSummaryStore.findById("missing-summary") } returns null
            // 원본이 purge된 후 잔존 북마크도 없는 일반 케이스
            every {
                bookmarkedArticleStore.findByUserAndSummary("user-id-1", "missing-summary")
            } returns null

            val error = io.kotest.assertions.throwables.shouldThrow<NotFoundException> {
                service.toggleBookmark(username, "missing-summary")
            }

            error.message shouldBe "기사를 찾을 수 없습니다: missing-summary"
            verify(exactly = 0) { bookmarkedArticleStore.toggle(any(), any()) }
        }

        @Test
        fun `원본이 purge된 북마크는 해제만 허용한다`() {
            val purgedSummaryId = "purged-summary"
            every { batchSummaryStore.findById(purgedSummaryId) } returns null
            val staleBookmark = com.clipping.mcpserver.model.BookmarkedArticle(
                id = "bm-1",
                userId = "user-id-1",
                summaryId = purgedSummaryId,
                originalTitle = "Purged Article",
                translatedTitle = null,
                summary = "Snapshot summary",
                insights = null,
                keywords = emptyList(),
                importanceScore = 0.5f,
                sourceLink = "https://example.com/purged",
                categoryId = categoryId,
                sentiment = null,
                eventType = null,
                articleCreatedAt = Instant.now()
            )
            every {
                bookmarkedArticleStore.findByUserAndSummary("user-id-1", purgedSummaryId)
            } returns staleBookmark
            // toggle은 기존 북마크 삭제로 귀결되어 false를 반환한다
            every { bookmarkedArticleStore.toggle("user-id-1", any()) } returns false

            val result = service.toggleBookmark(username, purgedSummaryId)

            result shouldBe false
            verify(exactly = 1) { bookmarkedArticleStore.toggle("user-id-1", any()) }
        }
    }

    @Nested
    inner class PurgedArticleDetailFallback {

        @Test
        fun `원본 요약이 삭제됐어도 북마크 스냅샷으로 상세를 구성한다`() {
            val purgedSummaryId = "purged-summary"
            every { batchSummaryStore.findById(purgedSummaryId) } returns null
            val snapshot = com.clipping.mcpserver.model.BookmarkedArticle(
                id = "bm-1",
                userId = "user-id-1",
                summaryId = purgedSummaryId,
                originalTitle = "Snapshot Title",
                translatedTitle = "스냅샷 제목",
                summary = "Snapshot body",
                insights = "Snapshot insights",
                keywords = listOf("ai", "news"),
                importanceScore = 0.82f,
                sourceLink = "https://example.com/purged",
                categoryId = categoryId,
                sentiment = "POSITIVE",
                eventType = null,
                articleCreatedAt = Instant.parse("2026-01-15T00:00:00Z")
            )
            every {
                bookmarkedArticleStore.findByUserAndSummary("user-id-1", purgedSummaryId)
            } returns snapshot
            every { articleContentExtractor.extract(snapshot.sourceLink) } returns null
            stubEmptyRelated()

            val detail = service.getArticleDetail(username, purgedSummaryId)

            detail.shouldNotBeNull()
            detail.id shouldBe purgedSummaryId
            detail.title shouldBe "스냅샷 제목"
            detail.summary shouldBe "Snapshot body"
            detail.isBookmarked shouldBe true
            detail.categoryName shouldBe "AI/테크"
            // 원본이 없으므로 batch_summaries findById는 null을 반환해야 하고,
            // 서비스는 스냅샷 경로로 분기했음을 확인
            verify(exactly = 1) {
                bookmarkedArticleStore.findByUserAndSummary("user-id-1", purgedSummaryId)
            }
        }

        @Test
        fun `원본도 없고 북마크도 없으면 null을 반환한다`() {
            val purgedSummaryId = "purged-summary"
            every { batchSummaryStore.findById(purgedSummaryId) } returns null
            every {
                bookmarkedArticleStore.findByUserAndSummary("user-id-1", purgedSummaryId)
            } returns null

            val detail = service.getArticleDetail(username, purgedSummaryId)

            detail shouldBe null
        }
    }

    private fun stubEmptyRelated() {
        every {
            batchSummaryStore.findSentArticles(
                categoryIds = any(), limit = any()
            )
        } returns emptyList()
    }

    private fun makeSummary(id: String, catId: String) = BatchSummary(
        id = id,
        originalTitle = "Article $id",
        summary = "Summary $id",
        sourceLink = "https://example.com/$id",
        categoryId = catId,
        rssItemId = "rss-$id",
        isSentToSlack = true,
        createdAt = Instant.now()
    )

    private fun makeApprovedRequest(id: String, catId: String) = UserClippingRequest(
        id = id,
        requesterUserId = "user-id-1",
        requestName = "요청 $id",
        sourceName = "소스",
        sourceUrl = "https://example.com/rss",
        slackChannelId = "C0123ABCD",
        personaName = "요약",
        personaPrompt = "prompt",
        status = UserClippingRequestStatus.APPROVED,
        approvedCategoryId = catId
    )
}
