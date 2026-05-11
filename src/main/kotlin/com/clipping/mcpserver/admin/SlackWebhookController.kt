package com.clipping.mcpserver.admin

import com.clipping.mcpserver.error.InvalidInputException
import com.clipping.mcpserver.service.SummaryFeedbackService
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Slack 피드백 웹훅 수신 전용 컨트롤러.
 * `/api/slack/feedback` 엔드포인트만 담당한다.
 */
@RestController
@RequestMapping("/api/slack")
class SlackWebhookController(
    private val summaryFeedbackService: SummaryFeedbackService
) {

    @PostMapping("/feedback", consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE])
    fun handleSlackFeedbackForm(
        @RequestParam(value = "payload", required = false) rawPayload: String?
    ): ResponseEntity<Map<String, Any>> = handleSlackFeedbackPayload(rawPayload)

    @PostMapping("/feedback", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun handleSlackFeedbackJson(
        @RequestBody(required = false) rawBody: String?
    ): ResponseEntity<Map<String, Any>> {
        return handleSlackFeedbackPayload(rawBody)
    }

    @PostMapping("/feedback")
    fun handleSlackFeedbackFallback(
        @RequestParam(value = "payload", required = false) rawPayload: String?
    ): ResponseEntity<Map<String, Any>> = handleSlackFeedbackPayload(rawPayload)

    private fun handleSlackFeedbackPayload(rawPayload: String?): ResponseEntity<Map<String, Any>> {
        val payload = rawPayload?.trim()
        if (payload.isNullOrBlank()) {
            throw InvalidInputException("피드백 요청 형식이 올바르지 않습니다.")
        }

        val (feedback, message) = summaryFeedbackService.recordFromSlackPayload(payload)
        val response = mapOf(
            "response_type" to "ephemeral",
            "replace_original" to false,
            "text" to message
        )

        return ResponseEntity.ok(
            response + mapOf(
                "feedbackId" to feedback.id,
                "summaryId" to feedback.summaryId,
                "feedbackType" to feedback.feedbackType
            )
        )
    }
}
