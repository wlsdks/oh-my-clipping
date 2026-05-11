package com.clipping.mcpserver.config

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

private val log = KotlinLogging.logger {}

/**
 * Slack 웹훅 요청의 HMAC-SHA256 서명을 검증하는 필터.
 *
 * `/api/slack/` 하위 경로로 들어오는 요청에 대해 `X-Slack-Signature`와
 * `X-Slack-Request-Timestamp` 헤더를 확인한다.
 *
 * - 타임스탬프가 5분 이상 오래된 요청은 리플레이 공격 방지를 위해 거부한다.
 * - 서명이 일치하지 않으면 401 Unauthorized를 반환한다.
 * - `SLACK_SIGNING_SECRET`이 설정되지 않으면 검증을 건너뛴다(로컬 개발 편의).
 * - `clipping.security.fail-fast=true` 에서는 secret 미설정 시 부팅 실패.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 3)
class SlackRequestVerificationFilter(
    @Value("\${SLACK_SIGNING_SECRET:}") private val signingSecret: String,
    @Value("\${clipping.security.fail-fast:false}") private val failFast: Boolean = false
) : WebFilter {

    /**
     * 운영 환경에서 secret 누락을 묵살하지 않도록 부팅 시 validate 한다.
     * fail-fast 플래그가 꺼져 있으면 로컬 dev 편의를 위해 경고로만 끝낸다.
     */
    @PostConstruct
    fun validateConfig() {
        if (signingSecret.isBlank()) {
            val base = "SLACK_SIGNING_SECRET 미설정 — Slack 웹훅 요청 서명 검증이 비활성화됩니다."
            check(!failFast) {
                "$base clipping.security.fail-fast=true 환경에서는 허용하지 않습니다."
            }
            log.warn { "$base 운영 환경에서는 반드시 설정하세요." }
        }
    }

    companion object {
        private const val SLACK_PATH_PREFIX = "/api/slack/"
        private const val TIMESTAMP_HEADER = "X-Slack-Request-Timestamp"
        private const val SIGNATURE_HEADER = "X-Slack-Signature"
        private const val SIGNATURE_VERSION = "v0"
        private const val MAX_AGE_SECONDS = 300L // 5분
        private const val HMAC_ALGORITHM = "HmacSHA256"
    }

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val path = exchange.request.path.value()
        if (!path.startsWith(SLACK_PATH_PREFIX)) {
            return chain.filter(exchange)
        }

        // 서명 시크릿이 없으면 검증을 건너뛴다 (로컬 개발 환경).
        if (signingSecret.isBlank()) {
            log.debug { "SLACK_SIGNING_SECRET 미설정 — Slack 서명 검증을 건너뜁니다." }
            return chain.filter(exchange)
        }

        val timestamp = exchange.request.headers.getFirst(TIMESTAMP_HEADER)
        val signature = exchange.request.headers.getFirst(SIGNATURE_HEADER)

        if (timestamp.isNullOrBlank() || signature.isNullOrBlank()) {
            log.warn { "Slack 요청에 서명 헤더가 없습니다: path=$path" }
            exchange.response.statusCode = HttpStatus.UNAUTHORIZED
            return exchange.response.setComplete()
        }

        // 리플레이 공격 방지: 타임스탬프가 5분 이상 오래되면 거부한다.
        val requestTime = timestamp.toLongOrNull()
        if (requestTime == null) {
            log.warn { "Slack 타임스탬프 파싱 실패: $timestamp" }
            exchange.response.statusCode = HttpStatus.UNAUTHORIZED
            return exchange.response.setComplete()
        }
        val now = System.currentTimeMillis() / 1000
        if (kotlin.math.abs(now - requestTime) > MAX_AGE_SECONDS) {
            log.warn { "Slack 요청 타임스탬프가 너무 오래됨: $timestamp (현재: $now)" }
            exchange.response.statusCode = HttpStatus.UNAUTHORIZED
            return exchange.response.setComplete()
        }

        // 요청 바디를 읽어 서명을 검증한다.
        return DataBufferUtils.join(exchange.request.body)
            .defaultIfEmpty(exchange.response.bufferFactory().wrap(ByteArray(0)))
            .flatMap { dataBuffer ->
                val body = dataBuffer.toString(StandardCharsets.UTF_8)
                DataBufferUtils.release(dataBuffer)

                val baseString = "$SIGNATURE_VERSION:$timestamp:$body"
                val computedSignature = computeSignature(baseString)
                val expectedSignature = "$SIGNATURE_VERSION=$computedSignature"

                // 상수 시간 비교로 타이밍 공격을 방지한다.
                if (!MessageDigest.isEqual(
                        expectedSignature.toByteArray(StandardCharsets.UTF_8),
                        signature.toByteArray(StandardCharsets.UTF_8)
                    )
                ) {
                    log.warn { "Slack 서명 불일치: path=$path" }
                    exchange.response.statusCode = HttpStatus.UNAUTHORIZED
                    return@flatMap exchange.response.setComplete()
                }

                // 바디를 이미 소비했으므로 캐시된 바디로 요청을 재구성한다.
                val cachedBody = exchange.response.bufferFactory()
                    .wrap(body.toByteArray(StandardCharsets.UTF_8))
                val mutatedExchange = exchange.mutate()
                    .request(
                        exchange.request.mutate().build()
                    )
                    .build()
                val decoratedExchange = object : org.springframework.web.server.ServerWebExchangeDecorator(mutatedExchange) {
                    override fun getRequest() = object : org.springframework.http.server.reactive.ServerHttpRequestDecorator(
                        mutatedExchange.request
                    ) {
                        override fun getBody() = reactor.core.publisher.Flux.just(cachedBody)
                    }
                }

                chain.filter(decoratedExchange)
            }
    }

    /**
     * HMAC-SHA256으로 서명을 계산한다.
     */
    private fun computeSignature(baseString: String): String {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(signingSecret.toByteArray(StandardCharsets.UTF_8), HMAC_ALGORITHM))
        val hash = mac.doFinal(baseString.toByteArray(StandardCharsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }
}
