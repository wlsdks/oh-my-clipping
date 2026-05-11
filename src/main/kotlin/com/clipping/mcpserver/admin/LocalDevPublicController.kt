package com.clipping.mcpserver.admin

import com.clipping.mcpserver.admin.dto.LocalDevLoginShortcutResponse
import com.clipping.mcpserver.admin.dto.LocalDevLoginShortcutsEnvelope
import com.clipping.mcpserver.service.LocalDevLoginShortcut
import com.clipping.mcpserver.service.LocalDevSupportService
import org.springframework.context.annotation.Profile
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 로컬 개발 환경에서만 사용하는 공개 shortcut API를 제공합니다.
 */
@Profile("local")
@RestController
@RequestMapping("/api/public/dev")
class LocalDevPublicController(
    private val localDevSupportService: LocalDevSupportService
) {

    /**
     * 로그인 화면에서 사용하는 개발용 shortcut 목록을 반환합니다.
     */
    @GetMapping("/login-shortcuts")
    fun getLoginShortcuts(): LocalDevLoginShortcutsEnvelope =
        LocalDevLoginShortcutsEnvelope(
            enabled = true,
            shortcuts = localDevSupportService.loginShortcuts().map { it.toResponse() }
        )

    /**
     * 서비스 내부 shortcut 모델을 공개 응답 DTO로 변환합니다.
     */
    private fun LocalDevLoginShortcut.toResponse(): LocalDevLoginShortcutResponse =
        LocalDevLoginShortcutResponse(
            key = key,
            label = label,
            scope = scope,
            username = username,
            password = password,
            note = note
        )
}

