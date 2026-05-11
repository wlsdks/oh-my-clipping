package com.clipping.mcpserver.config

import com.clipping.mcpserver.store.AdminUserStore
import com.clipping.mcpserver.model.AccountApprovalStatus
import com.clipping.mcpserver.model.AccountRole
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.DisabledException
import org.springframework.security.authentication.LockedException
import org.springframework.security.authentication.ReactiveAuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.SecurityWebFiltersOrder
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.authentication.UserDetailsRepositoryReactiveAuthenticationManager
import org.springframework.security.core.userdetails.ReactiveUserDetailsService
import org.springframework.security.core.userdetails.User
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.server.DelegatingServerAuthenticationEntryPoint
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.security.web.server.authentication.AuthenticationWebFilter
import org.springframework.security.web.server.authentication.HttpStatusServerEntryPoint
import org.springframework.security.web.server.authentication.RedirectServerAuthenticationEntryPoint
import org.springframework.security.web.server.authentication.RedirectServerAuthenticationFailureHandler
import org.springframework.security.web.server.authentication.RedirectServerAuthenticationSuccessHandler
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter
import org.springframework.security.web.server.authentication.ServerAuthenticationSuccessHandler
import org.springframework.security.web.server.authentication.logout.ServerLogoutSuccessHandler
import org.springframework.security.web.server.authentication.logout.RedirectServerLogoutSuccessHandler
import org.springframework.security.web.server.authorization.HttpStatusServerAccessDeniedHandler
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository
import org.springframework.security.web.server.context.WebSessionServerSecurityContextRepository
import org.springframework.security.web.server.csrf.CookieServerCsrfTokenRepository
import org.springframework.security.web.server.csrf.CsrfWebFilter
import org.springframework.security.web.server.csrf.ServerCsrfTokenRequestAttributeHandler
import org.springframework.security.web.server.header.ReferrerPolicyServerHttpHeadersWriter
import org.springframework.security.web.server.util.matcher.AndServerWebExchangeMatcher
import org.springframework.security.web.server.util.matcher.NegatedServerWebExchangeMatcher
import org.springframework.security.web.server.util.matcher.OrServerWebExchangeMatcher
import org.springframework.security.web.server.util.matcher.PathPatternParserServerWebExchangeMatcher
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
class SecurityConfig(
    private val securityProperties: SecurityProperties,
    private val adminUserStore: AdminUserStore,
    private val maintenanceModeWebFilter: MaintenanceModeWebFilter,
    private val mustChangePasswordFilter: MustChangePasswordFilter,
    private val accountLockoutService: AccountLockoutService
) {

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun reactiveUserDetailsService(): ReactiveUserDetailsService =
        ReactiveUserDetailsService { username ->
            val normalizedUsername = username.trim().lowercase()
            // 계정 잠금 상태를 먼저 확인한다 — DB 조회 전에 차단하여 부하를 줄인다
            if (accountLockoutService.isLocked(normalizedUsername)) {
                return@ReactiveUserDetailsService Mono.error(
                    LockedException("account_locked")
                )
            }
            Mono.fromCallable { adminUserStore.findByUsername(normalizedUsername) }
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap { user ->
                    when {
                        user == null -> Mono.empty()
                        user.role == AccountRole.USER && user.approvalStatus == AccountApprovalStatus.PENDING ->
                            Mono.error(DisabledException("account_pending_approval"))
                        user.role == AccountRole.USER && user.approvalStatus == AccountApprovalStatus.REJECTED ->
                            Mono.error(DisabledException("account_rejected"))
                        !user.isActive -> Mono.error(DisabledException("account_disabled"))
                        else -> Mono.just(
                            User.withUsername(user.username)
                                .password(user.passwordHash)
                                .roles(user.role.name)
                                .build()
                        )
                    }
                }
        }

    /**
     * 폼 로그인용 인증 매니저.
     * 기본 UserDetails 인증에 계정 잠금(lockout) 감지와 성공/실패 기록을 추가한다.
     */
    @Bean
    fun formLoginAuthenticationManager(
        userDetailsService: ReactiveUserDetailsService,
        passwordEncoder: PasswordEncoder
    ): ReactiveAuthenticationManager {
        val delegate = UserDetailsRepositoryReactiveAuthenticationManager(userDetailsService).apply {
            setPasswordEncoder(passwordEncoder)
        }
        return ReactiveAuthenticationManager { authentication ->
            val username = authentication.name?.trim()?.lowercase().orEmpty()
            delegate.authenticate(authentication)
                .doOnNext { accountLockoutService.recordSuccess(username) }
                .doOnError { e ->
                    // BadCredentials(비밀번호 불일치)만 실패 카운트에 반영한다
                    if (e is BadCredentialsException) {
                        accountLockoutService.recordFailure(username)
                    }
                }
        }
    }

    @Bean
    fun securityWebFilterChain(
        http: ServerHttpSecurity,
        formLoginAuthenticationManager: ReactiveAuthenticationManager
    ): SecurityWebFilterChain {
        val bearerAuthManager = ReactiveAuthenticationManager { authentication ->
            val providedToken = authentication.credentials?.toString().orEmpty()
            // 상수 시간 비교로 타이밍 공격을 방지한다.
            val tokenMatch = securityProperties.adminToken.isNotBlank() && MessageDigest.isEqual(
                providedToken.toByteArray(StandardCharsets.UTF_8),
                securityProperties.adminToken.toByteArray(StandardCharsets.UTF_8)
            )
            if (tokenMatch) {
                Mono.just(
                    UsernamePasswordAuthenticationToken(
                        "admin-api",
                        providedToken,
                        listOf(SimpleGrantedAuthority("ROLE_ADMIN"))
                    )
                )
            } else {
                Mono.error(BadCredentialsException("Invalid admin token"))
            }
        }

        val bearerAuthFilter = AuthenticationWebFilter(bearerAuthManager).apply {
            setServerAuthenticationConverter(bearerTokenConverter())
            setSecurityContextRepository(NoOpServerSecurityContextRepository.getInstance())
            setRequiresAuthenticationMatcher(ServerWebExchangeMatcher { exchange ->
                // Bearer 토큰 인증은 관리자 API와 클라이언트 에러 보고 엔드포인트에서 허용한다.
                val adminMatcher = PathPatternParserServerWebExchangeMatcher("/api/admin/**")
                val clientErrorMatcher = PathPatternParserServerWebExchangeMatcher("/api/client-errors")
                val combined = OrServerWebExchangeMatcher(adminMatcher, clientErrorMatcher)
                combined.matches(exchange).flatMap { result ->
                    if (!result.isMatch) {
                        ServerWebExchangeMatcher.MatchResult.notMatch()
                    } else {
                        val authHeader = exchange.request.headers.getFirst(HttpHeaders.AUTHORIZATION).orEmpty()
                        if (authHeader.startsWith("Bearer ", ignoreCase = true)) {
                            ServerWebExchangeMatcher.MatchResult.match()
                        } else {
                            ServerWebExchangeMatcher.MatchResult.notMatch()
                        }
                    }
                }
            })
        }

        val redirectSuccessHandler = ServerAuthenticationSuccessHandler { webFilterExchange, authentication ->
            val isAdmin = authentication.authorities.any { it.authority == "ROLE_ADMIN" }
            val target = if (isAdmin) "/admin" else "/user"
            val exchange = webFilterExchange.exchange
            // 세션 고정 공격 방지: 기존 세션 ID를 변경하여 세션 고정 공격을 차단한다
            exchange.session.flatMap { session ->
                session.changeSessionId()
            }.then(Mono.defer {
                exchange.response.statusCode = HttpStatus.FOUND
                exchange.response.headers.location = URI.create(target)
                exchange.response.setComplete()
            })
        }

        val redirectLogoutSuccessHandler = RedirectServerLogoutSuccessHandler().apply {
            setLogoutSuccessUrl(URI.create("/login?logout=1"))
        }
        val logoutSuccessHandler = ServerLogoutSuccessHandler { webFilterExchange, authentication ->
            val apiLogout = webFilterExchange.exchange.request.headers.getFirst("X-Logout-Mode") == "api"
            if (apiLogout) {
                webFilterExchange.exchange.response.statusCode = HttpStatus.NO_CONTENT
                webFilterExchange.exchange.response.setComplete()
            } else {
                redirectLogoutSuccessHandler.onLogoutSuccess(webFilterExchange, authentication)
            }
        }

        val authEntryPoint = DelegatingServerAuthenticationEntryPoint(
            listOf(
                DelegatingServerAuthenticationEntryPoint.DelegateEntry(
                    PathPatternParserServerWebExchangeMatcher("/api/**"),
                    HttpStatusServerEntryPoint(HttpStatus.UNAUTHORIZED)
                ),
                DelegatingServerAuthenticationEntryPoint.DelegateEntry(
                    OrServerWebExchangeMatcher(
                        PathPatternParserServerWebExchangeMatcher("/admin/**"),
                        PathPatternParserServerWebExchangeMatcher("/user/**")
                    ),
                    RedirectServerAuthenticationEntryPoint("/login")
                )
            )
        ).apply {
            setDefaultEntryPoint(HttpStatusServerEntryPoint(HttpStatus.UNAUTHORIZED))
        }

        // SPA용 CSRF 설정: 프론트가 XSRF-TOKEN 쿠키를 읽어 X-XSRF-TOKEN 헤더로 전송한다.
        val csrfTokenRepository = CookieServerCsrfTokenRepository.withHttpOnlyFalse()
        val csrfRequestHandler = ServerCsrfTokenRequestAttributeHandler()

        // 기본 CSRF 매처(비안전 메서드만)에 경로 제외를 AND 합성한다.
        val defaultCsrfMatcher = CsrfWebFilter.DEFAULT_CSRF_MATCHER
        // API 경로와 서버 렌더링 폼은 CSRF에서 제외한다.
        // SPA API 호출은 SameSite=lax 세션 쿠키 + CORS로 보호된다.
        val pathExclusions = NegatedServerWebExchangeMatcher(
            OrServerWebExchangeMatcher(
                // 모든 API 경로: SameSite=lax + CORS + 커스텀 헤더(Content-Type: JSON)로 보호
                PathPatternParserServerWebExchangeMatcher("/api/**"),
                // 서버 렌더링 폼 로그인/회원가입: 세션 기반 보호
                PathPatternParserServerWebExchangeMatcher("/login", HttpMethod.POST),
                PathPatternParserServerWebExchangeMatcher("/signup", HttpMethod.POST),
                PathPatternParserServerWebExchangeMatcher("/admin/signup", HttpMethod.POST),
                PathPatternParserServerWebExchangeMatcher("/user/signup", HttpMethod.POST),
                PathPatternParserServerWebExchangeMatcher("/logout", HttpMethod.POST),
                // MCP 프로토콜: 외부 MCP 클라이언트가 JSON-RPC를 POST로 전송한다.
                // /mcp: STREAMABLE HTTP transport (MCP 2025-03-26 spec, R_2026-04 추가)
                PathPatternParserServerWebExchangeMatcher("/mcp/message", HttpMethod.POST),
                PathPatternParserServerWebExchangeMatcher("/mcp", HttpMethod.POST),
                PathPatternParserServerWebExchangeMatcher("/mcp", HttpMethod.GET),
                PathPatternParserServerWebExchangeMatcher("/mcp", HttpMethod.DELETE)
            )
        )
        val csrfMatcher = AndServerWebExchangeMatcher(defaultCsrfMatcher, pathExclusions)

        return http
            .csrf { csrf ->
                csrf.csrfTokenRepository(csrfTokenRepository)
                csrf.csrfTokenRequestHandler(csrfRequestHandler)
                // Slack 웹훅과 Bearer 토큰 인증 경로는 CSRF에서 제외한다.
                csrf.requireCsrfProtectionMatcher(csrfMatcher)
            }
            .cors { }
            .headers { headers ->
                headers.contentTypeOptions {}
                headers.frameOptions { it.mode(org.springframework.security.web.server.header.XFrameOptionsServerHttpHeadersWriter.Mode.DENY) }
                headers.referrerPolicy {
                    it.policy(
                        ReferrerPolicyServerHttpHeadersWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN
                    )
                }
                headers.permissionsPolicy { it.policy("camera=(), microphone=(), geolocation=()") }
            }
            .httpBasic { it.disable() }
            .securityContextRepository(WebSessionServerSecurityContextRepository())
            .exceptionHandling { handling ->
                handling.accessDeniedHandler(HttpStatusServerAccessDeniedHandler(HttpStatus.FORBIDDEN))
                handling.authenticationEntryPoint(authEntryPoint)
            }
            .formLogin { form ->
                form.loginPage("/login")
                form.authenticationManager(formLoginAuthenticationManager)
                form.requiresAuthenticationMatcher(
                    PathPatternParserServerWebExchangeMatcher("/login", HttpMethod.POST)
                )
                form.authenticationSuccessHandler(redirectSuccessHandler)
                form.authenticationFailureHandler { webFilterExchange, exception ->
                    // 통합 로그인(/login)이므로 경로 기반 분기 대신 예외 메시지로 리다이렉트 대상을 결정한다
                    val target = when {
                        exception is LockedException && exception.message == "account_locked" ->
                            "/login?locked=1"
                        (exception as? DisabledException)?.message == "account_pending_approval" ->
                            "/login?pending_approval=1"
                        (exception as? DisabledException)?.message == "account_rejected" ->
                            "/login?rejected=1"
                        else -> "/login?error=1"
                    }
                    RedirectServerAuthenticationFailureHandler(target)
                        .onAuthenticationFailure(webFilterExchange, exception)
                }
            }
            .logout { logout ->
                logout.logoutUrl("/logout")
                logout.logoutSuccessHandler(logoutSuccessHandler)
            }
            .authorizeExchange { exchanges ->
                exchanges.pathMatchers(HttpMethod.OPTIONS).permitAll()
                exchanges.pathMatchers(HttpMethod.GET, "/login", "/signup", "/admin/login", "/admin/signup", "/user/login", "/user/signup").permitAll()
                exchanges.pathMatchers(HttpMethod.POST, "/login", "/signup", "/admin/signup", "/user/signup").permitAll()
                exchanges.pathMatchers("/favicon.ico").permitAll()
                exchanges.pathMatchers("/api/public/**").permitAll()
                // 기사 클릭 추적: Slack 사용자는 미인증이므로 인증 없이 접근을 허용한다.
                exchanges.pathMatchers("/api/track/**").permitAll()
                exchanges.pathMatchers("/api/admin/**").hasRole("ADMIN")
                exchanges.pathMatchers("/api/user/**").hasAnyRole("USER", "ADMIN")
                // 클라이언트 에러 보고: 관리자/사용자 모두 ErrorBoundary에서 호출한다.
                exchanges.pathMatchers("/api/client-errors").hasAnyRole("USER", "ADMIN")
                exchanges.pathMatchers("/admin/**").hasRole("ADMIN")
                exchanges.pathMatchers("/user/**").hasAnyRole("USER", "ADMIN")
                // /api/me는 인증된 사용자만 접근 가능하다.
                exchanges.pathMatchers("/api/me").authenticated()
                // Slack 웹훅: CSRF 제외 + 서명 검증 필터에서 인증한다.
                exchanges.pathMatchers("/api/slack/**").permitAll()
                // MCP 엔드포인트: arc-reactor 등 외부 MCP 클라이언트가 접근한다.
                // R_2026-04: STREAMABLE transport 의 /mcp 추가 (MCP 2025-03-26 spec).
                exchanges.pathMatchers("/sse", "/mcp/message", "/mcp").permitAll()
                // Actuator: health만 공개, 나머지(metrics/prometheus)는 관리자만
                exchanges.pathMatchers("/actuator/health", "/actuator/health/**").permitAll()
                exchanges.pathMatchers("/actuator/**").hasRole("ADMIN")
                // 정적 리소스와 SPA 폴백은 인증 없이 접근을 허용한다.
                exchanges.pathMatchers(
                    "/", "/index.html", "/assets/**", "/*.js", "/*.css", "/*.ico",
                    "/swagger-ui.html", "/swagger-ui/**", "/api-docs", "/api-docs/**"
                ).permitAll()
                exchanges.anyExchange().authenticated()
            }
            .addFilterAt(bearerAuthFilter, SecurityWebFiltersOrder.AUTHENTICATION)
            .addFilterAfter(maintenanceModeWebFilter, SecurityWebFiltersOrder.AUTHORIZATION)
            .addFilterAfter(mustChangePasswordFilter, SecurityWebFiltersOrder.AUTHORIZATION)
            .build()
    }

    private fun bearerTokenConverter(): ServerAuthenticationConverter = ServerAuthenticationConverter { exchange ->
        val authHeader = exchange.request.headers.getFirst(HttpHeaders.AUTHORIZATION)
            ?: return@ServerAuthenticationConverter Mono.empty()
        if (!authHeader.startsWith("Bearer ", ignoreCase = true)) {
            return@ServerAuthenticationConverter Mono.empty()
        }

        val token = authHeader.substringAfter("Bearer ").trim()
        if (token.isBlank()) return@ServerAuthenticationConverter Mono.empty()

        Mono.just(UsernamePasswordAuthenticationToken(token, token))
    }
}
