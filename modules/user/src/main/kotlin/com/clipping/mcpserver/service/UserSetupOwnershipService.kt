package com.clipping.mcpserver.service

import com.clipping.mcpserver.error.NotFoundException
import com.clipping.mcpserver.error.ensureValid
import com.clipping.mcpserver.model.AccountRole
import com.clipping.mcpserver.model.AdminUser
import com.clipping.mcpserver.model.UserClippingRequestStatus
import com.clipping.mcpserver.store.AdminUserStore
import com.clipping.mcpserver.store.UserClippingRequestStore
import com.clipping.mcpserver.store.UserOwnedCategoryStore
import com.clipping.mcpserver.store.PersonaStore
import com.clipping.mcpserver.store.UserOwnedPersonaStore
import com.clipping.mcpserver.store.UserOwnedSourceStore
import org.springframework.stereotype.Service

/**
 * user setup에서 사용하는 owner-scope 판정과 매핑 기록을 담당한다.
 */
@Service
class UserSetupOwnershipService(
    private val adminUserStore: AdminUserStore,
    private val requestStore: UserClippingRequestStore,
    private val userOwnedPersonaStore: UserOwnedPersonaStore,
    private val userOwnedCategoryStore: UserOwnedCategoryStore,
    private val userOwnedSourceStore: UserOwnedSourceStore,
    private val personaStore: PersonaStore
) {

    /**
     * USER 계정만 setup 리소스를 다루게 제한한다.
     */
    fun requireUserRequester(username: String, action: String): AdminUser =
        requireUserByUsername(username).also { requester ->
            // setup API는 USER 계정만 호출할 수 있다.
            ensureValid(requester.role == AccountRole.USER) {
                "Only USER accounts can $action"
            }
        }

    /**
     * 승인 완료 또는 위자드 생성 리소스를 기준으로 페르소나 소유권을 확인한다.
     */
    fun ensureOwnsPersona(userId: String, personaId: String) {
        // 프리셋 페르소나는 모든 유저가 사용 가능하다.
        val persona = personaStore.findById(personaId)
        if (persona?.isPreset == true) return
        // 직접 생성 매핑 또는 승인된 요청 연결 중 하나면 접근을 허용한다.
        if (userOwnedPersonaStore.exists(userId, personaId) || isApprovedPersonaLocked(userId, personaId)) return
        throw NotFoundException("Persona not found: $personaId")
    }

    /**
     * 승인 완료 또는 위자드 생성 리소스를 기준으로 카테고리 소유권을 확인한다.
     */
    fun ensureOwnsCategory(userId: String, categoryId: String) {
        // 직접 생성 매핑 또는 승인된 요청 연결 중 하나면 접근을 허용한다.
        if (userOwnedCategoryStore.exists(userId, categoryId) || hasApprovedCategory(userId, categoryId)) return
        throw NotFoundException("Category not found: $categoryId")
    }

    /**
     * 승인 완료 또는 위자드 생성 리소스를 기준으로 RSS 소스 소유권을 확인한다.
     */
    fun ensureOwnsSource(userId: String, sourceId: String) {
        // 직접 생성 매핑 또는 승인된 요청 연결 중 하나면 접근을 허용한다.
        if (userOwnedSourceStore.exists(userId, sourceId) || hasApprovedSource(userId, sourceId)) return
        throw NotFoundException("Source not found: $sourceId")
    }

    /**
     * 사용자에게 노출할 owned persona ID 목록을 만든다.
     */
    fun listOwnedPersonaIds(userId: String): List<String> {
        val ownedIds = linkedSetOf<String>()
        // 위자드 직접 저장한 페르소나를 먼저 담는다.
        ownedIds += userOwnedPersonaStore.listPersonaIds(userId)
        // 승인 완료된 요청에서 연결된 페르소나도 저장 목록에 포함한다.
        approvedRequests(userId)
            .mapNotNull { it.approvedPersonaId }
            .forEach { ownedIds += it }
        return ownedIds.toList()
    }

    /**
     * 승인되거나 위자드에서 직접 만든 리소스를 owner 매핑에 등록한다.
     */
    fun registerOwnedResources(
        userId: String,
        categoryId: String?,
        personaId: String?,
        sourceId: String?
    ) {
        // 승인 완료 후 재조회 없이 owner-scope API를 타기 위해 즉시 매핑을 남긴다.
        categoryId?.takeIf { it.isNotBlank() }?.let { userOwnedCategoryStore.save(userId, it) }
        personaId?.takeIf { it.isNotBlank() }?.let { userOwnedPersonaStore.save(userId, it) }
        sourceId?.takeIf { it.isNotBlank() }?.let { userOwnedSourceStore.save(userId, it) }
    }

    /**
     * 승인된 구독에 연결된 페르소나는 setup API에서 잠금 대상으로 본다.
     */
    fun isApprovedPersonaLocked(userId: String, personaId: String): Boolean {
        // APPROVED 요청에 연결된 페르소나는 운영 검토 없이 직접 수정하면 안 된다.
        return approvedRequests(userId).any { it.approvedPersonaId == personaId }
    }

    private fun requireUserByUsername(username: String) =
        adminUserStore.findByUsername(username.trim().lowercase())
            ?: throw NotFoundException("User not found: $username")

    private fun hasApprovedCategory(userId: String, categoryId: String): Boolean =
        approvedRequests(userId).any { it.approvedCategoryId == categoryId }

    private fun hasApprovedSource(userId: String, sourceId: String): Boolean =
        approvedRequests(userId).any { it.approvedSourceId == sourceId }

    private fun approvedRequests(userId: String) =
        requestStore.listByRequesterUserId(userId)
            .filter { it.status == UserClippingRequestStatus.APPROVED }
}
