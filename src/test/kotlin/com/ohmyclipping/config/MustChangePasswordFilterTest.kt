package com.ohmyclipping.config

import com.ohmyclipping.model.AccountApprovalStatus
import com.ohmyclipping.model.AccountRole
import com.ohmyclipping.model.AdminUser
import com.ohmyclipping.store.AdminUserStore
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.security.core.context.SecurityContextImpl
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import java.util.concurrent.atomic.AtomicBoolean

class MustChangePasswordFilterTest {

    private val adminUserStore = mockk<AdminUserStore>()
    private val sut = MustChangePasswordFilter(adminUserStore)

    /** 테스트용 사용자를 생성한다. */
    private fun testUser(
        username: String = "testuser",
        mustChangePassword: Boolean = false
    ) = AdminUser(
        id = "u1",
        username = username,
        passwordHash = "hashed",
        role = AccountRole.USER,
        approvalStatus = AccountApprovalStatus.APPROVED,
        mustChangePassword = mustChangePassword
    )

    /**
     * 필터를 실행하고 (exchange, chainCalled) 쌍을 반환한다.
     * username이 null이면 인증 컨텍스트 없이 실행한다.
     */
    private fun executeFilter(
        method: HttpMethod,
        path: String,
        username: String? = null
    ): Pair<MockServerWebExchange, Boolean> {
        val request = MockServerHttpRequest.method(method, path).build()
        val exchange = MockServerWebExchange.from(request)
        val chainCalled = AtomicBoolean(false)

        val chain = WebFilterChain { _ ->
            chainCalled.set(true)
            Mono.empty()
        }

        if (username != null) {
            val auth = UsernamePasswordAuthenticationToken(
                username, "password",
                listOf(SimpleGrantedAuthority("ROLE_USER"))
            )
            val securityContext = SecurityContextImpl(auth)

            // contextWrite가 확실히 전파되도록 Mono.defer로 감싼다.
            Mono.defer { sut.filter(exchange, chain) }
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(securityContext)))
                .block()
        } else {
            sut.filter(exchange, chain).block()
        }

        return exchange to chainCalled.get()
    }

    @Nested
    inner class `mustChangePassword가 true인 사용자` {

        @Test
        fun `POST clipping-requests 요청을 403으로 차단한다`() {
            every { adminUserStore.findByUsername("testuser") } returns testUser(mustChangePassword = true)

            val (exchange, chainCalled) = executeFilter(
                HttpMethod.POST, "/api/user/clipping-requests", "testuser"
            )

            exchange.response.statusCode shouldBe HttpStatus.FORBIDDEN
            chainCalled shouldBe false
        }

        @Test
        fun `PUT clipping-requests 요청을 403으로 차단한다`() {
            every { adminUserStore.findByUsername("testuser") } returns testUser(mustChangePassword = true)

            val (exchange, chainCalled) = executeFilter(
                HttpMethod.PUT, "/api/user/clipping-requests/123", "testuser"
            )

            exchange.response.statusCode shouldBe HttpStatus.FORBIDDEN
            chainCalled shouldBe false
        }

        @Test
        fun `DELETE clipping-requests 요청을 403으로 차단한다`() {
            every { adminUserStore.findByUsername("testuser") } returns testUser(mustChangePassword = true)

            val (exchange, chainCalled) = executeFilter(
                HttpMethod.DELETE, "/api/user/clipping-requests/123", "testuser"
            )

            exchange.response.statusCode shouldBe HttpStatus.FORBIDDEN
            chainCalled shouldBe false
        }

        @Test
        fun `POST account withdraw 요청을 403으로 차단한다`() {
            every { adminUserStore.findByUsername("testuser") } returns testUser(mustChangePassword = true)

            val (exchange, chainCalled) = executeFilter(
                HttpMethod.POST, "/api/user/account/withdraw", "testuser"
            )

            exchange.response.statusCode shouldBe HttpStatus.FORBIDDEN
            chainCalled shouldBe false
        }
    }

    @Nested
    inner class `mustChangePassword가 false인 사용자` {

        @Test
        fun `POST clipping-requests 요청을 통과시킨다`() {
            every { adminUserStore.findByUsername("testuser") } returns testUser(mustChangePassword = false)

            val (_, chainCalled) = executeFilter(
                HttpMethod.POST, "/api/user/clipping-requests", "testuser"
            )

            chainCalled shouldBe true
        }

        @Test
        fun `POST account withdraw 요청을 통과시킨다`() {
            every { adminUserStore.findByUsername("testuser") } returns testUser(mustChangePassword = false)

            val (_, chainCalled) = executeFilter(
                HttpMethod.POST, "/api/user/account/withdraw", "testuser"
            )

            chainCalled shouldBe true
        }
    }

    @Nested
    inner class `차단 대상이 아닌 경로` {

        @Test
        fun `GET me 요청은 mustChangePassword=true여도 통과한다`() {
            val (_, chainCalled) = executeFilter(
                HttpMethod.GET, "/api/me", "testuser"
            )

            chainCalled shouldBe true
        }

        @Test
        fun `POST change-password 요청은 mustChangePassword=true여도 통과한다`() {
            val (_, chainCalled) = executeFilter(
                HttpMethod.POST, "/api/user/account/change-password", "testuser"
            )

            chainCalled shouldBe true
        }

        @Test
        fun `GET clipping-requests 조회는 mustChangePassword=true여도 통과한다`() {
            val (_, chainCalled) = executeFilter(
                HttpMethod.GET, "/api/user/clipping-requests", "testuser"
            )

            chainCalled shouldBe true
        }
    }

    @Nested
    inner class `미인증 요청` {

        @Test
        fun `인증 정보 없이도 필터를 통과한다`() {
            val (_, chainCalled) = executeFilter(
                HttpMethod.POST, "/api/user/clipping-requests", username = null
            )

            chainCalled shouldBe true
        }
    }
}
