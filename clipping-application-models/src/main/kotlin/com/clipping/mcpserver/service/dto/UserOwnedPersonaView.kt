package com.clipping.mcpserver.service.dto

import com.clipping.mcpserver.model.Persona

/**
 * 사용자 setup 화면에 보여줄 페르소나 뷰를 전달한다.
 */
data class UserOwnedPersonaView(
    val persona: Persona
)
