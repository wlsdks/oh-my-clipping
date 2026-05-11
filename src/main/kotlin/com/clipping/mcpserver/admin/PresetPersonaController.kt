package com.clipping.mcpserver.admin

import com.clipping.mcpserver.admin.dto.PersonaResponse
import com.clipping.mcpserver.model.Persona
import com.clipping.mcpserver.service.AdminPersonaService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 유저용 프리셋 페르소나 조회 컨트롤러.
 * 관리자가 등록한 기본 프리셋만 활성 상태로 반환한다.
 */
@RestController
@RequestMapping("/api/user/setup/preset-personas")
class PresetPersonaController(
    private val adminPersonaService: AdminPersonaService
) {

    /** 활성 프리셋 목록 반환 */
    @GetMapping
    fun listPresets(): List<PersonaResponse> =
        adminPersonaService.listPresets().map { it.toResponse() }

    private fun Persona.toResponse() = PersonaResponse(
        id = id, name = name, description = description,
        systemPrompt = systemPrompt, summaryStyle = summaryStyle,
        targetAudience = targetAudience, maxItems = maxItems,
        language = language, isActive = isActive,
        isPreset = isPreset,
        previewTitle = previewTitle,
        previewSource = previewSource,
        previewBody = previewBody,
        createdAt = createdAt.toString(), updatedAt = updatedAt.toString()
    )
}
