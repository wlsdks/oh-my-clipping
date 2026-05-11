package com.ohmyclipping.user

import com.ohmyclipping.admin.dto.BookmarkToggleResponse
import com.ohmyclipping.service.dto.user.ArticleDetailView
import com.ohmyclipping.service.dto.user.ArticleHistoryPageView
import com.ohmyclipping.service.dto.user.UndeliveredDigestView
import com.ohmyclipping.service.UserArticleHistoryService
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate

/**
 * 사용자 기사 히스토리 및 북마크 API.
 */
@RestController
@RequestMapping("/api/user/history")
class UserArticleHistoryController(
    private val userArticleHistoryService: UserArticleHistoryService
) {

    /**
     * 발행 기사 목록을 조건 검색 + 페이지네이션으로 조회한다.
     */
    @GetMapping("/articles")
    fun searchArticles(
        authentication: Authentication,
        @RequestParam(required = false) categoryId: String?,
        @RequestParam(required = false) keyword: String?,
        @RequestParam(required = false) dateFrom: String?,
        @RequestParam(required = false) dateTo: String?,
        @RequestParam(defaultValue = "false") bookmarkedOnly: Boolean,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "30") size: Int
    ): ArticleHistoryPageView {
        val safeSize = size.coerceIn(1, 200)
        // 날짜 문자열을 LocalDate로 변환하여 서비스에 위임한다.
        return userArticleHistoryService.searchArticles(
            requesterUsername = authentication.name,
            categoryId = categoryId,
            keyword = keyword,
            dateFrom = dateFrom?.let { LocalDate.parse(it) },
            dateTo = dateTo?.let { LocalDate.parse(it) },
            bookmarkedOnly = bookmarkedOnly,
            page = page,
            size = safeSize
        )
    }

    /**
     * 기사 상세 정보를 조회한다. 원문 마크다운과 연관 기사를 포함한다.
     */
    @GetMapping("/articles/{summaryId}")
    fun getArticleDetail(
        authentication: Authentication,
        @PathVariable summaryId: String
    ): ArticleDetailView =
        // 기사 상세를 조회하고, 접근 불가 시 404를 반환한다.
        userArticleHistoryService.getArticleDetail(
            requesterUsername = authentication.name,
            summaryId = summaryId
        ) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "기사를 찾을 수 없습니다.")

    /**
     * 로그인 사용자의 미전달 다이제스트 목록을 반환한다.
     * ABANDONED, STALE, 15분 이상 경과한 FAILED 상태의 건을 포함한다.
     */
    @GetMapping("/undelivered-digests")
    fun getUndeliveredDigests(authentication: Authentication): List<UndeliveredDigestView> =
        userArticleHistoryService.getUndeliveredDigests(authentication.name)

    /**
     * 기사 북마크를 토글한다.
     */
    @PostMapping("/articles/{summaryId}/bookmark")
    fun toggleBookmark(
        authentication: Authentication,
        @PathVariable summaryId: String
    ): BookmarkToggleResponse {
        // 토글 결과를 응답 DTO로 감싸 반환한다.
        val isBookmarked = userArticleHistoryService.toggleBookmark(
            requesterUsername = authentication.name,
            summaryId = summaryId
        )
        return BookmarkToggleResponse(
            summaryId = summaryId,
            isBookmarked = isBookmarked
        )
    }
}
