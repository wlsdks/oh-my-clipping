package com.ohmyclipping.admin

import com.ohmyclipping.admin.dto.CreatePersonaRequest
import com.ohmyclipping.admin.dto.PersonaResponse
import com.ohmyclipping.admin.dto.SetPersonaActiveRequest
import com.ohmyclipping.admin.dto.UpdatePersonaRequest
import com.ohmyclipping.model.Persona
import com.ohmyclipping.service.AdminPersonaService
import com.ohmyclipping.service.dto.PersonaVersionDetail
import com.ohmyclipping.service.dto.PersonaVersionSummary
import com.ohmyclipping.support.IdempotencyKeyService
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

/**
 * 페르소나 관리 API를 제공하는 컨트롤러.
 */
@RestController
@RequestMapping("/api/admin/personas")
class PersonaAdminController(
    private val adminPersonaService: AdminPersonaService,
    private val idempotencyKeyService: IdempotencyKeyService
) {

    /**
     * 페르소나 목록을 조회합니다.
     */
    @GetMapping
    fun list(): List<PersonaResponse> =
        adminPersonaService.listPersonas().map { it.toResponse() }

    /**
     * 페르소나 단건을 조회합니다.
     */
    @GetMapping("/{id}")
    fun get(@PathVariable id: String): PersonaResponse =
        adminPersonaService.getPersona(id).toResponse()

    /**
     * 페르소나를 생성합니다.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@RequestBody request: CreatePersonaRequest): PersonaResponse =
        adminPersonaService.createPersona(
            name = request.name,
            description = request.description,
            systemPrompt = request.systemPrompt,
            summaryStyle = request.summaryStyle,
            targetAudience = request.targetAudience,
            maxItems = request.maxItems,
            language = request.language
        ).toResponse()

    /**
     * 페르소나 정보를 수정합니다.
     *
     * `Idempotency-Key` 헤더가 제공되면 같은 키의 재전송은 DB 를 다시 건드리지 않고 첫 응답을 그대로 재사용한다.
     */
    @PutMapping("/{id}")
    fun update(
        @PathVariable id: String,
        @RequestBody request: UpdatePersonaRequest,
        @RequestHeader(value = "Idempotency-Key", required = false) idempotencyKey: String?,
        authentication: Authentication
    ): PersonaResponse =
        idempotencyKeyService.executeIfKeyPresent(
            actor = authentication.name,
            key = idempotencyKey,
            resultClass = PersonaResponse::class.java
        ) {
            adminPersonaService.updatePersona(
                id = id,
                name = request.name,
                description = request.description,
                systemPrompt = request.systemPrompt,
                summaryStyle = request.summaryStyle,
                targetAudience = request.targetAudience,
                maxItems = request.maxItems,
                language = request.language,
                isActive = request.isActive,
                previewTitle = request.previewTitle,
                previewSource = request.previewSource,
                previewBody = request.previewBody,
                actorUsername = authentication.name
            ).toResponse()
        }

    /**
     * 페르소나 활성 상태만 부분 업데이트합니다.
     *
     * 기존 `PUT /{id}` 는 전체 필드 스냅샷을 요구하므로, UI에서 활성 토글만
     * 수행할 때 fetch-then-put 라운드트립을 피하기 위해 제공합니다.
     *
     * - 존재하지 않는 `id` → 404 (서비스 계층 [NotFoundException])
     * - 프리셋 페르소나 비활성화 시도 → 409 (서비스 계층 [ConflictException])
     * - 이미 동일 상태면 저장소 쓰기 없이 현재 스냅샷을 반환합니다.
     */
    @PatchMapping("/{id}/active")
    fun patchActive(
        @PathVariable id: String,
        @RequestBody request: SetPersonaActiveRequest,
        authentication: org.springframework.security.core.Authentication
    ): PersonaResponse =
        adminPersonaService.setActive(
            id = id,
            isActive = request.isActive,
            actorUsername = authentication.name
        ).toResponse()

    /**
     * 페르소나를 삭제합니다.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: String, authentication: org.springframework.security.core.Authentication) {
        adminPersonaService.deletePersona(id, deletedByUsername = authentication.name)
    }

    /**
     * 특정 페르소나의 버전 히스토리 목록을 조회합니다.
     */
    @GetMapping("/{id}/versions")
    fun getVersions(@PathVariable id: String): List<PersonaVersionSummary> {
        return adminPersonaService.getVersions(id)
    }

    /**
     * 특정 페르소나의 특정 버전 스냅샷 상세를 조회합니다.
     */
    @GetMapping("/{id}/versions/{version}")
    fun getVersionDetail(@PathVariable id: String, @PathVariable version: Int): PersonaVersionDetail {
        return adminPersonaService.getVersionDetail(id, version)
    }

    /**
     * 특정 버전으로 롤백합니다.
     * 현재 상태를 스냅샷으로 저장한 뒤, 대상 버전의 스냅샷으로 복원합니다.
     */
    @PostMapping("/{id}/rollback/{version}")
    fun rollback(@PathVariable id: String, @PathVariable version: Int): PersonaResponse {
        val persona = adminPersonaService.rollbackToVersion(id, version)
        return persona.toResponse()
    }

    private fun Persona.toResponse() = PersonaResponse(
        id = id, name = name, description = description,
        systemPrompt = systemPrompt, summaryStyle = summaryStyle,
        targetAudience = targetAudience, maxItems = maxItems,
        language = language, isActive = isActive,
        isPreset = isPreset,
        previewTitle = previewTitle,
        previewSource = previewSource,
        previewBody = previewBody,
        currentVersion = currentVersion,
        tone = tone, lengthPref = lengthPref,
        createdAt = createdAt.toString(), updatedAt = updatedAt.toString()
    )
}
