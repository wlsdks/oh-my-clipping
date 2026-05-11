package com.clipping.mcpserver.mcp

import com.clipping.mcpserver.observability.ClippingMetrics
import com.clipping.mcpserver.store.McpAuditEntry
import com.clipping.mcpserver.store.McpAuditLogStore
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID

private val log = KotlinLogging.logger {}

/**
 * MCP 엔드포인트(`/sse`, `/mcp/message`)에 대한 Bearer 토큰 인증 필터.
 *
 * Spring Security의 `permitAll()` 설정으로 세션 인증을 우회하는 MCP 경로에
 * 서비스 토큰 기반 인증을 적용한다.
 * 토큰 비교는 `MessageDigest.isEqual`을 사용하여 타이밍 공격을 방지한다.
 *
 * 기동 시 토큰이 비어 있거나 32자 미만이면 `IllegalStateException`을 던져
 * 무인증 상태로 서버가 시작되는 것을 차단한다.
 *
 * 인증 성공 시 요청 토큰의 SHA-256 prefix(`tokenKid`, 8 hex chars)를
 * exchange attribute 로 전달하여 downstream 필터/도구에서 per-caller
 * rate-limit actor 로 활용한다. 인증 실패 시에는 `McpAuditFilter` 가
 * 요청을 보지 못하므로 여기서 직접 감사 로그를 남겨 경계선 침투 시도를
 * 추적할 수 있게 한다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = ["clipping.mcp.server.enabled"], havingValue = "true"
)
class McpBearerAuthFilter(
    @Value("\${clipping.mcp.service-token:}") private val serviceToken: String,
    private val metrics: ClippingMetrics,
    private val mcpAuditLogStore: McpAuditLogStore,
) : WebFilter {

    companion object {
        private const val BEARER_PREFIX = "Bearer "
        private const val MIN_TOKEN_LENGTH = 32

        /** 인증된 호출자의 SHA-256 토큰 지문(앞 8 hex)을 담는 exchange attribute 키. */
        const val ATTR_TOKEN_KID = "mcp.tokenKid"

        /** 인증 실패 시 감사 로그에 쓰는 actor / tool 고정 값. */
        private const val AUTH_FAILED_ACTOR = "__auth_failed__"
        private const val AUTH_FAILED_TOOL = "__auth_failed__"

        private val HEX_CHARS: CharArray = "0123456789abcdef".toCharArray()
    }

    /**
     * 기동 시 서비스 토큰의 유효성을 검증한다.
     * 토큰이 비어 있거나 최소 길이 미만이면 서버 시작을 중단한다.
     */
    @PostConstruct
    fun validateToken() {
        check(serviceToken.isNotBlank()) {
            "clipping.mcp.service-token이 설정되지 않았습니다. " +
                "MCP 엔드포인트를 무인증 상태로 노출할 수 없습니다."
        }
        check(serviceToken.length >= MIN_TOKEN_LENGTH) {
            "clipping.mcp.service-token이 ${MIN_TOKEN_LENGTH}자 미만입니다. " +
                "보안을 위해 충분히 긴 토큰을 사용하세요."
        }
        log.info { "MCP 서비스 토큰 검증 완료 (길이=${serviceToken.length})" }
    }

    /**
     * MCP 경로 요청에서 Bearer 토큰을 추출하고 상수 시간 비교로 인증한다.
     * MCP 경로가 아닌 요청은 필터 체인을 그대로 통과시킨다.
     */
    override fun filter(
        exchange: ServerWebExchange,
        chain: WebFilterChain
    ): Mono<Void> {
        val path = exchange.request.path.value()

        // MCP 경로가 아니면 다음 필터로 넘긴다
        if (!isMcpPath(path)) {
            return chain.filter(exchange)
        }

        val authHeader = exchange.request.headers
            .getFirst(HttpHeaders.AUTHORIZATION).orEmpty()

        // Bearer 접두어를 제거하여 토큰을 추출한다
        val presentedToken = extractBearerToken(authHeader)
        if (presentedToken.isBlank()) {
            log.warn { "MCP 인증 실패: Bearer 토큰 없음 (path=$path)" }
            metrics.recordMcpBearerAuthFailure()
            recordAuthFailureAudit(exchange, reason = "missing_bearer")
            return reject(exchange)
        }

        // 상수 시간 비교로 토큰을 검증한다
        if (!isTokenValid(presentedToken)) {
            log.warn { "MCP 인증 실패: 토큰 불일치 (path=$path)" }
            metrics.recordMcpBearerAuthFailure()
            recordAuthFailureAudit(exchange, reason = "token_mismatch")
            return reject(exchange)
        }

        // 호출자 토큰 지문을 downstream 필터/도구에서 rate-limit actor 로 사용할 수 있게 남긴다.
        exchange.attributes[ATTR_TOKEN_KID] = tokenKid(presentedToken)

        return chain.filter(exchange)
    }

    /**
     * 인증 실패 감사 로그를 비동기로 기록한다. downstream [McpAuditFilter] 는
     * 인증 성공 요청만 보기 때문에, 경계 이전 실패 시도는 이 필터가 직접 남긴다.
     * actor/tool 은 `__auth_failed__` sentinel 로 고정하고 args_json 은 remoteIp,
     * path, reason 만 포함해 민감 정보(헤더/본문)는 절대 섞지 않는다.
     */
    private fun recordAuthFailureAudit(exchange: ServerWebExchange, reason: String) {
        val remoteAddress = exchange.request.remoteAddress?.address?.hostAddress
        val path = exchange.request.path.value()
        // 간단한 JSON 수동 조립 — 외부에서 주입되는 값은 따옴표만 제거해 injection 을 차단한다.
        val argsJson = buildString {
            append("{")
            append("\"remoteIp\":\"").append(remoteAddress?.replace("\"", "") ?: "unknown").append("\",")
            append("\"path\":\"").append(path.replace("\"", "")).append("\",")
            append("\"reason\":\"").append(reason).append("\"")
            append("}")
        }
        val entry = McpAuditEntry(
            id = UUID.randomUUID().toString(),
            requestId = UUID.randomUUID().toString(),
            actor = AUTH_FAILED_ACTOR,
            toolName = AUTH_FAILED_TOOL,
            argsJson = argsJson,
            resultStatus = "ERROR",
            resultCode = null,
            httpStatusCode = HttpStatus.UNAUTHORIZED.value(),
            durationMs = 0,
            errorMessage = "auth_failed:$reason",
        )
        // 감사 로그 저장은 리액터 이벤트 루프를 막지 않도록 boundedElastic 에서 수행한다.
        Mono.fromRunnable<Unit> {
            try {
                mcpAuditLogStore.insert(entry)
            } catch (e: Exception) {
                log.error(e) { "MCP 인증 실패 감사 로그 저장 실패: path=$path" }
            }
        }.subscribeOn(Schedulers.boundedElastic()).subscribe()
    }

    /**
     * MCP 엔드포인트 경로인지 판별한다.
     *
     * 지원 transport:
     * - `/sse` + `/mcp/message`: SSE transport (MCP 2024-11-05 spec)
     * - `/mcp`: STREAMABLE HTTP transport (MCP 2025-03-26 spec, R_2026-04 추가)
     *
     * `/mcp/message` 가 `/mcp` 보다 startsWith 매칭이 strict 하므로 순서 무관 OK.
     */
    private fun isMcpPath(path: String): Boolean =
        path.startsWith("/sse") || path.startsWith("/mcp/message") || path == "/mcp" || path.startsWith("/mcp?")

    /** Authorization 헤더에서 Bearer 접두어를 제거하고 토큰 문자열을 반환한다. */
    private fun extractBearerToken(header: String): String =
        if (header.startsWith(BEARER_PREFIX, ignoreCase = true)) {
            header.substring(BEARER_PREFIX.length).trim()
        } else {
            ""
        }

    /**
     * 제시된 토큰과 서비스 토큰을 상수 시간 비교로 검증한다.
     * 길이가 다르면 즉시 거부하되, `MessageDigest.isEqual`이
     * 내부적으로 상수 시간 보장을 제공하므로 타이밍 누출이 없다.
     */
    private fun isTokenValid(presentedToken: String): Boolean {
        val expectedBytes = serviceToken.toByteArray(StandardCharsets.UTF_8)
        val presentedBytes = presentedToken.toByteArray(StandardCharsets.UTF_8)

        // 길이가 다르면 거부 — isEqual도 길이 다르면 false를 반환하지만
        // 명시적으로 먼저 체크하여 의도를 드러낸다
        if (expectedBytes.size != presentedBytes.size) return false

        return MessageDigest.isEqual(expectedBytes, presentedBytes)
    }

    /** 401 Unauthorized로 응답을 종료한다. 보안상 응답 본문은 포함하지 않는다. */
    private fun reject(exchange: ServerWebExchange): Mono<Void> {
        exchange.response.statusCode = HttpStatus.UNAUTHORIZED
        return exchange.response.setComplete()
    }

    /**
     * 토큰의 SHA-256 해시 앞 8 hex 문자를 반환한다.
     * 다수 토큰을 운영할 때 rate-limit actor / 감사 로그 식별자로 쓰기 위한
     * 경량 지문으로, 원본 토큰을 복원할 수 없다.
     */
    private fun tokenKid(token: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(token.toByteArray(StandardCharsets.UTF_8))
        // 앞 4바이트를 8 hex 문자로 인코딩한다.
        val sb = StringBuilder(8)
        for (i in 0 until 4) {
            val b = digest[i].toInt() and 0xFF
            sb.append(HEX_CHARS[b ushr 4])
            sb.append(HEX_CHARS[b and 0x0F])
        }
        return sb.toString()
    }
}
