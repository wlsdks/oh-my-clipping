package com.ohmyclipping.admin

import com.ohmyclipping.admin.dto.CreatePersonaRequest
import com.ohmyclipping.admin.dto.PersonaResponse
import com.ohmyclipping.admin.dto.UpdatePersonaRequest
import com.ohmyclipping.service.UserOwnedPersonaService
import com.ohmyclipping.service.dto.user.UserOwnedPersonaView
import org.springframework.security.core.Authentication
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

/**
 * 사용자(USER) 역할의 setup 전용 페르소나 CRUD를 제공한다.
 */
@RestController
@RequestMapping("/api/user/setup/personas")
class UserPersonaController(private val userOwnedPersonaService: UserOwnedPersonaService) {

    /** 페르소나 목록을 조회한다. */
    @GetMapping
    fun list(authentication: Authentication): List<PersonaResponse> =
        userOwnedPersonaService.listOwnPersonas(authentication.name).map { it.toResponse() }

    /** 페르소나 단건을 조회한다. */
    @GetMapping("/{id}")
    fun get(authentication: Authentication, @PathVariable id: String): PersonaResponse =
        userOwnedPersonaService.getOwnPersona(authentication.name, id).toResponse()

    /** 페르소나를 생성한다. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(authentication: Authentication, @RequestBody request: CreatePersonaRequest): PersonaResponse =
        userOwnedPersonaService.createOwnPersona(
            requesterUsername = authentication.name,
            name = request.name,
            description = request.description,
            systemPrompt = request.systemPrompt,
            summaryStyle = request.summaryStyle,
            targetAudience = request.targetAudience,
            maxItems = request.maxItems,
            language = request.language
        ).toResponse()

    /** 페르소나 정보를 수정한다. */
    @PutMapping("/{id}")
    fun update(
        authentication: Authentication,
        @PathVariable id: String,
        @RequestBody request: UpdatePersonaRequest
    ): PersonaResponse =
        userOwnedPersonaService.updateOwnPersona(
            requesterUsername = authentication.name,
            personaId = id,
            name = request.name,
            description = request.description,
            systemPrompt = request.systemPrompt,
            summaryStyle = request.summaryStyle,
            targetAudience = request.targetAudience,
            maxItems = request.maxItems,
            language = request.language,
            isActive = request.isActive
        ).toResponse()

    /** 페르소나를 삭제한다. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(authentication: Authentication, @PathVariable id: String) {
        userOwnedPersonaService.deleteOwnPersona(authentication.name, id)
    }

    private fun UserOwnedPersonaView.toResponse() = PersonaResponse(
        id = persona.id, name = persona.name, description = persona.description,
        systemPrompt = persona.systemPrompt, summaryStyle = persona.summaryStyle,
        targetAudience = persona.targetAudience, maxItems = persona.maxItems,
        language = persona.language, isActive = persona.isActive,
        currentVersion = persona.currentVersion,
        tone = persona.tone, lengthPref = persona.lengthPref,
        createdAt = persona.createdAt.toString(), updatedAt = persona.updatedAt.toString()
    )

    private fun com.ohmyclipping.model.Persona.toResponse() = PersonaResponse(
        id = id, name = name, description = description,
        systemPrompt = systemPrompt, summaryStyle = summaryStyle,
        targetAudience = targetAudience, maxItems = maxItems,
        language = language, isActive = isActive,
        currentVersion = currentVersion,
        tone = tone, lengthPref = lengthPref,
        createdAt = createdAt.toString(), updatedAt = updatedAt.toString()
    )
}
