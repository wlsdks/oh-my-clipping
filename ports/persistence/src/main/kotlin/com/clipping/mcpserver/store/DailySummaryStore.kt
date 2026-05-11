package com.clipping.mcpserver.store

import com.clipping.mcpserver.model.DailySummary
import java.time.LocalDate

interface DailySummaryStore {
    fun findByCategoryAndDate(categoryId: String, date: LocalDate): DailySummary?
    fun findByCategoryId(categoryId: String): List<DailySummary>

    /** 특정 날짜의 모든 카테고리 일간 요약을 조회한다. */
    fun findByDate(date: LocalDate): List<DailySummary>

    fun countOlderThan(cutoffDate: LocalDate, categoryId: String? = null): Int
    fun deleteOlderThan(cutoffDate: LocalDate, categoryId: String? = null): Int
    fun save(summary: DailySummary): DailySummary
}
