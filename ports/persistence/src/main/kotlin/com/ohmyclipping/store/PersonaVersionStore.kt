package com.ohmyclipping.store

import com.ohmyclipping.model.PersonaVersionDetail
import com.ohmyclipping.model.PersonaVersionSummary

/**
 * 페르소나 버전 스냅샷 저장소.
 * 페르소나 변경 이력을 버전 단위로 관리한다.
 */
interface PersonaVersionStore {
    /** 특정 페르소나의 버전 히스토리를 최신순으로 반환한다. */
    fun listByPersonaId(personaId: String): List<PersonaVersionSummary>

    /** 특정 페르소나의 특정 버전 스냅샷을 반환한다. */
    fun findByPersonaIdAndVersion(personaId: String, version: Int): PersonaVersionDetail?

    /** 페르소나의 현재 상태를 버전 스냅샷으로 저장한다. */
    fun saveSnapshot(personaId: String, version: Int, detail: PersonaVersionDetail, changeSummary: String)
}
