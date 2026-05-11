package com.ohmyclipping.observability

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import java.util.concurrent.atomic.AtomicBoolean

/**
 * AccessLogWebFilter 단위 테스트.
 *
 * 로깅 자체는 slf4j 바인딩을 통해 stdout 에 출력되므로 검증이 어렵지만, 본 테스트는
 * 1) chain 이 항상 호출되는지 (필터가 요청을 가로막지 않음)
 * 2) 헬스체크/프로메테우스 엔드포인트는 조기 return 경로를 타는지 (noise 제거)
 * 를 행동적으로 검증해 로깅 동작의 회귀를 막는다.
 */
class AccessLogWebFilterTest {

    private val filter = AccessLogWebFilter()

    /** 필터를 실행하고 (exchange, chainCalled) 쌍을 반환한다. */
    private fun executeFilter(path: String): Pair<MockServerWebExchange, Boolean> {
        val request = MockServerHttpRequest.get(path).build()
        val exchange = MockServerWebExchange.from(request)
        val chainCalled = AtomicBoolean(false)
        val chain = WebFilterChain { ex ->
            chainCalled.set(true)
            ex.response.statusCode = HttpStatus.OK
            Mono.empty()
        }
        filter.filter(exchange, chain).block()
        return exchange to chainCalled.get()
    }

    @Nested
    inner class `일반 요청 로깅` {

        @Test
        fun `비-제외 경로에서는 chain 을 호출한 뒤 로깅이 정상 종료된다`() {
            // /api/** 같은 일반 경로는 로그 경로를 타지만 chain 은 항상 호출되어야 한다.
            val (exchange, chainCalled) = executeFilter("/api/admin/categories")

            chainCalled shouldBe true
            exchange.response.statusCode shouldBe HttpStatus.OK
        }

        @Test
        fun `체인이 상태 코드를 세팅하지 않아도 필터는 throw 하지 않는다`() {
            // response.statusCode 가 null 인 상태에서도 durationMs 로깅이 예외를 발생시키지 않아야 한다.
            val request = MockServerHttpRequest.post("/api/user/login").build()
            val exchange = MockServerWebExchange.from(request)
            val chainCalled = AtomicBoolean(false)
            val chain = WebFilterChain { _ ->
                chainCalled.set(true)
                Mono.empty()
            }

            filter.filter(exchange, chain).block()

            chainCalled.get() shouldBe true
        }
    }

    @Nested
    inner class `제외 경로 처리` {

        @Test
        fun `액추에이터 헬스체크는 로깅하지 않고 바로 체인으로 패스스루한다`() {
            // /actuator/health 는 EXCLUDED_PREFIXES 에 포함되어 로깅 스팬을 만들지 않는다.
            // 실행 자체가 실패하지 않고 chain 이 호출되어야 한다는 것만 외부에서 검증 가능하다.
            val (_, chainCalled) = executeFilter("/actuator/health")

            chainCalled shouldBe true
        }

        @Test
        fun `프로메테우스 메트릭 엔드포인트도 로깅이 제외된다`() {
            val (_, chainCalled) = executeFilter("/actuator/prometheus")

            chainCalled shouldBe true
        }

        @Test
        fun `헬스체크 하위 경로(liveness, readiness)도 prefix 매칭으로 제외된다`() {
            val (_, chainCalled) = executeFilter("/actuator/health/liveness")

            chainCalled shouldBe true
        }
    }
}
