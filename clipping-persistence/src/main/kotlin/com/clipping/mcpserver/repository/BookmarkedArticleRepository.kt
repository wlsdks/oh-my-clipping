package com.clipping.mcpserver.repository

import com.clipping.mcpserver.entity.BookmarkedArticleEntity
import org.springframework.data.jpa.repository.JpaRepository

interface BookmarkedArticleRepository : JpaRepository<BookmarkedArticleEntity, String> {
    fun findByUserIdAndSummaryId(userId: String, summaryId: String): BookmarkedArticleEntity?
    fun findByUserIdAndSummaryIdIn(
        userId: String,
        summaryIds: List<String>
    ): List<BookmarkedArticleEntity>
    fun findByUserIdOrderByBookmarkedAtDesc(userId: String): List<BookmarkedArticleEntity>
    fun deleteByUserIdAndSummaryId(userId: String, summaryId: String)
}
