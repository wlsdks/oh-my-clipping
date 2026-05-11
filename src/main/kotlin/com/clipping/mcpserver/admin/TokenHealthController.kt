package com.clipping.mcpserver.admin

import com.clipping.mcpserver.admin.dto.TokenHealthResponse
import com.clipping.mcpserver.observability.TokenHealthTracker
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 토큰(Slack Bot / Gemini API) 헬스 상태 조회 엔드포인트.
 *
 * F8: 토큰 만료/quota 소진 알림 경로의 프론트 배너가 이 엔드포인트를 폴링해
 * 운영팀에게 즉시 경고를 노출한다. 인증이 필요한 `/api/admin/system/token-health`
 * 경로로 노출되어 관리자 콘솔에서만 접근 가능하다.
 */
@RestController
@RequestMapping("/api/admin/system")
class TokenHealthController(
    private val tokenHealthTracker: TokenHealthTracker
) {

    /**
     * 현재 Slack Bot 토큰과 Gemini API 키의 헬스 상태를 반환한다.
     *
     * 응답은 wire 형식(`ok`/`expired`/`scope_mismatch`/`quota_exhausted`/`unknown`) 으로
     * 내려가며 프론트 배너는 `ok` 이외일 때만 경고를 표시한다.
     */
    @GetMapping("/token-health")
    fun getTokenHealth(): TokenHealthResponse {
        val slack = tokenHealthTracker.slackBotStatus()
        val gemini = tokenHealthTracker.geminiStatus()
        return TokenHealthResponse(
            slackBot = slack.wireValue,
            gemini = gemini.wireValue,
            ok = slack == com.clipping.mcpserver.observability.TokenStatus.OK &&
                gemini == com.clipping.mcpserver.observability.TokenStatus.OK
        )
    }
}
