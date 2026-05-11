package com.clipping.mcpserver.service

import com.clipping.mcpserver.error.NotFoundException
import com.clipping.mcpserver.service.dto.ArticleDetailView
import com.clipping.mcpserver.service.dto.ArticleHistoryItemView
import com.clipping.mcpserver.service.dto.ArticleHistoryPageView
import com.clipping.mcpserver.service.dto.UndeliveredArticleView
import com.clipping.mcpserver.service.dto.UndeliveredDigestView
import com.clipping.mcpserver.content.ArticleContentExtractor
import com.clipping.mcpserver.model.BatchSummary
import com.clipping.mcpserver.model.BookmarkedArticle
import com.clipping.mcpserver.model.OriginalContent
import com.clipping.mcpserver.store.AdminUserStore
import com.clipping.mcpserver.store.BatchSummaryStore
import com.clipping.mcpserver.store.BookmarkedArticleStore
import com.clipping.mcpserver.store.CategoryStore
import com.clipping.mcpserver.store.DeliveryLogStore
import com.clipping.mcpserver.store.OriginalContentStore
import com.clipping.mcpserver.store.SentArticleStore
import com.clipping.mcpserver.support.PaginationUtils
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlin.math.ceil

/**
 * 사용자 기사 히스토리 조회 및 북마크 비즈니스 로직을 담당한다.
 */
