package com.ohmyclipping.admin

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient

/**
 * 로컬 profile이 아닐 때 개발용 공개 API가 노출되지 않는지 검증합니다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class LocalDevPublicControllerDisabledIntegrationTest {

    @Autowired
    lateinit var webClient: WebTestClient

    @Test
    fun `dev login shortcuts should not exist outside local profile`() {
        webClient.get().uri("/api/public/dev/login-shortcuts")
            .exchange()
            .expectStatus().isNotFound
    }
}

