package com.ohmyclipping.admin

import com.ohmyclipping.service.LocalDevSupportService
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.reactive.function.BodyInserters

/**
 * 로컬 시드 기준 사용자 핵심 여정이 실제 API에서 끊김 없이 동작하는지 검증한다.
 * 히스토리/상세/북마크, 발송 스케줄, 오늘 브리핑, 내 정보와 권한 경계를 함께 확인한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test", "local")
class UserExperienceScenarioIntegrationTest {

    @Autowired
    lateinit var webClient: WebTestClient

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Autowired
    lateinit var localDevSupportService: LocalDevSupportService

    private val mapper = jacksonObjectMapper()

    @BeforeEach
    fun bootstrap() {
        // 로컬 시드를 매번 다시 맞춰 테스트 간 상태 오염을 제거한다.
        localDevSupportService.bootstrap()
        // 북마크와 개인 스케줄은 로컬 시드가 직접 재설정하지 않으므로 테스트 전에 정리한다.
        jdbc.update(
            """
            DELETE FROM bookmarked_articles
            WHERE user_id IN (
                SELECT id
                FROM admin_users
                WHERE username IN ('dev.user@clipping.local', 'dev.user.fresh@clipping.local')
            )
            """.trimIndent()
        )
        jdbc.update(
            """
            DELETE FROM user_delivery_schedules
            WHERE user_id IN (
                SELECT id
                FROM admin_users
                WHERE username IN ('dev.user@clipping.local', 'dev.user.fresh@clipping.local')
            )
            """.trimIndent()
        )
    }

    @Test
    fun `seeded user should browse history detail and toggle bookmark end to end`() {
        val session = login("dev.user@clipping.local")
        val userId = requireUserId("dev.user@clipping.local")

        val history = getJson("/api/user/history/articles?page=0&size=20", session)
        history["totalCount"].asInt() shouldBeGreaterThan 0

        val firstItem = history["items"].first()
        val summaryId = firstItem["id"].asText()

        webClient.get().uri("/api/user/history/articles/$summaryId")
            .cookie("SESSION", session)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.id").isEqualTo(summaryId)
            .jsonPath("$.categoryName").isNotEmpty
            .jsonPath("$.relatedArticles").isArray

        webClient.post().uri("/api/user/history/articles/$summaryId/bookmark")
            .cookie("SESSION", session)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.summaryId").isEqualTo(summaryId)
            .jsonPath("$.isBookmarked").isEqualTo(true)

        val bookmarkCountAfterCreate = jdbc.queryForObject(
            "SELECT COUNT(*) FROM bookmarked_articles WHERE user_id = ? AND summary_id = ?",
            Int::class.java,
            userId,
            summaryId
        ) ?: 0
        bookmarkCountAfterCreate shouldBe 1

        val bookmarkedOnly = getJson("/api/user/history/articles?bookmarkedOnly=true&page=0&size=20", session)
        bookmarkedOnly["totalCount"].asInt() shouldBe 1
        bookmarkedOnly["items"][0]["id"].asText() shouldBe summaryId
        bookmarkedOnly["items"][0]["isBookmarked"].asBoolean().shouldBeTrue()

        webClient.post().uri("/api/user/history/articles/$summaryId/bookmark")
            .cookie("SESSION", session)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.isBookmarked").isEqualTo(false)

        val bookmarkCountAfterDelete = jdbc.queryForObject(
            "SELECT COUNT(*) FROM bookmarked_articles WHERE user_id = ? AND summary_id = ?",
            Int::class.java,
            userId,
            summaryId
        ) ?: 0
        bookmarkCountAfterDelete shouldBe 0

        val bookmarkedEmpty = getJson("/api/user/history/articles?bookmarkedOnly=true&page=0&size=20", session)
        bookmarkedEmpty["totalCount"].asInt() shouldBe 0
    }

    @Test
    fun `fresh user should see empty history and cannot access seeded article`() {
        val session = login("dev.user.fresh@clipping.local")
        val seededSummaryId = jdbc.queryForObject(
            "SELECT id FROM batch_summaries WHERE is_sent_to_slack = TRUE ORDER BY created_at DESC LIMIT 1",
            String::class.java
        ) ?: error("seeded summary not found")

        val history = getJson("/api/user/history/articles?page=0&size=20", session)
        history["totalCount"].asInt() shouldBe 0

        webClient.get().uri("/api/user/history/articles/$seededSummaryId")
            .cookie("SESSION", session)
            .exchange()
            .expectStatus().isNotFound

        webClient.post().uri("/api/user/history/articles/$seededSummaryId/bookmark")
            .cookie("SESSION", session)
            .exchange()
            .expectStatus().isNotFound
    }

    @Test
    fun `user delivery schedule api should expose default and persist custom values`() {
        val session = login("dev.user.fresh@clipping.local")
        val userId = requireUserId("dev.user.fresh@clipping.local")

        webClient.get().uri("/api/user/delivery-schedule")
            .cookie("SESSION", session)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.preset").isEqualTo("WEEKDAYS")
            .jsonPath("$.deliveryHour").isEqualTo(8)
            .jsonPath("$.deliveryDays.length()").isEqualTo(5)

        webClient.put().uri("/api/user/delivery-schedule")
            .cookie("SESSION", session)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"deliveryDays":["mon","wed"],"deliveryHour":12,"preset":"CUSTOM"}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.preset").isEqualTo("CUSTOM")
            .jsonPath("$.deliveryHour").isEqualTo(12)
            .jsonPath("$.deliveryDays[0]").isEqualTo("MON")
            .jsonPath("$.deliveryDays[1]").isEqualTo("WED")

        webClient.get().uri("/api/user/delivery-schedule")
            .cookie("SESSION", session)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.preset").isEqualTo("CUSTOM")
            .jsonPath("$.deliveryHour").isEqualTo(12)
            .jsonPath("$.deliveryDays.length()").isEqualTo(2)

        val storedHour = jdbc.queryForObject(
            "SELECT delivery_hour FROM user_delivery_schedules WHERE user_id = ?",
            Int::class.java,
            userId
        ) ?: error("delivery schedule hour missing")
        val storedDays = jdbc.queryForObject(
            "SELECT delivery_days FROM user_delivery_schedules WHERE user_id = ?",
            String::class.java,
            userId
        ) ?: error("delivery schedule days missing")

        storedHour shouldBe 12
        storedDays shouldBe "MON,WED"
    }

    @Test
    fun `invalid delivery schedule request should return bad request`() {
        val session = login("dev.user.fresh@clipping.local")

        webClient.put().uri("/api/user/delivery-schedule")
            .cookie("SESSION", session)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"deliveryDays":["MON"],"deliveryHour":24,"preset":"CUSTOM"}""")
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `user briefing and me endpoints should return seeded context`() {
        val session = login("dev.user@clipping.local")
        val approvedBriefingCategoryId = jdbc.queryForObject(
            """
            SELECT ds.category_id
            FROM daily_summaries ds
            JOIN clipping_user_requests cur ON cur.approved_category_id = ds.category_id
            WHERE cur.requester_user_id = ?
              AND cur.status = 'APPROVED'
            ORDER BY ds.created_at DESC
            LIMIT 1
            """.trimIndent(),
            String::class.java,
            requireUserId("dev.user@clipping.local")
        ) ?: error("approved briefing category not found")

        webClient.get().uri("/api/me")
            .cookie("SESSION", session)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.username").isEqualTo("dev.user@clipping.local")
            .jsonPath("$.role").isEqualTo("USER")

        val briefings = getJson("/api/user/briefing/today", session)
        briefings["briefings"].size() shouldBeGreaterThan 0

        val filtered = getJson("/api/user/briefing/today?categoryId=$approvedBriefingCategoryId", session)
        filtered["briefings"].size() shouldBeGreaterThan 0
        filtered["briefings"].forEach { item ->
            item["categoryId"].asText() shouldBe approvedBriefingCategoryId
        }
    }

    /** 세션 로그인 후 SESSION 쿠키 값을 추출한다. */
    private fun login(username: String): String {
        val loginResult = webClient.post().uri("/login")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(
                BodyInserters.fromFormData("username", username)
                    .with("password", "LocalPass123!")
            )
            .exchange()
            .expectStatus().is3xxRedirection
            .returnResult(Void::class.java)

        return loginResult.responseHeaders[HttpHeaders.SET_COOKIE]
            ?.asSequence()
            ?.map { it.substringBefore(";") }
            ?.lastOrNull { cookie ->
                cookie.startsWith("SESSION=") && cookie.substringAfter("SESSION=").isNotBlank()
            }
            ?.substringAfter("SESSION=")
            ?: error("SESSION cookie missing for $username")
    }

    /** 인증 쿠키를 붙여 GET 응답을 JSON 트리로 읽는다. */
    private fun getJson(path: String, session: String) =
        mapper.readTree(
            webClient.get().uri(path)
                .cookie("SESSION", session)
                .exchange()
                .expectStatus().isOk
                .expectBody(String::class.java)
                .returnResult()
                .responseBody ?: error("response body missing for $path")
        )

    /** 로컬 시드 사용자 ID를 조회한다. */
    private fun requireUserId(username: String): String =
        jdbc.queryForObject(
            "SELECT id FROM admin_users WHERE username = ?",
            String::class.java,
            username
        ) ?: error("user id not found: $username")
}
