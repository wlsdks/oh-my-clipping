package com.clipping.mcpserver.config

import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange as SpringMockExchange
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import java.security.Principal

/**
 * [RateLimitFilter] 동작을 검증하는 단위 테스트.
 *
 * 핵심 보장 항목:
 * 1. /api/user/ 하위는 인증 사용자 기준 설정된 사용자 한도 제한이 적용된다.
 * 2. /api/admin/ 하위의 쓰기 메서드(POST/PUT/PATCH/DELETE)는 관리자별 쓰기 한도 제한이 적용된다.
 * 3. /api/admin/ 하위의 GET/HEAD/OPTIONS는 관리자별 읽기 한도 제한이 적용된다 (폴링 허용).
 * 4. 쓰기/읽기 버킷은 서로 독립이다 (`rl:admin:write:{actor}` vs `rl:admin:read:{actor}`).
 * 5. 화이트리스트(`/actuator/` 하위, `/sse`, `/mcp/message`)는 rate limit 대상이 아니다.
 * 6. 위 두 경로로 시작하지 않는 요청은 필터가 통과시킨다.
 */
class RateLimitFilterTest {

    private val redisRateLimitService = mockk<RedisRateLimitService>()
    private val properties = ClippingMcpServerProperties()
    private val filter = RateLimitFilter(redisRateLimitService, properties)

    @Nested
    inner class `사용자 API 경로` {

        @Test
        fun `사용자가 제한 미만이면 rate limit 서비스에 문의하고 429가 아닌 응답을 낸다`() {
            every { redisRateLimitService.isRateLimited("rl:user:alice", 60, 60L) } returns false
            val exchange = authenticatedExchange(HttpMethod.GET, "/api/user/me", "alice")

            filter.filter(exchange, countingChain()).block()

            // 사용자 키로 정확히 1회 rate limit을 조회한다.
            verify(exactly = 1) {
                redisRateLimitService.isRateLimited("rl:user:alice", 60, 60L)
            }
            exchange.response.statusCode shouldBe null
        }

        @Test
        fun `사용자가 제한 초과면 429로 응답한다`() {
            every { redisRateLimitService.isRateLimited("rl:user:alice", 60, 60L) } returns true
            val exchange = authenticatedExchange(HttpMethod.GET, "/api/user/me", "alice")

            filter.filter(exchange, countingChain()).block()

            exchange.response.statusCode shouldBe HttpStatus.TOO_MANY_REQUESTS
        }

        @Test
        fun `인증 Principal이 없으면 rate limit을 적용하지 않는다`() {
            val exchange = SpringMockExchange.from(
                MockServerHttpRequest.method(HttpMethod.GET, "/api/user/me").build()
            )

            filter.filter(exchange, countingChain()).block()

            verify(exactly = 0) { redisRateLimitService.isRateLimited(any(), any(), any()) }
            exchange.response.statusCode shouldBe null
        }
    }

