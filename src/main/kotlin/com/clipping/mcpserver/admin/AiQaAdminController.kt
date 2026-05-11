package com.clipping.mcpserver.admin

import com.clipping.mcpserver.service.AiQaService
import com.clipping.mcpserver.service.dto.AiQaRequest
import com.clipping.mcpserver.service.dto.AiQaResponse
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 뉴스 AI Q&A 관리자 API 컨트롤러.
 * 사용자 질문을 받아 수집된 기사를 기반으로 AI 답변을 반환한다.
 */
@RestController
@RequestMapping("/api/admin/report")
class AiQaAdminController(
    private val service: AiQaService,
) {
    /** 사용자 질문에 대해 AI 답변을 생성한다. */
    @PostMapping("/ai-qa")
    fun askQuestion(@RequestBody request: AiQaRequest): AiQaResponse =
        service.ask(request)
}
