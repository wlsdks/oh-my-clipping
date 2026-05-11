package com.ohmyclipping.repository

import com.ohmyclipping.entity.PersonaVersionEntity
import org.springframework.data.jpa.repository.JpaRepository

interface PersonaVersionRepository : JpaRepository<PersonaVersionEntity, String> {
    fun findByPersonaIdOrderByVersionDesc(personaId: String): List<PersonaVersionEntity>
    fun findByPersonaIdAndVersion(personaId: String, version: Int): PersonaVersionEntity?
}