    @Nested
    inner class `관리자 API 쓰기 경로` {

        @Test
        fun `POST가 제한 미만이면 체인을 통과시킨다`() {
            every { redisRateLimitService.isRateLimited("rl:admin:write:bob", 100, 60L) } returns false
            val exchange = authenticatedExchange(HttpMethod.POST, "/api/admin/personas", "bob")

            filter.filter(exchange, countingChain()).block()

            verify(exactly = 1) {
                redisRateLimitService.isRateLimited("rl:admin:write:bob", 100, 60L)
            }
            exchange.response.statusCode shouldBe null
        }

        @Test
        fun `POST가 제한 초과면 429로 응답한다`() {
            every { redisRateLimitService.isRateLimited("rl:admin:write:bob", 100, 60L) } returns true
            val exchange = authenticatedExchange(HttpMethod.POST, "/api/admin/personas", "bob")

            filter.filter(exchange, countingChain()).block()

            exchange.response.statusCode shouldBe HttpStatus.TOO_MANY_REQUESTS
        }

        @Test
        fun `PUT도 제한 대상이다`() {
            every { redisRateLimitService.isRateLimited("rl:admin:write:bob", 100, 60L) } returns true
            val exchange = authenticatedExchange(HttpMethod.PUT, "/api/admin/personas/p1", "bob")

            filter.filter(exchange, countingChain()).block()

            exchange.response.statusCode shouldBe HttpStatus.TOO_MANY_REQUESTS
        }

        @Test
        fun `PATCH도 제한 대상이다`() {
            every { redisRateLimitService.isRateLimited("rl:admin:write:bob", 100, 60L) } returns true
            val exchange = authenticatedExchange(HttpMethod.PATCH, "/api/admin/personas/p1", "bob")

            filter.filter(exchange, countingChain()).block()

            exchange.response.statusCode shouldBe HttpStatus.TOO_MANY_REQUESTS
        }

        @Test
        fun `DELETE도 제한 대상이다`() {
            every { redisRateLimitService.isRateLimited("rl:admin:write:bob", 100, 60L) } returns true
            val exchange = authenticatedExchange(HttpMethod.DELETE, "/api/admin/personas/p1", "bob")

            filter.filter(exchange, countingChain()).block()

            exchange.response.statusCode shouldBe HttpStatus.TOO_MANY_REQUESTS
        }

        @Test
        fun `인증 Principal이 없으면 anonymous-admin 키로 기록된다`() {
            every {
                redisRateLimitService.isRateLimited(
                    "rl:admin:write:anonymous-admin", 100, 60L
                )
            } returns false
            val exchange = SpringMockExchange.from(
                MockServerHttpRequest.method(HttpMethod.POST, "/api/admin/personas").build()
            )

            filter.filter(exchange, countingChain()).block()

            verify(exactly = 1) {
                redisRateLimitService.isRateLimited("rl:admin:write:anonymous-admin", 100, 60L)
            }
        }
    }

