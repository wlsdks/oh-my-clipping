package com.ohmyclipping.config

import com.ohmyclipping.store.AdminUserStore
import org.springframework.core.annotation.Order
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

/**
 * mustChangePassword=true인 사용자의 민감 API 접근을 차단한다.
 * 비밀번호 변경, /me 조회, 읽기 전용 API는 허용한다.
 */
@Component
@Order(1)
class MustChangePasswordFilter(
    private val adminUserStore: AdminUserStore
) : WebFilter {

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        // 차단 대상이 아닌 요청은 즉시 통과시킨다.
        if (!isBlockedPath(exchange)) {
            return chain.filter(exchange)
        }

        // 인증 컨텍스트에서 사용자명을 꺼내 mustChangePassword 플래그를 확인한다.
        return ReactiveSecurityContextHolder.getContext()
            .map { it.authentication }
            .filter { it != null && it.isAuthenticated }
            .map { it.name }
            .flatMap { username ->
                Mono.defer { Mono.justOrEmpty(adminUserStore.findByUsername(username)) }
                    .subscribeOn(Schedulers.boundedElastic())
            }
            .flatMap { user ->
                if (user.mustChangePassword) {
                    respondForbidden(exchange).then(Mono.just(true))
                } else {
                    chain.filter(exchange).then(Mono.just(true))
                }
            }
            .switchIfEmpty(Mono.defer { chain.filter(exchange).then(Mono.just(true)) })
            .then()
    }

    /**
     * mustChangePassword 상태에서 차단할 요청인지 판별한다.
     * POST/PUT/DELETE 중 민감 경로만 차단한다.
     */
    private fun isBlockedPath(exchange: ServerWebExchange): Boolean {
        val path = exchange.request.path.pathWithinApplication().value()
        val method = exchange.request.method
        return BLOCKED_PATTERNS.any { (m, p) ->
            method == m && path.startsWith(p)
        }
    }

    /** 403 Forbidden 응답과 안내 JSON을 반환한다. */
    private fun respondForbidden(exchange: ServerWebExchange): Mono<Void> {
        val response = exchange.response
        response.statusCode = HttpStatus.FORBIDDEN
        response.headers.contentType = MediaType.APPLICATION_JSON
        val body = """{"error":"must_change_password","message":"비밀번호를 먼저 변경해 주세요."}"""
        val buffer = response.bufferFactory().wrap(body.toByteArray(Charsets.UTF_8))
        return response.writeWith(Mono.just(buffer))
    }

    companion object {
        /** 차단 대상: 민감 쓰기 API 패턴 (비밀번호 변경·/me 조회는 제외). */
        private val BLOCKED_PATTERNS = listOf(
            HttpMethod.POST to "/api/user/account/withdraw",
            HttpMethod.POST to "/api/user/clipping-requests",
            HttpMethod.PUT to "/api/user/clipping-requests",
            HttpMethod.DELETE to "/api/user/clipping-requests"
        )
    }
}
