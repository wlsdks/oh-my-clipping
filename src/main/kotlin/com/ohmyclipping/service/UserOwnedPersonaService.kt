package com.ohmyclipping.service

import com.ohmyclipping.error.ensureValid
import com.ohmyclipping.error.NotFoundException
import com.ohmyclipping.model.Persona
import com.ohmyclipping.service.dto.user.UserOwnedPersonaView
import com.ohmyclipping.store.PersonaStore
import com.ohmyclipping.store.UserOwnedPersonaStore
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 사용자 전용 페르소나 CRUD를 담당한다.
 */
@Service
class UserOwnedPersonaService(
    private val adminPersonaService: AdminPersonaService,
    private val personaStore: PersonaStore,
    private val userOwnedPersonaStore: UserOwnedPersonaStore,
    private val userSetupOwnershipService: UserSetupOwnershipService
) {

    /**
     * 로그인 사용자가 직접 만들었거나 승인 요청으로 연결된 페르소나만 조회한다.
     */
    fun listOwnPersonas(requesterUsername: String): List<UserOwnedPersonaView> {
        val requester = userSetupOwnershipService.requireUserRequester(requesterUsername, "list setup personas")
        return userSetupOwnershipService.listOwnedPersonaIds(requester.id)
            .mapNotNull { personaStore.findById(it) }
            .sortedByDescending { it.createdAt }
            .map { persona ->
                UserOwnedPersonaView(persona = persona)
            }
    }

    /**
     * 로그인 사용자가 접근 가능한 페르소나 한 건만 조회한다.
     */
    fun getOwnPersona(requesterUsername: String, personaId: String): UserOwnedPersonaView {
        val requester = userSetupOwnershipService.requireUserRequester(requesterUsername, "access setup personas")
        // owner-scope를 먼저 강제해 타 사용자 페르소나를 감춘다.
        userSetupOwnershipService.ensureOwnsPersona(requester.id, personaId)
        val persona = personaStore.findById(personaId) ?: throw NotFoundException("Persona not found: $personaId")
        return UserOwnedPersonaView(persona = persona)
    }

    /**
     * 사용자가 자기 전용 페르소나를 만든 뒤 owner 매핑을 함께 저장한다.
     */
    @Transactional
    fun createOwnPersona(
        requesterUsername: String,
        name: String,
        description: String?,
        systemPrompt: String,
        summaryStyle: String?,
        targetAudience: String?,
        maxItems: Int,
        language: String
    ): Persona {
        val requester = userSetupOwnershipService.requireUserRequester(requesterUsername, "create setup personas")
        // 관리자 생성 정책을 재사용해 입력 검증 기준을 맞춘다.
        val persona = adminPersonaService.createPersona(
            name = name,
            description = description,
            systemPrompt = systemPrompt,
            summaryStyle = summaryStyle,
            targetAudience = targetAudience,
            maxItems = maxItems,
            language = language
        )
        // 이후 수정/삭제/list에서 owner-scope를 적용할 수 있게 매핑을 저장한다.
        userOwnedPersonaStore.save(requester.id, persona.id)
        return persona
    }

    /**
     * 본인 페르소나를 수정한다.
     */
    fun updateOwnPersona(
        requesterUsername: String,
        personaId: String,
        name: String?,
        description: String?,
        systemPrompt: String?,
        summaryStyle: String?,
        targetAudience: String?,
        maxItems: Int?,
        language: String?,
        isActive: Boolean?
    ): Persona {
        val requester = userSetupOwnershipService.requireUserRequester(requesterUsername, "update setup personas")
        // 본인 소유가 아닌 페르소나는 수정하지 못하게 막는다.
        userSetupOwnershipService.ensureOwnsPersona(requester.id, personaId)
        // 승인된 구독에 연결된 페르소나는 운영 검토 요청으로만 변경하게 강제한다.
        ensureEditablePersona(requester.id, personaId)
        return adminPersonaService.updatePersona(
            id = personaId,
            name = name,
            description = description,
            systemPrompt = systemPrompt,
            summaryStyle = summaryStyle,
            targetAudience = targetAudience,
            maxItems = maxItems,
            language = language,
            isActive = isActive
        )
    }

    /**
     * 본인 페르소나를 삭제한다.
     */
    @Transactional
    fun deleteOwnPersona(requesterUsername: String, personaId: String) {
        val requester = userSetupOwnershipService.requireUserRequester(requesterUsername, "delete setup personas")
        // 본인 소유가 아닌 페르소나는 삭제하지 못하게 막는다.
        userSetupOwnershipService.ensureOwnsPersona(requester.id, personaId)
        // 승인된 구독에 연결된 페르소나는 운영 검토 없이 삭제하지 못하게 막는다.
        ensureEditablePersona(requester.id, personaId)
        adminPersonaService.deletePersona(personaId)
        userOwnedPersonaStore.delete(requester.id, personaId)
    }

    /**
     * 승인된 구독에 연결된 페르소나는 setup API에서 직접 수정/삭제하지 못하게 막는다.
     */
    private fun ensureEditablePersona(userId: String, personaId: String) {
        // 활성 구독 정책을 깨지 않기 위해 승인된 persona는 운영 검토 요청으로만 변경한다.
        ensureValid(!userSetupOwnershipService.isApprovedPersonaLocked(userId, personaId)) {
            "승인된 구독에 연결된 페르소나는 setup API에서 직접 수정하거나 삭제할 수 없습니다. 운영 검토 요청을 통해 변경해 주세요."
        }
    }
}
