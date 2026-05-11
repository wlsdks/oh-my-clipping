package com.clipping.mcpserver.store

import com.clipping.mcpserver.model.BatchSummary
import java.time.LocalDate

interface SummarySearchStore {

    fun search(categoryId: String, query: String, limit: Int = 10): List<BatchSummary>

    fun searchAcrossCategories(query: String, limit: Int): List<BatchSummary>

    fun searchInDateRange(
        categoryId: String?,
        query: String,
        from: LocalDate,
        to: LocalDate,
        limit: Int,
    ): List<BatchSummary>

    fun findByImportanceScoreGreaterThan(
        categoryId: String,
        minScore: Double,
        sinceDate: LocalDate,
        limit: Int,
    ): List<BatchSummary>
}