@Service
class UserArticleHistoryService(
    private val userClippingRequestService: UserClippingRequestService,
    private val batchSummaryStore: BatchSummaryStore,
    private val categoryStore: CategoryStore,
    private val bookmarkedArticleStore: BookmarkedArticleStore,
    private val originalContentStore: OriginalContentStore,
    private val articleContentExtractor: ArticleContentExtractor,
    private val adminUserStore: AdminUserStore,
    private val deliveryLogStore: DeliveryLogStore,
    private val sentArticleStore: SentArticleStore = batchSummaryStore
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 로그인 사용자가 접근 가능한 발행 기사를 조건 검색 + 페이지네이션으로 조회한다.
     *
     * @param requesterUsername 로그인 사용자 이름
     * @param categoryId 특정 카테고리 필터 (null이면 전체)
     * @param keyword 검색 키워드
     * @param dateFrom 시작 날짜
     * @param dateTo 종료 날짜
     * @param bookmarkedOnly 북마크만 보기
     * @param page 페이지 번호 (0-based)
     * @param size 페이지 크기
     */
    fun searchArticles(
        requesterUsername: String,
        categoryId: String?,
        keyword: String?,
        dateFrom: LocalDate?,
        dateTo: LocalDate?,
        bookmarkedOnly: Boolean,
        page: Int,
        size: Int
    ): ArticleHistoryPageView {
        val safePage = page.coerceAtLeast(0)
        val safeSize = size.coerceIn(1, 50)
        // 사용자가 접근 가능한 카테고리 ID를 먼저 추출한다.
        val approvedCategoryIds = resolveApprovedCategoryIds(requesterUsername)
        if (approvedCategoryIds.isEmpty()) {
            return emptyPage(safePage, safeSize)
        }
        val requesterUserId = requireUserId(requesterUsername)

        // 카테고리 필터가 지정된 경우 접근 가능 범위 내에서만 필터한다.
        val targetCategoryIds = if (categoryId != null) {
            if (categoryId in approvedCategoryIds) listOf(categoryId) else return emptyPage(safePage, safeSize)
        } else {
            approvedCategoryIds
        }

        // 날짜를 Instant로 변환한다.
        val kst = ZoneId.of("Asia/Seoul")
        val fromInstant = dateFrom?.atStartOfDay(kst)?.toInstant()
        val toInstant = dateTo?.plusDays(1)?.atStartOfDay(kst)?.toInstant()

        val offset = PaginationUtils.safeOffset(safePage, safeSize)

        // 북마크 탭은 batch_summaries 대신 스냅샷 테이블을 조회한다.
        // 원본 요약이 retention으로 삭제돼도 북마크는 유지돼야 하기 때문이다.
        return if (bookmarkedOnly) {
            searchBookmarkedArticles(
                requesterUserId, targetCategoryIds, keyword,
                fromInstant, toInstant, safePage, safeSize, offset
            )
        } else {
            searchSentArticles(
                requesterUserId, targetCategoryIds, keyword,
                fromInstant, toInstant, safePage, safeSize, offset
            )
        }
    }

    /** 발송된 기사 목록을 batch_summaries에서 조회하고 isBookmarked 플래그를 붙인다. */
    private fun searchSentArticles(
        requesterUserId: String,
        targetCategoryIds: List<String>,
        keyword: String?,
        fromInstant: Instant?,
        toInstant: Instant?,
        safePage: Int,
        safeSize: Int,
        offset: Int
    ): ArticleHistoryPageView {
        val totalCount = sentArticleStore.countSentArticles(
            categoryIds = targetCategoryIds,
            keyword = keyword,
            dateFrom = fromInstant,
            dateTo = toInstant
        )
        if (totalCount == 0) return emptyPage(safePage, safeSize)

        val articles = sentArticleStore.findSentArticles(
            categoryIds = targetCategoryIds,
            keyword = keyword,
            dateFrom = fromInstant,
            dateTo = toInstant,
            offset = offset,
            limit = safeSize
        )

        // 조회된 기사의 북마크 상태를 일괄 확인한다 (스냅샷 테이블 기준).
        val articleIds = articles.map { it.id }
        val bookmarkedSet = bookmarkedArticleStore
            .findBookmarkedSummaryIds(requesterUserId, articleIds)

        val categoryNameCache = mutableMapOf<String, String>()
        val items = articles.map { summary ->
            val catName = categoryNameCache.getOrPut(summary.categoryId) {
                categoryStore.findById(summary.categoryId)?.name ?: "삭제된 카테고리"
            }
            ArticleHistoryItemView(
                id = summary.id,
                title = summary.translatedTitle ?: summary.originalTitle,
                summary = summary.summary,
                keywords = summary.keywords,
                importanceScore = summary.importanceScore,
                sourceLink = summary.sourceLink,
                categoryId = summary.categoryId,
                categoryName = catName,
                isBookmarked = summary.id in bookmarkedSet,
                createdAt = summary.createdAt.toString()
            )
        }
        return ArticleHistoryPageView(
            items = items,
            page = safePage,
            size = safeSize,
            totalCount = totalCount,
            totalPages = ceil(totalCount.toDouble() / safeSize).toInt()
        )
    }

    /** 북마크 스냅샷 테이블에서 직접 조회한다. 원본 요약이 사라져도 동작한다. */
    private fun searchBookmarkedArticles(
        requesterUserId: String,
        targetCategoryIds: List<String>,
        keyword: String?,
        fromInstant: Instant?,
        toInstant: Instant?,
        safePage: Int,
        safeSize: Int,
        offset: Int
    ): ArticleHistoryPageView {
        val totalCount = bookmarkedArticleStore.countBookmarks(
            userId = requesterUserId,
            categoryIds = targetCategoryIds,
            keyword = keyword,
            dateFrom = fromInstant,
            dateTo = toInstant
        )
        if (totalCount == 0) return emptyPage(safePage, safeSize)

        val bookmarks = bookmarkedArticleStore.searchBookmarks(
            userId = requesterUserId,
            categoryIds = targetCategoryIds,
            keyword = keyword,
            dateFrom = fromInstant,
            dateTo = toInstant,
            offset = offset,
            limit = safeSize
        )

        val categoryNameCache = mutableMapOf<String, String>()
        val items = bookmarks.map { bookmark ->
            val catName = categoryNameCache.getOrPut(bookmark.categoryId) {
                categoryStore.findById(bookmark.categoryId)?.name ?: "삭제된 카테고리"
            }
            ArticleHistoryItemView(
                id = bookmark.summaryId,
                title = bookmark.translatedTitle ?: bookmark.originalTitle,
                summary = bookmark.summary,
                keywords = bookmark.keywords,
                importanceScore = bookmark.importanceScore,
                sourceLink = bookmark.sourceLink,
                categoryId = bookmark.categoryId,
                categoryName = catName,
                isBookmarked = true,
                createdAt = bookmark.articleCreatedAt.toString()
            )
        }
        return ArticleHistoryPageView(
            items = items,
            page = safePage,
            size = safeSize,
            totalCount = totalCount,
            totalPages = ceil(totalCount.toDouble() / safeSize).toInt()
        )
    }

    /**
     * 기사 상세 정보를 조회한다. 원문 마크다운과 같은 카테고리의 연관 기사를 포함한다.
     *
     * @param requesterUsername 로그인 사용자 이름
     * @param summaryId 기사(BatchSummary) ID
     * @return 상세 뷰 (접근 불가하면 null)
     */
    fun getArticleDetail(
        requesterUsername: String,
        summaryId: String
    ): ArticleDetailView? {
        val requesterUserId = requireUserId(requesterUsername)
        val approvedCategoryIds = resolveApprovedCategoryIds(requesterUsername)

        // 원본 요약이 있으면 그 내용을, 없으면 북마크 스냅샷을 사용한다.
        val summary = batchSummaryStore.findById(summaryId)
        if (summary == null) {
            // 원본이 purge된 경우: 북마크 스냅샷이 있으면 그걸로 복원한다.
            val snapshot = bookmarkedArticleStore
                .findByUserAndSummary(requesterUserId, summaryId)
                ?: return null
            if (snapshot.categoryId !in approvedCategoryIds) return null
            return buildDetailViewFromBookmark(snapshot, approvedCategoryIds, requesterUserId)
        }

        // 사용자가 해당 카테고리에 접근 가능한지 확인한다.
        if (summary.categoryId !in approvedCategoryIds) return null

        val catName = categoryStore.findById(summary.categoryId)?.name ?: "삭제된 카테고리"
        val isBookmarked = bookmarkedArticleStore
            .findBookmarkedSummaryIds(requesterUserId, listOf(summaryId))
            .contains(summaryId)

        // 원문 마크다운을 rssItemId로 조회하고, 없으면 실시간 추출을 시도한다.
        val originalMarkdown = fetchOrExtractOriginalContent(
            summary.rssItemId, summary.sourceLink
        )

        // 같은 카테고리의 최근 발행 기사를 연관 뉴스로 조회한다 (자기 자신 제외, 최대 5개).
        val relatedSummaries = buildRelatedArticles(
            summaryId, summary.categoryId, approvedCategoryIds
        )

        // 카테고리명 캐시로 연관 기사에 올바른 카테고리명 매핑
        val categoryNameCache = mutableMapOf(summary.categoryId to catName)

        val relatedBookmarkedIds = if (relatedSummaries.isNotEmpty()) {
            bookmarkedArticleStore.findBookmarkedSummaryIds(
                requesterUserId,
                relatedSummaries.map { it.id }
            )
        } else {
            emptySet()
        }

        val relatedItems = relatedSummaries.map { rel ->
            val relCatName = categoryNameCache.getOrPut(rel.categoryId) {
                categoryStore.findById(rel.categoryId)?.name ?: "삭제된 카테고리"
            }
            ArticleHistoryItemView(
                id = rel.id,
                title = rel.translatedTitle ?: rel.originalTitle,
                summary = rel.summary,
                keywords = rel.keywords,
                importanceScore = rel.importanceScore,
                sourceLink = rel.sourceLink,
                categoryId = rel.categoryId,
                categoryName = relCatName,
                isBookmarked = rel.id in relatedBookmarkedIds,
                createdAt = rel.createdAt.toString()
            )
        }

        return ArticleDetailView(
            id = summary.id,
            title = summary.translatedTitle ?: summary.originalTitle,
            summary = summary.summary,
            insights = summary.insights,
            originalContent = originalMarkdown,
            keywords = summary.keywords,
            importanceScore = summary.importanceScore,
            sourceLink = summary.sourceLink,
            categoryId = summary.categoryId,
            categoryName = catName,
            isBookmarked = isBookmarked,
            createdAt = summary.createdAt.toString(),
            relatedArticles = relatedItems
        )
    }

    /**
     * MCP 경로용 북마크 목록 조회. 최신순 페이지네이션.
     *
     * @return BookmarkedArticle 리스트. 원본이 purge 되어도 스냅샷 기준으로 반환된다.
     */
    fun listBookmarksByUserId(userId: String, offset: Int, limit: Int): List<BookmarkedArticle> {
        val user = adminUserStore.findById(userId)
            ?: throw NotFoundException("사용자를 찾을 수 없습니다: $userId")
        // 접근 가능 카테고리 ID 범위 내에서만 북마크를 열람한다.
        val approvedCategoryIds = resolveApprovedCategoryIds(user.username)
        if (approvedCategoryIds.isEmpty()) return emptyList()
        val safeOffset = offset.coerceAtLeast(0)
        val safeLimit = limit.coerceIn(1, 100)
        return bookmarkedArticleStore.searchBookmarks(
            userId = userId,
            categoryIds = approvedCategoryIds,
            keyword = null,
            dateFrom = null,
            dateTo = null,
            offset = safeOffset,
            limit = safeLimit,
        )
    }

    /**
     * MCP 경로용 북마크 토글.
     *
     * 기존 [toggleBookmark] 는 인증 세션의 username 으로 userId 를 해석하지만,
     * MCP 호출은 orchestrator 가 내부 userId 를 직접 주입하므로 별도 어댑터를 둔다.
     * 카테고리 접근 권한은 해당 userId 의 승인 요청 목록에서 추출한다.
     *
     * @return 토글 후 북마크 상태 (true=북마크됨, false=해제됨)
     * @throws NotFoundException 사용자를 찾을 수 없거나 접근 불가 카테고리
     */
    fun toggleBookmarkByUserId(userId: String, summaryId: String): Boolean {
        // userId 역참조로 username 을 구해 기존 접근 권한 로직을 그대로 재사용한다.
        val user = adminUserStore.findById(userId)
            ?: throw NotFoundException("사용자를 찾을 수 없습니다: $userId")
        return toggleBookmark(user.username, summaryId)
    }

    /**
     * 기사 북마크를 토글한다. 생성 시 현재 요약 내용을 스냅샷으로 복사한다.
     * 원본 요약이 이미 retention으로 삭제됐더라도 기존 북마크 해제는 허용한다.
     * @return 토글 후 북마크 상태
     */
    fun toggleBookmark(requesterUsername: String, summaryId: String): Boolean {
        val userId = requireUserId(requesterUsername)
        val summary = batchSummaryStore.findById(summaryId)

        if (summary == null) {
            // 원본이 사라진 경우: 북마크 스냅샷이 있으면 해제만 허용한다 (재생성은 불가).
            val existing = bookmarkedArticleStore.findByUserAndSummary(userId, summaryId)
                ?: throw NotFoundException("기사를 찾을 수 없습니다: $summaryId")
            if (existing.categoryId !in resolveApprovedCategoryIds(requesterUsername)) {
                throw NotFoundException("기사를 찾을 수 없습니다: $summaryId")
            }
            // 이미 북마크된 상태이므로 toggle은 항상 삭제로 귀결된다.
            return bookmarkedArticleStore.toggle(userId, placeholderFor(existing))
        }

        // 원본이 살아있으면 평소처럼 승인 카테고리 체크 후 스냅샷 토글.
        val approvedCategoryIds = resolveApprovedCategoryIds(requesterUsername)
        if (summary.categoryId !in approvedCategoryIds) {
            throw NotFoundException("기사를 찾을 수 없습니다: $summaryId")
        }
        return bookmarkedArticleStore.toggle(userId, summary)
    }

    /**
     * BookmarkedArticle 스냅샷을 BatchSummary 형태로 복원한다.
     * toggleBookmark가 원본 삭제 후 "해제"만 수행할 때 사용한다 (저장 시에는 참조되지 않음).
     */
    private fun placeholderFor(snapshot: BookmarkedArticle): BatchSummary = BatchSummary(
        id = snapshot.summaryId,
        originalTitle = snapshot.originalTitle,
        translatedTitle = snapshot.translatedTitle,
        summary = snapshot.summary,
        insights = snapshot.insights,
        keywords = snapshot.keywords,
        importanceScore = snapshot.importanceScore,
        sourceLink = snapshot.sourceLink,
        isSentToSlack = true,
        categoryId = snapshot.categoryId,
        rssItemId = "",
        sentiment = snapshot.sentiment,
        eventType = snapshot.eventType,
        createdAt = snapshot.articleCreatedAt
    )

    /**
     * 원본 요약이 삭제됐을 때 북마크 스냅샷으로 상세 뷰를 구성한다.
     * 원문 마크다운은 소스 링크에서 실시간 추출만 시도하고, 실패하면 null로 둔다.
     * 연관 기사는 같은 카테고리의 현재 살아있는 요약에서 뽑는다.
     */
    private fun buildDetailViewFromBookmark(
        snapshot: BookmarkedArticle,
        approvedCategoryIds: List<String>,
        requesterUserId: String
    ): ArticleDetailView {
        val catName = categoryStore.findById(snapshot.categoryId)?.name ?: "삭제된 카테고리"

        // 원본 RSS item ID를 모르기 때문에 캐시를 못 쓰고 소스 링크 실시간 추출만 시도한다.
        val originalMarkdown = try {
            if (snapshot.sourceLink.isBlank()) null
            else articleContentExtractor.extract(snapshot.sourceLink)?.content
        } catch (e: Exception) {
            log.warn("북마크 스냅샷 원문 실시간 추출 실패: sourceLink={}", snapshot.sourceLink, e)
            null
        }

        val relatedSummaries = buildRelatedArticles(
            snapshot.summaryId, snapshot.categoryId, approvedCategoryIds
        )
        val categoryNameCache = mutableMapOf(snapshot.categoryId to catName)
        val relatedBookmarkedIds = if (relatedSummaries.isNotEmpty()) {
            bookmarkedArticleStore.findBookmarkedSummaryIds(
                requesterUserId,
                relatedSummaries.map { it.id }
            )
        } else {
            emptySet()
        }
        val relatedItems = relatedSummaries.map { rel ->
            val relCatName = categoryNameCache.getOrPut(rel.categoryId) {
                categoryStore.findById(rel.categoryId)?.name ?: "삭제된 카테고리"
            }
            ArticleHistoryItemView(
                id = rel.id,
                title = rel.translatedTitle ?: rel.originalTitle,
                summary = rel.summary,
                keywords = rel.keywords,
                importanceScore = rel.importanceScore,
                sourceLink = rel.sourceLink,
                categoryId = rel.categoryId,
                categoryName = relCatName,
                isBookmarked = rel.id in relatedBookmarkedIds,
                createdAt = rel.createdAt.toString()
            )
        }

        return ArticleDetailView(
            id = snapshot.summaryId,
            title = snapshot.translatedTitle ?: snapshot.originalTitle,
            summary = snapshot.summary,
            insights = snapshot.insights,
            originalContent = originalMarkdown,
            keywords = snapshot.keywords,
            importanceScore = snapshot.importanceScore,
            sourceLink = snapshot.sourceLink,
            categoryId = snapshot.categoryId,
            categoryName = catName,
            isBookmarked = true,
            createdAt = snapshot.articleCreatedAt.toString(),
            relatedArticles = relatedItems
        )
    }

    /**
     * 로그인 사용자의 미전달 다이제스트 목록을 조회한다.
     * ABANDONED, STALE, 15분 이상 경과한 FAILED 상태의 건을 반환한다.
     *
     * @param userId 로그인 사용자 이름 (username)
     * @return 미전달 다이제스트 목록 (카테고리명 + 기사 목록 포함)
     */
    fun getUndeliveredDigests(userId: String): List<UndeliveredDigestView> {
        // 승인된 카테고리 ID 목록을 먼저 추출한다.
        val approvedCategoryIds = resolveApprovedCategoryIds(userId)
        if (approvedCategoryIds.isEmpty()) return emptyList()

        // 미전달 건을 조회한다.
        val undelivered = deliveryLogStore.findUndeliveredForUser(approvedCategoryIds)
        if (undelivered.isEmpty()) return emptyList()

        // 카테고리명 캐시를 구성해 N+1 조회를 방지한다.
        val categoryNameCache = mutableMapOf<String, String>()

        return undelivered.map { entry ->
            val catName = categoryNameCache.getOrPut(entry.categoryId) {
                categoryStore.findById(entry.categoryId)?.name ?: "삭제된 카테고리"
            }
            // 발송 시각을 사용자 친화적 레이블로 변환한다 (예: "오전 9시").
            val timeLabel = formatDeliveryHour(entry.deliveryHour)

            val articles = entry.preparedDigest?.items?.map { item ->
                UndeliveredArticleView(
                    summaryId = item.summaryId,
                    title = item.title,
                    summary = item.summary,
                    sourceLink = item.sourceLink
                )
            } ?: emptyList()

            UndeliveredDigestView(
                deliveryLogId = entry.deliveryLogId,
                categoryName = catName,
                deliveryDate = entry.deliveryDate.toString(),
                deliveryTimeLabel = timeLabel,
                status = entry.status,
                retryCount = entry.retryCount,
                maxRetries = DeliveryRetryOrchestrator.MAX_RETRIES,
                articleCount = articles.size,
                articles = articles
            )
        }
    }

    /** 발송 시각(0-23)을 한국어 친화적 레이블로 변환한다. */
    private fun formatDeliveryHour(hour: Int): String {
        val ampm = if (hour < 12) "오전" else "오후"
        val displayHour = if (hour % 12 == 0) 12 else hour % 12
        return "${ampm} ${displayHour}시"
    }

    /**
     * 원문 마크다운을 조회하고, DB에 없으면 소스 링크에서 실시간 추출하여 저장한다.
     * 추출 실패 시 null을 반환한다.
     *
     * rssItemId가 null인 경우(V139 retention 후 기사 삭제됨)는 DB 조회를 건너뛰고
     * 소스 링크에서 실시간 추출만 시도한다.
     */
    private fun fetchOrExtractOriginalContent(
        rssItemId: String?,
        sourceLink: String
    ): String? {
        // rssItemId가 있는 경우 DB에서 기존 원문을 먼저 조회한다.
        if (rssItemId != null) {
            val existing = originalContentStore.findByRssItemId(rssItemId)
            if (existing != null) return existing.markdown
        }

        // 소스 링크가 없으면 추출 불가
        if (sourceLink.isBlank()) return null

        return try {
            // 소스 링크에서 원문을 실시간 추출한다.
            val extracted = articleContentExtractor.extract(sourceLink)
                ?: return null
            // rssItemId가 있을 때만 DB에 저장한다 (null이면 FK 연결 불가로 저장 생략).
            if (rssItemId != null) {
                originalContentStore.save(
                    OriginalContent(
                        id = UUID.randomUUID().toString(),
                        rssItemId = rssItemId,
                        sourceLink = sourceLink,
                        title = extracted.title,
                        markdown = extracted.content
                    )
                )
            }
            extracted.content
        } catch (e: Exception) {
            log.warn("원문 실시간 추출 실패: sourceLink={}", sourceLink, e)
            null
        }
    }

    /**
     * 연관 기사를 최대 5개 구성한다.
     * 같은 카테고리에서 먼저 채우고, 부족하면 다른 카테고리에서 보충한다.
     */
    private fun buildRelatedArticles(
        currentSummaryId: String,
        currentCategoryId: String,
        approvedCategoryIds: List<String>
    ): List<BatchSummary> {
        // 같은 카테고리에서 최대 6개 조회 (자기 자신 제외 후 최대 5개)
        val sameCat = sentArticleStore.findSentArticles(
            categoryIds = listOf(currentCategoryId),
            limit = 6
        ).filter { it.id != currentSummaryId }.take(5)

        if (sameCat.size >= 5) return sameCat

        // 부족분을 다른 카테고리에서 채운다.
        val otherCategoryIds = approvedCategoryIds
            .filter { it != currentCategoryId }
        if (otherCategoryIds.isEmpty()) return sameCat

        val needed = 5 - sameCat.size
        val existingIds = sameCat.map { it.id }.toSet() + currentSummaryId
        val otherCat = sentArticleStore.findSentArticles(
            categoryIds = otherCategoryIds,
            limit = needed + 1
        ).filter { it.id !in existingIds }.take(needed)

        return sameCat + otherCat
    }

    /**
     * 사용자가 접근 가능한 기사인지 검증하고 해당 요약을 반환한다.
     * 기사 미존재 또는 미승인 카테고리면 동일하게 404로 처리한다.
     */
    private fun requireAccessibleSummary(
        requesterUsername: String,
        summaryId: String
    ): BatchSummary {
        // 존재하지 않는 기사 ID는 즉시 404로 처리한다.
        val summary = batchSummaryStore.findById(summaryId)
            ?: throw NotFoundException("기사를 찾을 수 없습니다: $summaryId")
        // 승인된 카테고리 밖 기사면 존재 여부를 숨기기 위해 동일하게 404로 처리한다.
        val approvedCategoryIds = resolveApprovedCategoryIds(requesterUsername)
        if (summary.categoryId !in approvedCategoryIds) {
            throw NotFoundException("기사를 찾을 수 없습니다: $summaryId")
        }
        return summary
    }

    private fun resolveApprovedCategoryIds(requesterUsername: String): List<String> =
        // 승인 완료된 요청에서만 카테고리 ID를 추출해 중복을 제거한다.
        userClippingRequestService.listOwnRequests(requesterUsername)
            .asSequence()
            .filter { it.isApproved() }
            .mapNotNull { it.approvedCategoryId?.takeIf(String::isNotBlank) }
            .distinct()
            .toList()

    /** 사용자명을 내부 userId로 해석한다. */
    private fun requireUserId(requesterUsername: String): String =
        adminUserStore.findByUsername(requesterUsername)?.id
            ?: throw NotFoundException("사용자를 찾을 수 없습니다: $requesterUsername")

    private fun emptyPage(page: Int, size: Int) = ArticleHistoryPageView(
        items = emptyList(),
        page = page,
        size = size,
        totalCount = 0,
        totalPages = 0
    )

}
