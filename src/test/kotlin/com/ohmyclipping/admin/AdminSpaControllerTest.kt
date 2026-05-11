package com.ohmyclipping.admin

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class AdminSpaControllerTest {

    @Autowired
    lateinit var webClient: WebTestClient

    @Test
    fun `root path should serve spa index`() {
        webClient.get().uri("/")
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML)
            .expectBody(String::class.java)
            .value { body ->
                assert(body.contains("<div id=\"root\"></div>"))
            }
    }

    @Test
    fun `unified login page should serve spa index`() {
        webClient.get().uri("/login")
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML)
            .expectBody(String::class.java)
            .value { body ->
                assert(body.contains("<div id=\"root\"></div>"))
            }
    }

    @Test
    fun `admin login page should serve spa index`() {
        webClient.get().uri("/admin/login")
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML)
            .expectBody(String::class.java)
            .value { body ->
                assert(body.contains("<div id=\"root\"></div>"))
            }
    }

    @Test
    fun `signup page should serve spa index`() {
        webClient.get().uri("/admin/signup")
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML)
            .expectBody(String::class.java)
            .value { body ->
                assert(body.contains("<div id=\"root\"></div>"))
            }
    }

    @Test
    fun `user login page should serve spa index`() {
        webClient.get().uri("/user/login")
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML)
            .expectBody(String::class.java)
            .value { body ->
                assert(body.contains("<div id=\"root\"></div>"))
            }
    }

    @Test
    fun `user signup page should serve spa index`() {
        webClient.get().uri("/user/signup")
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML)
            .expectBody(String::class.java)
            .value { body ->
                assert(body.contains("<div id=\"root\"></div>"))
            }
    }

    @Test
    fun `spa index should use built assets without external runtime cdn`() {
        webClient.get().uri("/admin/login")
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML)
            .expectBody(String::class.java)
            .value { body ->
                assert(body.contains("/assets/"))
                assert(!body.contains("https://unpkg.com/react"))
                assert(!body.contains("@babel/standalone"))
                assert(!body.contains("text/babel"))
            }
    }

    @Test
    fun `public signup availability endpoint should be accessible`() {
        webClient.get().uri("/api/public/admin/auth/signup-availability")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.allowed").isBoolean
            .jsonPath("$.reason").isNotEmpty
            .jsonPath("$.message").isNotEmpty
    }

    @Test
    fun `public user signup availability endpoint should be accessible`() {
        webClient.get().uri("/api/public/user/auth/signup-availability")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.allowed").isBoolean
            .jsonPath("$.reason").isNotEmpty
            .jsonPath("$.message").isNotEmpty
    }
}
