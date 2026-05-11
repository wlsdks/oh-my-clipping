package com.ohmyclipping.mcp

import com.ohmyclipping.observability.ClippingMetrics
import com.ohmyclipping.store.McpAuditEntry
import com.ohmyclipping.store.McpAuditLogStore
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.http.HttpStatus
import org.springframework.http.server.reactive.ServerHttpRequestDecorator
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.util.UUID

private val log = KotlinLogging.logger {}

/**
 * MCP JSON-RPC 요청(`/mcp/message`)에 대한 감사 로깅 필터.
 *
 * Bearer 인증 필터(`McpBearerAuthFilter`) 이후에 동작하며,
 * 요청 본문을 캐시하여 도구 이름을 추출하고 실행 결과를 감사 로그로 기록한다.
 *
 * 주요 동작:
 * 1. Content-Length가 10KB를 초과하면 413 PAYLOAD_TOO_LARGE로 거부
 * 2. 요청 본문을 메모리에 캐시하여 다운스트림에서도 읽을 수 있도록 데코레이터 적용
 * 3. JSON-RPC 본문에서 도구 이름(`params.name` 또는 `method`)을 추출
 * 4. 실행 완료 후 소요 시간, 상태 코드, 에러 정보를 포함한 감사 로그 저장
 *
 * 감사 로그 INSERT는 `Schedulers.boundedElastic()`에서 실행하여
 * 리액터 이벤트 루프를 블로킹하지 않는다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = ["clipping.mcp.server.enabled"], havingValue = "true"
)
class McpAuditFilter(
    private val objectMapper: ObjectMapper,
    private val mcpArgsRedactor: McpArgsRedactor,
    private val mcpAuditLogStore: McpAuditLogStore,
    private val metrics: ClippingMetrics
) : WebFilter {

    companion object {
        private const val MCP_MESSAGE_PATH = "/mcp/message"

        /** 요청 본문 최대 크기 (10KB). 초과 시 413으로 거부한다. */
        private const val MAX_BODY_SIZE = 10_240L

        /** 다운스트림 도구가 결과 코드를 기록할 때 사용하는 exchange 속성 키. */
        const val ATTR_RESULT_CODE = "mcp.resultCode"

        /** 다운스트림 도구가 에러 메시지를 기록할 때 사용하는 exchange 속성 키. */
        const val ATTR_ERROR_MESSAGE = "mcp.errorMessage"

        /** 감사 로그에서 요청을 추적하기 위한 고유 ID exchange 속성 키. */
        const val ATTR_REQUEST_ID = "mcp.requestId"
    }

    /**
     * `/mcp/message` 경로의 요청 본문을 캐시하고, 도구 호출 감사 로그를 기록한다.
     * MCP 메시지 경로가 아닌 요청은 필터 체인을 그대로 통과시킨다.
     */
    override fun filter(
        exchange: ServerWebExchange,
        chain: WebFilterChain
    ): Mono<Void> {
        val path = exchange.request.path.value()

        // MCP 메시지 경로가 아니면 다음 필터로 넘긴다
        if (path != MCP_MESSAGE_PATH) {
            return chain.filter(exchange)
        }

        // Content-Length 헤더로 사전 크기 검사를 수행한다
        val contentLength = exchange.request.headers.contentLength
        if (contentLength > MAX_BODY_SIZE) {
            log.warn { "MCP 요청 본문 크기 초과: ${contentLength}B (최대 ${MAX_BODY_SIZE}B)" }
            exchange.response.statusCode = HttpStatus.PAYLOAD_TOO_LARGE
            return exchange.response.setComplete()
        }

        // 요청 본문을 캐시하여 감사 로그용 파싱과 다운스트림 처리에 모두 사용한다
        return DataBufferUtils.join(exchange.request.body)
            .defaultIfEmpty(
                exchange.response.bufferFactory().wrap(ByteArray(0))
            )
            .flatMap { dataBuffer ->
                val bodyBytes = ByteArray(dataBuffer.readableByteCount())
                dataBuffer.read(bodyBytes)
                DataBufferUtils.release(dataBuffer)

                // 캐시된 바이트로부터 도구 이름을 추출한다
                val toolName = extractToolName(bodyBytes)

                // 요청 본문을 다운스트림에서 다시 읽을 수 있도록 데코레이터를 적용한다
                val decoratedExchange = buildDecoratedExchange(
                    exchange, bodyBytes
                )

                // 감사 추적용 요청 ID를 exchange 속성에 기록한다
                val requestId = UUID.randomUUID().toString()
                decoratedExchange.attributes[ATTR_REQUEST_ID] = requestId

                val startNanos = System.nanoTime()

                // 호출자 토큰 지문을 ThreadLocal 로 바인딩해 도구 내부 rate limiter 가 actor 로 쓰게 한다.
                val tokenKid = decoratedExchange.attributes[
                    McpBearerAuthFilter.ATTR_TOKEN_KID
                ] as? String
                McpCallerContext.setTokenKid(tokenKid)

                chain.filter(decoratedExchange)
                    .doFinally {
                        // 도구 실행 종료 후 ThreadLocal 을 해제해 다른 요청에 누수되지 않게 한다.
                        McpCallerContext.clear()
                        recordAuditLog(
                            decoratedExchange,
                            requestId,
                            toolName,
                            bodyBytes,
                            startNanos
                        )
                    }
            }
    }

    /**
     * JSON-RPC 본문에서 도구 이름을 추출한다.
     * `params.name` → `method` 순서로 탐색하며, 파싱 실패 시 "unknown"을 반환한다.
     */
    private fun extractToolName(bodyBytes: ByteArray): String {
        return try {
            val root = objectMapper.readTree(bodyBytes)
            // MCP 프로토콜: params.name에 도구 이름이 있을 수 있다
            val paramsName = root.path("params").path("name").asText("")
            if (paramsName.isNotBlank()) return paramsName

            // JSON-RPC 표준: method 필드를 폴백으로 사용한다
            val method = root.path("method").asText("")
            method.ifBlank { "unknown" }
        } catch (e: Exception) {
            log.debug { "MCP JSON-RPC 본문 파싱 실패: ${e.message}" }
            "unknown"
        }
    }

    /**
     * 캐시된 요청 본문 바이트를 재생하는 데코레이터가 적용된 exchange를 생성한다.
     * 다운스트림 핸들러가 요청 본문을 정상적으로 읽을 수 있도록 보장한다.
     */
    private fun buildDecoratedExchange(
        exchange: ServerWebExchange,
        bodyBytes: ByteArray
    ): ServerWebExchange {
        val cachedBuffer = exchange.response.bufferFactory().wrap(bodyBytes)
        val decoratedRequest = object : ServerHttpRequestDecorator(
            exchange.request
        ) {
            override fun getBody() = Flux.just(cachedBuffer)
        }
        return exchange.mutate().request(decoratedRequest).build()
    }

    /**
     * 감사 로그를 비동기로 기록한다.
     * `Schedulers.boundedElastic()`에서 실행하여 리액터 이벤트 루프를 블로킹하지 않는다.
     */
    private fun recordAuditLog(
        exchange: ServerWebExchange,
        requestId: String,
        toolName: String,
        bodyBytes: ByteArray,
        startNanos: Long
    ) {
        val durationMs = ((System.nanoTime() - startNanos) / 1_000_000).toInt()
        val httpStatus = exchange.response.statusCode?.value()
        val resultCode = exchange.attributes[ATTR_RESULT_CODE] as? Int
        val errorMessage = exchange.attributes[ATTR_ERROR_MESSAGE] as? String

        // 결과 상태를 HTTP 상태 코드 기반으로 결정한다
        val resultStatus = when {
            errorMessage != null -> "ERROR"
            httpStatus != null && httpStatus >= 400 -> "ERROR"
            else -> "OK"
        }

        // 인자를 레드액션 처리하여 민감 정보를 제거한다
        val redactedArgs = mcpArgsRedactor.redact(toolName, bodyBytes)

        // 인증 필터가 남긴 토큰 지문을 actor 로 사용하고, 없으면 anonymous 로 폴백한다.
        val actor = (
            exchange.attributes[McpBearerAuthFilter.ATTR_TOKEN_KID] as? String
        ) ?: "anonymous"

        val entry = McpAuditEntry(
            id = UUID.randomUUID().toString(),
            requestId = requestId,
            actor = actor,
            toolName = toolName,
            argsJson = redactedArgs,
            resultStatus = resultStatus,
            resultCode = resultCode,
            httpStatusCode = httpStatus,
            durationMs = durationMs,
            errorMessage = errorMessage
        )

        // MCP 도구 호출 메트릭을 기록한다
        metrics.recordMcpToolCall(
            toolName = toolName,
            resultCode = httpStatus ?: 0,
            durationMs = durationMs.toLong()
        )

        // INSERT를 boundedElastic 스케줄러에서 실행하여 블로킹을 방지한다
        Mono.fromRunnable<Unit> {
            try {
                mcpAuditLogStore.insert(entry)
            } catch (e: Exception) {
                log.error(e) {
                    "MCP 감사 로그 저장 실패: requestId=$requestId, tool=$toolName"
                }
            }
        }.subscribeOn(Schedulers.boundedElastic()).subscribe()
    }
}
