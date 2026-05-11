package com.ohmyclipping.store

import com.ohmyclipping.model.ClippingStat
import java.time.LocalDate
import java.time.YearMonth

interface StatsStore {
    fun findByCategoryAndDate(categoryId: String, date: LocalDate): ClippingStat?
    fun findMonthly(categoryId: String?, yearMonth: YearMonth): List<ClippingStat>
    fun findDailyRange(categoryId: String?, from: LocalDate, to: LocalDate): List<ClippingStat>
    fun countOlderThan(cutoffDate: LocalDate, categoryId: String? = null): Int
    fun deleteOlderThan(cutoffDate: LocalDate, categoryId: String? = null): Int
    fun upsert(stat: ClippingStat): ClippingStat
}
