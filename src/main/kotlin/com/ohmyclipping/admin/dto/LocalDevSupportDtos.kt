package com.ohmyclipping.admin.dto

/**
 * 로컬 개발용 로그인 shortcut 단건 응답 DTO.
 */
data class LocalDevLoginShortcutResponse(
    val key: String,
    val label: String,
    val scope: String,
    val username: String,
    val password: String,
    val note: String
)

/**
 * 로컬 개발용 로그인 shortcut 목록 응답 DTO.
 */
data class LocalDevLoginShortcutsEnvelope(
    val enabled: Boolean,
    val shortcuts: List<LocalDevLoginShortcutResponse>
)

