package com.clipping.mcpserver.repository

import com.clipping.mcpserver.entity.DailySummaryEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

interface DailySummaryRepository : JpaRepository<DailySummaryEntity, String> {
    fun findByCategoryIdAndSummaryDate(categoryId: String, summaryDate: LocalDate): DailySummaryEntity?
    fun findByCategoryId(categoryId: String): List<DailySummaryEntity>
    fun findBySummaryDate(summaryDate: LocalDate): List<DailySummaryEntity>
    fun countBySummaryDateBeforeAndCategoryId(summaryDate: LocalDate, categoryId: String): Int
    fun countBySummaryDateBefore(summaryDate: LocalDate): Int
    fun deleteBySummaryDateBeforeAndCategoryId(summaryDate: LocalDate, categoryId: String): Int
    fun deleteBySummaryDateBefore(summaryDate: LocalDate): Int
}
