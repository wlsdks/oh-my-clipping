package com.ohmyclipping.config

import com.ohmyclipping.service.RuntimeSettingService
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

/**
 * 점검 모드가 활성화되면 사용자 쓰기 API를 503으로 차단한다.
 * 공개 API, 로그인, 관리자 API는 기존 보안 정책을 유지한다.
 */
@Component
class MaintenanceModeWebFilter(
    private val runtimeSettingService: RuntimeSettingService,
    private val objectMapper: ObjectMapper
) : WebFilter {

    /**
     * 현재 요청이 점검 모드 차단 대상이면 안내 JSON을 반환한다.
     * 차단 대상이 아니면 다음 필터로 넘긴다.
     */
    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        // 점검 모드가 아니거나 사용자 쓰기 API가 아니면 기존 체인을 그대로 진행한다.
        if (!shouldBlock(exchange)) {
            return chain.filter(exchange)
        }

        val maintenanceStatus = runtimeSettingService.maintenanceStatus()
        if (!maintenanceStatus.active) {
            return chain.filter(exchange)
        }

        // 점검 중인 사용자 쓰기 요청은 명시적 503과 안내 메시지로 응답한다.
        val response = exchange.response
        response.statusCode = HttpStatus.SERVICE_UNAVAILABLE
        response.headers.contentType = MediaType.APPLICATION_JSON
        val responseBody = objectMapper.writeValueAsBytes(
            MaintenanceBlockedResponse(
                maintenanceMode = true,
                message = maintenanceStatus.message.ifBlank { DEFAULT_MESSAGE }
            )
        )
        val buffer = response.bufferFactory().wrap(responseBody)
        return response.writeWith(Mono.just(buffer))
    }

    /**
     * `/api/user` 하위의 mutating method만 점검 모드 차단 대상으로 본다.
     */
    private fun shouldBlock(exchange: ServerWebExchange): Boolean {
        val path = exchange.request.path.pathWithinApplication().value()
        val method = exchange.request.method
        return method in BLOCKED_METHODS && (path == USER_API_PREFIX || path.startsWith("$USER_API_PREFIX/"))
    }

    private data class MaintenanceBlockedResponse(
        val maintenanceMode: Boolean,
        val message: String
    )

    companion object {
        private const val USER_API_PREFIX = "/api/user"
        private const val DEFAULT_MESSAGE = "서비스 점검 중입니다."
        private val BLOCKED_METHODS = setOf(
            HttpMethod.POST,
            HttpMethod.PUT,
            HttpMethod.PATCH,
            HttpMethod.DELETE
        )
    }
}
