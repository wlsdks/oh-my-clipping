package com.clipping.mcpserver.admin

import com.clipping.mcpserver.admin.dto.EditingHeartbeatRequest
import com.clipping.mcpserver.admin.dto.EditingReleaseRequest
import com.clipping.mcpserver.admin.dto.EditingSessionResponse
import com.clipping.mcpserver.service.AdminAuthService
import com.clipping.mcpserver.support.EditingPresenceService
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 편집 presence heartbeat/release/list API.
 *
 * 4개 엔티티(Persona, Category, CategoryRule, RssSource)의 편집 모달이 동시에 열릴 때,
 * 다른 관리자가 편집 중임을 UI 에 알리기 위한 가벼운 presence 트래커.
 *
 * 모든 엔드포인트는 `/api/admin` 하위 경로라 기존 보안 필터에 의해 인증된 관리자만 접근할 수 있다.
 */
@RestController
@RequestMapping("/api/admin/editing-sessions")
class EditingPresenceController(
    private val editingPresenceService: EditingPresenceService,
    private val adminAuthService: AdminAuthService
) {
    /**
     * 편집 세션을 생성/갱신한다.
     * - 최초 호출: startedAt 을 현재 시각으로 저장
     * - 반복 호출: startedAt 유지 + TTL 만 연장
     */
    @PostMapping("/heartbeat")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun heartbeat(
        @RequestBody body: EditingHeartbeatRequest,
        authentication: Authentication
    ) {
        // displayName 을 계정 정보에서 조회 — 프론트 임의 값 신뢰 금지.
        val actor = resolveActor(authentication)
        editingPresenceService.heartbeat(
            resourceType = body.resourceType,
            resourceId = body.resourceId,
            userId = actor.userId,
            displayName = actor.displayName
        )
    }

    /**
     * 편집 세션을 즉시 제거한다. 프론트가 모달을 닫거나 언마운트할 때 호출한다.
     */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun release(
        @RequestBody body: EditingReleaseRequest,
        authentication: Authentication
    ) {
        editingPresenceService.release(
            resourceType = body.resourceType,
            resourceId = body.resourceId,
            userId = authentication.name
        )
    }

    /**
     * 해당 리소스를 현재 편집 중인 다른 관리자 목록을 반환한다. 본인 세션은 제외된다.
     */
    @GetMapping
    fun list(
        @RequestParam resourceType: String,
        @RequestParam resourceId: String,
        authentication: Authentication
    ): List<EditingSessionResponse> {
        val sessions = editingPresenceService.listActive(
            resourceType = resourceType,
            resourceId = resourceId,
            excludeUserId = authentication.name
        )
        return sessions.map { EditingSessionResponse.from(it) }
    }

    /** 인증 객체에서 표시용 계정 정보를 추출한다. 조회 실패 시에도 기본값으로 흐름은 유지한다. */
    private fun resolveActor(authentication: Authentication): Actor {
        val userId = authentication.name
        val displayName = try {
            // 정책상 사용자 입력 대신 저장된 displayName 을 사용해 위조를 방지한다.
            adminAuthService.findByUsername(userId)?.displayName
        } catch (e: Exception) {
            null
        }
        return Actor(userId = userId, displayName = displayName?.takeIf { it.isNotBlank() } ?: "관리자")
    }

    private data class Actor(val userId: String, val displayName: String)
}