    @Nested
    inner class `관리자 API 읽기 경로` {

        @Test
        fun `GET이 제한 미만이면 읽기 버킷 키로 조회하고 체인을 통과시킨다`() {
            every { redisRateLimitService.isRateLimited("rl:admin:read:bob", 500, 60L) } returns false
            val exchange = authenticatedExchange(HttpMethod.GET, "/api/admin/personas", "bob")

            filter.filter(exchange, countingChain()).block()

            verify(exactly = 1) {
                redisRateLimitService.isRateLimited("rl:admin:read:bob", 500, 60L)
            }
            exchange.response.statusCode shouldBe null
        }

        @Test
        fun `설정된 관리자 읽기 한도를 rate limit 서비스에 전달한다`() {
            val customProperties = ClippingMcpServerProperties(
                rateLimit = RateLimitProperties(maxAdminReadRequestsPerMinute = 1234)
            )
            val customFilter = RateLimitFilter(redisRateLimitService, customProperties)
            every { redisRateLimitService.isRateLimited("rl:admin:read:bob", 1234, 60L) } returns false
            val exchange = authenticatedExchange(HttpMethod.GET, "/api/admin/personas", "bob")

            customFilter.filter(exchange, countingChain()).block()

            verify(exactly = 1) {
                redisRateLimitService.isRateLimited("rl:admin:read:bob", 1234, 60L)
            }
            exchange.response.statusCode shouldBe null
        }

        @Test
        fun `GET이 제한 초과면 429로 응답한다`() {
            every { redisRateLimitService.isRateLimited("rl:admin:read:bob", 500, 60L) } returns true
            val exchange = authenticatedExchange(HttpMethod.GET, "/api/admin/personas", "bob")

            filter.filter(exchange, countingChain()).block()

            exchange.response.statusCode shouldBe HttpStatus.TOO_MANY_REQUESTS
        }

        @Test
        fun `HEAD도 읽기 버킷 대상이다`() {
            every { redisRateLimitService.isRateLimited("rl:admin:read:bob", 500, 60L) } returns true
            val exchange = authenticatedExchange(HttpMethod.HEAD, "/api/admin/personas", "bob")

            filter.filter(exchange, countingChain()).block()

            exchange.response.statusCode shouldBe HttpStatus.TOO_MANY_REQUESTS
        }

        @Test
        fun `OPTIONS도 읽기 버킷 대상이다`() {
            every { redisRateLimitService.isRateLimited("rl:admin:read:bob", 500, 60L) } returns false
            val exchange = authenticatedExchange(HttpMethod.OPTIONS, "/api/admin/personas", "bob")

            filter.filter(exchange, countingChain()).block()

            verify(exactly = 1) {
                redisRateLimitService.isRateLimited("rl:admin:read:bob", 500, 60L)
            }
            exchange.response.statusCode shouldBe null
        }

        @Test
        fun `읽기 버킷과 쓰기 버킷은 키가 분리돼 서로 영향을 주지 않는다`() {
            // given: 쓰기 버킷은 초과, 읽기 버킷은 여유 — 각자 독립된 키를 사용해야 한다.
            every { redisRateLimitService.isRateLimited("rl:admin:write:bob", 100, 60L) } returns true
            every { redisRateLimitService.isRateLimited("rl:admin:read:bob", 500, 60L) } returns false

            val writeExchange = authenticatedExchange(HttpMethod.POST, "/api/admin/personas", "bob")
            val readExchange = authenticatedExchange(HttpMethod.GET, "/api/admin/personas", "bob")

            filter.filter(writeExchange, countingChain()).block()
            filter.filter(readExchange, countingChain()).block()

            // 쓰기는 429, 읽기는 통과 — 같은 actor라도 버킷이 섞이면 안 된다.
            writeExchange.response.statusCode shouldBe HttpStatus.TOO_MANY_REQUESTS
            readExchange.response.statusCode shouldBe null
            verify(exactly = 1) { redisRateLimitService.isRateLimited("rl:admin:write:bob", 100, 60L) }
            verify(exactly = 1) { redisRateLimitService.isRateLimited("rl:admin:read:bob", 500, 60L) }
        }

        @Test
        fun `인증 Principal이 없으면 anonymous-admin 읽기 키로 기록된다`() {
            every {
                redisRateLimitService.isRateLimited(
                    "rl:admin:read:anonymous-admin", 500, 60L
                )
            } returns false
            val exchange = SpringMockExchange.from(
                MockServerHttpRequest.method(HttpMethod.GET, "/api/admin/personas").build()
            )

            filter.filter(exchange, countingChain()).block()

            verify(exactly = 1) {
                redisRateLimitService.isRateLimited("rl:admin:read:anonymous-admin", 500, 60L)
            }
        }
    }

    @Nested
    inner class `화이트리스트 경로` {

        @Test
        fun `actuator 하위 경로는 rate limit 대상이 아니다`() {
            val exchange = authenticatedExchange(HttpMethod.GET, "/actuator/health", "bob")

            filter.filter(exchange, countingChain()).block()

            verify(exactly = 0) { redisRateLimitService.isRateLimited(any(), any(), any()) }
            exchange.response.statusCode shouldBe null
        }

        @Test
        fun `actuator prometheus scrape도 rate limit 대상이 아니다`() {
            val exchange = authenticatedExchange(HttpMethod.GET, "/actuator/prometheus", "bob")

            filter.filter(exchange, countingChain()).block()

            verify(exactly = 0) { redisRateLimitService.isRateLimited(any(), any(), any()) }
            exchange.response.statusCode shouldBe null
        }

        @Test
        fun `sse 엔드포인트는 rate limit 대상이 아니다`() {
            val exchange = authenticatedExchange(HttpMethod.GET, "/sse", "bob")

            filter.filter(exchange, countingChain()).block()

            verify(exactly = 0) { redisRateLimitService.isRateLimited(any(), any(), any()) }
            exchange.response.statusCode shouldBe null
        }

        @Test
        fun `mcp message 엔드포인트는 rate limit 대상이 아니다`() {
            // McpRateLimiter가 별도로 제한하므로 본 필터는 통과시켜야 한다.
            val exchange = authenticatedExchange(HttpMethod.POST, "/mcp/message", "bob")

            filter.filter(exchange, countingChain()).block()

            verify(exactly = 0) { redisRateLimitService.isRateLimited(any(), any(), any()) }
            exchange.response.statusCode shouldBe null
        }
    }

