package com.ohmyclipping.store

import com.ohmyclipping.model.Persona
import java.time.Instant

interface PersonaStore {
    fun list(): List<Persona>
    fun listActive(): List<Persona>
    fun listPresets(): List<Persona>
    fun findById(id: String): Persona?
    fun save(persona: Persona): Persona
    fun update(persona: Persona): Persona

    /**
     * 기대 updated_at과 일치할 때만 저장한다. 편집 충돌(낙관적 잠금 실패) 시 null 반환.
     *
     * 서비스 레이어는 반환값이 null이면 `ConflictException(staleEditInfo)` 로 변환해야 한다.
     */
    fun updateWithExpectedUpdatedAt(persona: Persona, expectedUpdatedAt: Instant): Persona?

    fun delete(id: String)

    /** 커스텀 페르소나 총 수를 반환한다. */
    fun countCustomPersonas(): Long

    /** 프리셋별 활성 구독 수를 반환한다. 활성 구독 수 내림차순으로 정렬된다. */
    fun findPresetUsage(): List<PresetUsageRow>

    /** 커스텀 페르소나를 사용하는 활성 구독 수를 반환한다. */
    fun countActiveCustomSubscriptions(): Long

    /** 최근 생성된 커스텀 페르소나를 최대 limit 건 반환한다. */
    fun findRecentCustomPersonas(limit: Int = 20): List<RecentCustomPersonaRow>

    /** 특정 페르소나를 사용하는 활성 구독 수를 반환한다. */
    fun countActiveSubscriptions(personaId: String): Long

    /** 전체 활성 구독(페르소나 연결) 수를 반환한다. */
    fun countTotalActiveSubscriptions(): Long

    /** 프리셋 페르소나를 사용하는 구독이 있는 고유 유저 수를 반환한다. */
    fun countPresetSubscriptionUsers(): Long

    /** 구독이 하나 이상 있는 고유 유저 수를 반환한다. */
    fun countTotalSubscriptionUsers(): Long

    /** 커스텀 페르소나의 tone 분포를 반환한다. */
    fun findToneDistribution(): Map<String, Long>

    /** 커스텀 페르소나의 length_pref 분포를 반환한다. */
    fun findLengthDistribution(): Map<String, Long>
}

/**
 * 프리셋별 활성 구독 수 집계 결과 (Store 레이어).
 */
data class PresetUsageRow(
    val presetId: String,
    val presetName: String,
    val activeSubscriptions: Long
)

/**
 * 최근 커스텀 페르소나 조회 결과 (Store 레이어).
 */
data class RecentCustomPersonaRow(
    val id: String,
    val userName: String,
    val personaName: String,
    val systemPrompt: String,
    val tone: String?,
    val lengthPref: String?,
    val createdAt: java.time.Instant
)
