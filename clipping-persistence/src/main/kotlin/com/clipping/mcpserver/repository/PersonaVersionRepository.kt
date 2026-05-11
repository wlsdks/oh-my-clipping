package com.clipping.mcpserver.repository

import com.clipping.mcpserver.entity.PersonaVersionEntity
import org.springframework.data.jpa.repository.JpaRepository

interface PersonaVersionRepository : JpaRepository<PersonaVersionEntity, String> {
    fun findByPersonaIdOrderByVersionDesc(personaId: String): List<PersonaVersionEntity>
    fun findByPersonaIdAndVersion(personaId: String, version: Int): PersonaVersionEntity?
}
