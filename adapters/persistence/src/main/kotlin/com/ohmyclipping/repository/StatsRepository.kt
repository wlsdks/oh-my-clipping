package com.ohmyclipping.repository

import com.ohmyclipping.entity.StatsEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

interface StatsRepository : JpaRepository<StatsEntity, String> {
    fun findByCategoryIdAndStatDate(categoryId: String, statDate: LocalDate): StatsEntity?
    fun findByStatDateBetween(from: LocalDate, to: LocalDate): List<StatsEntity>
    fun findByCategoryIdAndStatDateBetween(categoryId: String, from: LocalDate, to: LocalDate): List<StatsEntity>
    fun countByStatDateBeforeAndCategoryId(statDate: LocalDate, categoryId: String): Int
    fun countByStatDateBefore(statDate: LocalDate): Int
    fun deleteByStatDateBeforeAndCategoryId(statDate: LocalDate, categoryId: String): Int
    fun deleteByStatDateBefore(statDate: LocalDate): Int
}
