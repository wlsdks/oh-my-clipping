package com.ohmyclipping.repository

import com.ohmyclipping.entity.CompetitorWatchlistEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface CompetitorWatchlistRepository : JpaRepository<CompetitorWatchlistEntity, String> {
    fun findByIsActiveTrue(): List<CompetitorWatchlistEntity>
    fun findByTier(tier: String): List<CompetitorWatchlistEntity>

    /**
     * 소문자 정규화된 이름 리스트에 해당하는 엔티티를 한 번의 쿼리로 조회한다.
     * 입력 names 는 호출 전 이미 lowercase 변환이 완료된 상태여야 한다.
     */
    @Query("SELECT c FROM CompetitorWatchlistEntity c WHERE LOWER(c.name) IN :names")
    fun findByNormalizedNamesIn(@Param("names") names: List<String>): List<CompetitorWatchlistEntity>
}
