package com.clipping.mcpserver.repository

import com.clipping.mcpserver.entity.PersonaEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

/**
 * 클리핑 페르소나 JPA 리포지토리.
 * clipping_personas 테이블에 대한 CRUD 및 조회 메서드를 제공한다.
 */
interface PersonaRepository : JpaRepository<PersonaEntity, String> {

    /** 활성 페르소나 목록을 조회한다. */
    fun findByIsActiveTrue(): List<PersonaEntity>

    /** 프리셋 페르소나 목록을 조회한다. */
    fun findByIsPresetTrue(): List<PersonaEntity>

    /** 커스텀(비프리셋) 페르소나 수를 반환한다. */
    fun countByIsPresetFalse(): Long

    /** tone 값 기준으로 커스텀 페르소나 수를 그룹 집계한다. */
    @Query("SELECT p.tone, COUNT(p) FROM PersonaEntity p WHERE p.isPreset = false AND p.tone IS NOT NULL GROUP BY p.tone")
    fun findToneDistribution(): List<Array<Any>>

    /** lengthPref 값 기준으로 커스텀 페르소나 수를 그룹 집계한다. */
    @Query("SELECT p.lengthPref, COUNT(p) FROM PersonaEntity p WHERE p.isPreset = false AND p.lengthPref IS NOT NULL GROUP BY p.lengthPref")
    fun findLengthDistribution(): List<Array<Any>>
}