    @Nested
    inner class `비대상 경로` {

        @Test
        fun `기타 경로는 rate limit을 적용하지 않는다`() {
            val exchange = authenticatedExchange(HttpMethod.GET, "/api-docs", "bob")

            filter.filter(exchange, countingChain()).block()

            verify(exactly = 0) { redisRateLimitService.isRateLimited(any(), any(), any()) }
            exchange.response.statusCode shouldBe null
        }
    }

    @Nested
    inner class `익명 공개 경로 IP rate limit` {

        @Test
        fun `departments tree 는 X-Forwarded-For 최좌측 IP 로 버킷팅된다`() {
            every {
                redisRateLimitService.isRateLimited("rl:public:ip:203.0.113.5", 60, 60L)
            } returns false
            val request = MockServerHttpRequest.method(HttpMethod.GET, "/api/public/departments/tree")
                .header("X-Forwarded-For", "203.0.113.5, 10.0.0.1")
                .build()
            val exchange: ServerWebExchange = SpringMockExchange.from(request)
            val chain = countingChain()

            filter.filter(exchange, chain).block()

            verify(exactly = 1) {
                redisRateLimitService.isRateLimited("rl:public:ip:203.0.113.5", 60, 60L)
            }
            chain.invocations shouldBe 1
        }

        @Test
        fun `X-Forwarded-For 없으면 X-Real-IP 를 우선 사용한다`() {
            every {
                redisRateLimitService.isRateLimited("rl:public:ip:198.51.100.7", 60, 60L)
            } returns false
            val request = MockServerHttpRequest.method(HttpMethod.GET, "/api/public/departments/tree")
                .header("X-Real-IP", "198.51.100.7")
                .build()
            val exchange: ServerWebExchange = SpringMockExchange.from(request)

            filter.filter(exchange, countingChain()).block()

            verify(exactly = 1) {
                redisRateLimitService.isRateLimited("rl:public:ip:198.51.100.7", 60, 60L)
            }
        }

        @Test
        fun `limit 초과 시 429 를 반환하고 체인은 호출되지 않는다`() {
            every {
                redisRateLimitService.isRateLimited("rl:public:ip:203.0.113.5", 60, 60L)
            } returns true
            val request = MockServerHttpRequest.method(HttpMethod.GET, "/api/public/departments/tree")
                .header("X-Forwarded-For", "203.0.113.5")
                .build()
            val exchange: ServerWebExchange = SpringMockExchange.from(request)
            val chain = countingChain()

            filter.filter(exchange, chain).block()

            exchange.response.statusCode shouldBe HttpStatus.TOO_MANY_REQUESTS
            chain.invocations shouldBe 0
        }

        @Test
        fun `리스트 외 공개 경로는 IP 제한 대상이 아니다`() {
            val request = MockServerHttpRequest.method(HttpMethod.GET, "/api/public/user/auth/signup").build()
            val exchange: ServerWebExchange = SpringMockExchange.from(request)

            filter.filter(exchange, countingChain()).block()

            verify(exactly = 0) { redisRateLimitService.isRateLimited(any(), any(), any()) }
            exchange.response.statusCode shouldBe null
        }
    }

    private fun authenticatedExchange(
        method: HttpMethod,
        path: String,
        username: String
    ): ServerWebExchange {
        val request = MockServerHttpRequest.method(method, path).build()
        val exchange: ServerWebExchange = SpringMockExchange.from(request)
        val principal: Principal = Principal { username }
        // SpringMockExchange의 getPrincipal은 기본 빈 Mono를 반환하므로,
        // Principal 주입을 위해 by 위임 패턴으로 래핑한다.
        return object : ServerWebExchange by exchange {
            @Suppress("UNCHECKED_CAST")
            override fun <T : Principal> getPrincipal(): Mono<T> = Mono.just(principal) as Mono<T>
        }
    }

    private class CountingChain : WebFilterChain {
        var invocations: Int = 0
            private set

        override fun filter(exchange: ServerWebExchange): Mono<Void> {
            invocations++
            return Mono.empty()
        }
    }

    private fun countingChain(): CountingChain = CountingChain()
}
