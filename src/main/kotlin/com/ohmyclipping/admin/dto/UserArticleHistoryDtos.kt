package com.ohmyclipping.admin.dto

/**
 * 북마크 토글 응답 DTO.
 */
data class BookmarkToggleResponse(
    val summaryId: String,
    val isBookmarked: Boolean
)
