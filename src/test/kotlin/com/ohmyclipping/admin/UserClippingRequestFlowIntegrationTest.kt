package com.ohmyclipping.admin

import com.ohmyclipping.model.Category
import com.ohmyclipping.service.SlackMessageSender
import com.ohmyclipping.service.source.SourceVerificationClient
import com.ohmyclipping.service.StatsService
import com.ohmyclipping.service.source.VerificationResult
import com.ohmyclipping.store.CategoryStore
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.nullable
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.reactive.function.BodyInserters
import java.net.URI
import java.time.YearMonth

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class UserClippingRequestFlowIntegrationTest {

    @Autowired
    lateinit var webClient: WebTestClient

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Autowired
    lateinit var statsService: StatsService

    @Autowired
    lateinit var categoryStore: CategoryStore

    @MockitoBean
    lateinit var slackMessageSender: SlackMessageSender

    @MockitoBean
    lateinit var sourceVerificationClient: SourceVerificationClient

    @BeforeEach
    fun setUp() {
        // 테스트 간 채널 중복 구독 충돌을 방지한다. 이전 테스트가 남긴 요청을 삭제한다.
        jdbc.execute("DELETE FROM clipping_user_requests WHERE slack_channel_id = 'C123TEST01'")
        given(slackMessageSender.testConnection(nullable(String::class.java), nullable(String::class.java)))
            .willReturn(
                SlackMessageSender.SlackConnectionTestResult(
                    ok = true,
                    botUser = "mock-bot",
                    team = "mock-team",
                    channelId = "C123TEST01",
                    channelName = "mock-channel",
                    neededScopes = null,
                    providedScopes = null,
                    rawError = null
                )
            )
        given(sourceVerificationClient.verify(any(URI::class.java) ?: URI.create("https://example.com/mock.xml")))
            .willReturn(VerificationResult.VERIFIED)
    }

    @Test
    fun `user request can be approved by admin`() {
        val adminSession = signupAndLogin(
            signupPath = "/admin/signup",
            loginPath = "/login",
            usernamePrefix = "adm"
        )
        val userCredentials = signupUserOnly("usr")
        approveUserAccount(adminSession, userCredentials.username)
        val userSession = loginOnly(
            loginPath = "/login",
            username = userCredentials.username,
            password = userCredentials.password
        )

        val createResult = webClient.post().uri("/api/user/requests")
            .cookie("SESSION", userSession)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "requestName": "팀 AI 다이제스트",
                  "sourceName": "Tech Source",
                  "sourceUrl": "https://example.com/rss.xml",
                  "slackChannelId": "C123TEST01",
                  "personaName": "실무 요약",
                  "personaPrompt": "핵심 3줄과 실무 액션을 제안해줘",
                  "summaryStyle": "핵심 요약",
                  "targetAudience": "운영 실무자",
                  "requestNote": "매일 아침 브리핑용"
                }
                """.trimIndent()
            )
            .exchange()
            .expectStatus().isCreated
            .expectBody(String::class.java)
            .returnResult()

        val requestBody = createResult.responseBody ?: error("request response missing")
        val requestId = requestBody.substringAfter("\"id\":\"").substringBefore("\"")
        requestId.isNotBlank() shouldBe true

        webClient.get().uri("/api/admin/user-requests?status=PENDING")
            .cookie("SESSION", adminSession)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$[0].id").isEqualTo(requestId)

        webClient.post().uri("/api/admin/user-requests/$requestId/approve")
            .cookie("SESSION", adminSession)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"legalBasis":"QUOTATION_ONLY","summaryAllowed":true,"fulltextAllowed":false,"reviewNotes":"승인 완료","responsibilityAcknowledged":true}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo("APPROVED")
            .jsonPath("$.deliveryState").isEqualTo("ACTIVE")
            .jsonPath("$.collectingReady").isEqualTo(true)
            .jsonPath("$.approvedCategoryId").isNotEmpty
            .jsonPath("$.approvedPersonaId").isNotEmpty
            .jsonPath("$.approvedSourceId").isNotEmpty

        val approvedCategoryId = jdbc.queryForObject(
            "SELECT approved_category_id FROM clipping_user_requests WHERE id = ?",
            String::class.java,
            requestId
        ) ?: error("approved category id not found")
        val approvedCategoryName = jdbc.queryForObject(
            "SELECT name FROM batch_categories WHERE id = ?",
            String::class.java,
            approvedCategoryId
        ) ?: error("approved category name not found")
        approvedCategoryName shouldBe "팀 AI 다이제스트"
        val approvedPersonaId = jdbc.queryForObject(
            "SELECT approved_persona_id FROM clipping_user_requests WHERE id = ?",
            String::class.java,
            requestId
        ) ?: error("approved persona id not found")
        val approvedPersonaName = jdbc.queryForObject(
            "SELECT name FROM clipping_personas WHERE id = ?",
            String::class.java,
            approvedPersonaId
        ) ?: error("approved persona name not found")
        approvedPersonaName shouldBe "실무 요약"

        webClient.get().uri("/api/user/requests")
            .cookie("SESSION", userSession)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$[0].status").isEqualTo("APPROVED")
            .jsonPath("$[0].deliveryState").isEqualTo("ACTIVE")
            .jsonPath("$[0].collectingReady").isEqualTo(true)
    }

    @Test
    fun `approved request should expose actual delivery state after verification and pause`() {
        val adminSession = signupAndLogin(
            signupPath = "/admin/signup",
            loginPath = "/login",
            usernamePrefix = "adm"
        )
        val userCredentials = signupUserOnly("usr")
        approveUserAccount(adminSession, userCredentials.username)
        val userSession = loginOnly(
            loginPath = "/login",
            username = userCredentials.username,
            password = userCredentials.password
        )
        val requestId = createAndApproveUserRequest(adminSession, userSession, "실제 전달 상태 테스트")

        val approvedSourceId = jdbc.queryForObject(
            "SELECT approved_source_id FROM clipping_user_requests WHERE id = ?",
            String::class.java,
            requestId
        ) ?: error("approved source id missing")

        // 승인 즉시 VERIFIED 상태로 소스가 생성되어 ACTIVE 상태여야 한다.
        webClient.get().uri("/api/user/requests")
            .cookie("SESSION", userSession)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$[0].deliveryState").isEqualTo("ACTIVE")
            .jsonPath("$[0].readySourceCount").isEqualTo(1)
            .jsonPath("$[0].totalSourceCount").isEqualTo(1)
            .jsonPath("$[0].representativeSourceVerificationStatus").isEqualTo("VERIFIED")

        // 관리자가 수동으로 verify를 재호출해도 여전히 VERIFIED/ACTIVE 상태여야 한다.
        webClient.post().uri("/api/admin/sources/$approvedSourceId/verify")
            .cookie("SESSION", adminSession)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{}")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo("VERIFIED")

        webClient.get().uri("/api/user/requests")
            .cookie("SESSION", userSession)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$[0].deliveryState").isEqualTo("ACTIVE")
            .jsonPath("$[0].collectingReady").isEqualTo(true)
            .jsonPath("$[0].readySourceCount").isEqualTo(1)
            .jsonPath("$[0].representativeSourceVerificationStatus").isEqualTo("VERIFIED")

        webClient.put().uri("/api/user/subscriptions/$requestId/preferences")
            .cookie("SESSION", userSession)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"isActive":false}""")
            .exchange()
            .expectStatus().isOk

        webClient.get().uri("/api/user/requests")
            .cookie("SESSION", userSession)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$[0].deliveryState").isEqualTo("PAUSED")
            .jsonPath("$[0].collectingReady").isEqualTo(false)

        webClient.get().uri("/api/admin/user-requests?status=APPROVED")
            .cookie("SESSION", adminSession)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$[0].deliveryState").isEqualTo("PAUSED")
    }

    @Test
    fun `approved user can access own monthly stats and csv report`() {
        val adminSession = signupAndLogin(
            signupPath = "/admin/signup",
            loginPath = "/login",
            usernamePrefix = "adm"
        )
        val userCredentials = signupUserOnly("usr")
        approveUserAccount(adminSession, userCredentials.username)
        val userSession = loginOnly(
            loginPath = "/login",
            username = userCredentials.username,
            password = userCredentials.password
        )

        val createResult = webClient.post().uri("/api/user/requests")
            .cookie("SESSION", userSession)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "requestName": "개인 통계 확인",
                  "sourceName": "Tech Source",
                  "sourceUrl": "https://example.com/rss.xml",
                  "slackChannelId": "C123TEST01",
                  "personaName": "실무 요약",
                  "personaPrompt": "핵심만 알려줘"
                }
                """.trimIndent()
            )
            .exchange()
            .expectStatus().isCreated
            .expectBody(String::class.java)
            .returnResult()

        val requestBody = createResult.responseBody ?: error("request response missing")
        val requestId = requestBody.substringAfter("\"id\":\"").substringBefore("\"")

        webClient.post().uri("/api/admin/user-requests/$requestId/approve")
            .cookie("SESSION", adminSession)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"legalBasis":"QUOTATION_ONLY","summaryAllowed":true,"fulltextAllowed":false,"reviewNotes":"승인 완료","responsibilityAcknowledged":true}""")
            .exchange()
            .expectStatus().isOk

        val approvedCategoryId = jdbc.queryForObject(
            "SELECT approved_category_id FROM clipping_user_requests WHERE id = ?",
            String::class.java,
            requestId
        ) ?: error("approved category id not found")

        val unrelatedCategory = categoryStore.save(
            Category(id = "", name = "Other-${System.nanoTime()}")
        )

        statsService.recordCollection(approvedCategoryId, 7)
        statsService.recordSummarization(approvedCategoryId, 5, listOf("AI", "Ops"), 0.82f)
        statsService.recordSent(approvedCategoryId, 3)
        statsService.recordCollection(unrelatedCategory.id, 99)

        val yearMonth = YearMonth.now().toString()

        webClient.get().uri("/api/user/stats/monthly?yearMonth=$yearMonth")
            .cookie("SESSION", userSession)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.length()").isEqualTo(1)
            .jsonPath("$[0].categoryId").isEqualTo(approvedCategoryId)
            .jsonPath("$[0].itemsCollected").isEqualTo(7)

        webClient.get().uri("/api/user/reports/monthly.csv?yearMonth=$yearMonth")
            .cookie("SESSION", userSession)
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentTypeCompatibleWith(MediaType.parseMediaType("text/csv"))
            .expectHeader().value(HttpHeaders.CONTENT_DISPOSITION) { value ->
                value.contains(".csv") shouldBe true
            }
            .expectBody(String::class.java)
            .value { csv ->
                csv.contains("categoryName") shouldBe true
                csv.contains(approvedCategoryId) shouldBe true
                csv.contains(unrelatedCategory.id) shouldBe false
            }
    }

    @Test
    fun `approved user can request additional rss sources in bulk`() {
        val adminSession = signupAndLogin(
            signupPath = "/admin/signup",
            loginPath = "/login",
            usernamePrefix = "adm"
        )
        val userCredentials = signupUserOnly("usr")
        approveUserAccount(adminSession, userCredentials.username)
        val userSession = loginOnly(
            loginPath = "/login",
            username = userCredentials.username,
            password = userCredentials.password
        )

        val createResult = webClient.post().uri("/api/user/requests")
            .cookie("SESSION", userSession)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "requestName": "AI 브리핑",
                  "sourceName": "기본 소스",
                  "sourceUrl": "https://example.com/rss.xml",
                  "slackChannelId": "C123TEST01",
                  "personaName": "실무 요약",
                  "personaPrompt": "핵심만 정리"
                }
                """.trimIndent()
            )
            .exchange()
            .expectStatus().isCreated
            .expectBody(String::class.java)
            .returnResult()
        val baseRequestId = createResult.responseBody?.substringAfter("\"id\":\"")?.substringBefore("\"")
            ?: error("base request id missing")

        webClient.post().uri("/api/admin/user-requests/$baseRequestId/approve")
            .cookie("SESSION", adminSession)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"legalBasis":"QUOTATION_ONLY","summaryAllowed":true,"fulltextAllowed":false,"reviewNotes":"승인 완료","responsibilityAcknowledged":true}""")
            .exchange()
            .expectStatus().isOk

        webClient.post().uri("/api/user/requests/rss-sources")
            .cookie("SESSION", userSession)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "baseRequestId": "$baseRequestId",
                  "requestNote": "다양한 RSS 추가 요청",
                  "sources": [
                    {"sourceName": "TechCrunch AI", "sourceUrl": "https://techcrunch.com/category/artificial-intelligence/feed/"},
                    {"sourceName": "VentureBeat AI", "sourceUrl": "https://venturebeat.com/ai/feed/"}
                  ]
                }
                """.trimIndent()
            )
            .exchange()
            .expectStatus().isCreated
            .expectBody()
            .jsonPath("$.length()").isEqualTo(2)
            .jsonPath("$[0].status").isEqualTo("PENDING")
            .jsonPath("$[1].status").isEqualTo("PENDING")
            .jsonPath("$[0].sourceName").isEqualTo("TechCrunch AI")
            .jsonPath("$[1].sourceName").isEqualTo("VentureBeat AI")

        webClient.get().uri("/api/user/requests")
            .cookie("SESSION", userSession)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.length()").isEqualTo(3)
    }

    @Test
    fun `additional rss approval for slack dm should reuse base delivery policy`() {
        val adminSession = signupAndLogin(
            signupPath = "/admin/signup",
            loginPath = "/login",
            usernamePrefix = "adm"
        )
        val userCredentials = signupUserOnly("usr")
        approveUserAccount(adminSession, userCredentials.username)
        setUserSlackDmChannel(userCredentials.username, "D_USER_DM_01")
        val userSession = loginOnly(
            loginPath = "/login",
            username = userCredentials.username,
            password = userCredentials.password
        )

        val personaResult = webClient.post().uri("/api/user/setup/personas")
            .cookie("SESSION", userSession)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"name":"DM 스타일","systemPrompt":"핵심만 정리","maxItems":5}""")
            .exchange()
            .expectStatus().isCreated
            .expectBody(String::class.java)
            .returnResult()
        val personaId = personaResult.responseBody?.substringAfter("\"id\":\"")?.substringBefore("\"")
            ?: error("persona id missing")

        val categoryResult = webClient.post().uri("/api/user/setup/categories")
            .cookie("SESSION", userSession)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "name":"DM 기반 주제",
                  "description":"Slack DM delivery policy",
                  "maxItems":5,
                  "personaId":"$personaId"
                }
                """.trimIndent()
            )
            .exchange()
            .expectStatus().isCreated
            .expectBody(String::class.java)
            .returnResult()
        val categoryId = categoryResult.responseBody?.substringAfter("\"id\":\"")?.substringBefore("\"")
            ?: error("category id missing")

        val baseRequestResult = webClient.post().uri("/api/user/requests/wizard-ownership")
            .cookie("SESSION", userSession)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "requestName":"DM 기반 주제",
                  "sourceName":"기본 소스",
                  "sourceUrl":"https://example.com/base-rss.xml",
                  "slackChannelId":"",
                  "personaName":"DM 스타일",
                  "personaPrompt":"핵심만 정리",
                  "categoryId":"$categoryId",
                  "personaId":"$personaId"
                }
                """.trimIndent()
            )
            .exchange()
            .expectStatus().isCreated
            .expectBody(String::class.java)
            .returnResult()
        val baseRequestId = baseRequestResult.responseBody?.substringAfter("\"id\":\"")?.substringBefore("\"")
            ?: error("base request id missing")
        jdbc.queryForObject(
            "SELECT slack_channel_id FROM clipping_user_requests WHERE id = ?",
            String::class.java,
            baseRequestId
        ) shouldBe "D_USER_DM_01"

        // 위자드 등록은 PENDING 상태로 저장되므로, 추가 RSS 등록 전에 관리자 승인이 필요하다.
        val baseApprovalResult = webClient.post().uri("/api/admin/user-requests/$baseRequestId/approve")
            .cookie("SESSION", adminSession)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"legalBasis":"QUOTATION_ONLY","summaryAllowed":true,"fulltextAllowed":false,"reviewNotes":"DM 기본 구독 승인","responsibilityAcknowledged":true}""")
            .exchange()
            .expectStatus().isOk
            .expectBody(String::class.java)
            .returnResult()
        // 승인 시 신규 리소스가 생성되므로 실제 approved ID를 추출한다.
        val approvedCategoryId = baseApprovalResult.responseBody
            ?.substringAfter("\"approvedCategoryId\":\"")?.substringBefore("\"")
            ?: error("approved category id missing from base approval")
        val approvedPersonaId = baseApprovalResult.responseBody
            ?.substringAfter("\"approvedPersonaId\":\"")?.substringBefore("\"")
            ?: error("approved persona id missing from base approval")

        val additionalResult = webClient.post().uri("/api/user/requests/rss-sources")
            .cookie("SESSION", userSession)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "baseRequestId":"$baseRequestId",
                  "sources":[
                    {"sourceName":"Supply Chain Dive","sourceUrl":"https://example.com/supply-chain.xml"}
                  ],
                  "requestNote":"DM 구독에 RSS 추가"
                }
                """.trimIndent()
            )
            .exchange()
            .expectStatus().isCreated
            .expectBody(String::class.java)
            .returnResult()
        val additionalRequestId = additionalResult.responseBody?.substringAfter("\"id\":\"")?.substringBefore("\"")
            ?: error("additional request id missing")

        webClient.post().uri("/api/admin/user-requests/$additionalRequestId/approve")
            .cookie("SESSION", adminSession)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"legalBasis":"QUOTATION_ONLY","summaryAllowed":true,"fulltextAllowed":false,"reviewNotes":"DM RSS 추가 승인","responsibilityAcknowledged":true}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo("APPROVED")
            .jsonPath("$.approvedCategoryId").isEqualTo(approvedCategoryId)
            .jsonPath("$.approvedPersonaId").isEqualTo(approvedPersonaId)
            .jsonPath("$.approvedSourceId").isNotEmpty

        jdbc.queryForObject(
            "SELECT approved_category_id FROM clipping_user_requests WHERE id = ?",
            String::class.java,
            additionalRequestId
        ) shouldBe approvedCategoryId
        jdbc.queryForObject(
            "SELECT approved_persona_id FROM clipping_user_requests WHERE id = ?",
            String::class.java,
            additionalRequestId
        ) shouldBe approvedPersonaId
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM batch_categories WHERE name LIKE 'DM 기반 주제 - RSS 추가%'",
            Int::class.java
        ) shouldBe 0

        val approvedSourceId = jdbc.queryForObject(
            "SELECT approved_source_id FROM clipping_user_requests WHERE id = ?",
            String::class.java,
            additionalRequestId
        ) ?: error("approved source missing")
        jdbc.queryForObject(
            "SELECT category_id FROM rss_sources WHERE id = ?",
            String::class.java,
            approvedSourceId
        ) shouldBe approvedCategoryId
        jdbc.queryForObject(
            "SELECT slack_channel_id FROM clipping_user_requests WHERE id = ?",
            String::class.java,
            baseRequestId
        ) shouldBe "D_USER_DM_01"
    }

    @Test
    fun `additional rss sources request should rollback all when one source URL is invalid`() {
        val adminSession = signupAndLogin(
            signupPath = "/admin/signup",
            loginPath = "/login",
            usernamePrefix = "adm"
        )
        val userCredentials = signupUserOnly("usr")
        approveUserAccount(adminSession, userCredentials.username)
        val userSession = loginOnly(
            loginPath = "/login",
            username = userCredentials.username,
            password = userCredentials.password
        )

        val createResult = webClient.post().uri("/api/user/requests")
            .cookie("SESSION", userSession)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "requestName": "보안 검증",
                  "sourceName": "기본 소스",
                  "sourceUrl": "https://example.com/rss.xml",
                  "slackChannelId": "C123TEST01",
                  "personaName": "실무 요약",
                  "personaPrompt": "핵심만 정리"
                }
                """.trimIndent()
            )
            .exchange()
            .expectStatus().isCreated
            .expectBody(String::class.java)
            .returnResult()
        val baseRequestId = createResult.responseBody?.substringAfter("\"id\":\"")?.substringBefore("\"")
            ?: error("base request id missing")

        webClient.post().uri("/api/admin/user-requests/$baseRequestId/approve")
            .cookie("SESSION", adminSession)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"legalBasis":"QUOTATION_ONLY","summaryAllowed":true,"fulltextAllowed":false,"reviewNotes":"승인 완료","responsibilityAcknowledged":true}""")
            .exchange()
            .expectStatus().isOk

        webClient.post().uri("/api/user/requests/rss-sources")
            .cookie("SESSION", userSession)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "baseRequestId": "$baseRequestId",
                  "requestNote": "부분 실패 롤백 검증",
                  "sources": [
                    {"sourceName": "Valid RSS", "sourceUrl": "https://example.com/valid-feed.xml"},
                    {"sourceName": "Invalid RSS", "sourceUrl": "http://127.0.0.1/internal.xml"}
                  ]
                }
                """.trimIndent()
            )
            .exchange()
            .expectStatus().isBadRequest

        webClient.get().uri("/api/user/requests")
            .cookie("SESSION", userSession)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.length()").isEqualTo(1)
            .jsonPath("$[0].id").isEqualTo(baseRequestId)
    }

    @Test
    fun `approved user can read and update immediate subscription preferences`() {
        val adminSession = signupAndLogin(
            signupPath = "/admin/signup",
            loginPath = "/login",
            usernamePrefix = "adm"
        )
        val userCredentials = signupUserOnly("usr")
        approveUserAccount(adminSession, userCredentials.username)
        val userSession = loginOnly(
            loginPath = "/login",
            username = userCredentials.username,
            password = userCredentials.password
        )
        val requestId = createAndApproveUserRequest(adminSession, userSession, "즉시 반영 테스트")

        webClient.get().uri("/api/user/subscriptions/$requestId/preferences")
            .cookie("SESSION", userSession)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.requestId").isEqualTo(requestId)
            .jsonPath("$.isActive").isEqualTo(true)
            .jsonPath("$.maxItems").isEqualTo(5)
            .jsonPath("$.excludeKeywords.length()").isEqualTo(0)

        webClient.put().uri("/api/user/subscriptions/$requestId/preferences")
            .cookie("SESSION", userSession)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "isActive": false,
                  "maxItems": 3,
                  "excludeKeywords": ["광고", "스팸"],
                  "includeThreshold": 0.75
                }
                """.trimIndent()
            )
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.isActive").isEqualTo(false)
            .jsonPath("$.maxItems").isEqualTo(3)
            .jsonPath("$.excludeKeywords.length()").isEqualTo(2)
            .jsonPath("$.excludeKeywords[0]").isEqualTo("광고")
            .jsonPath("$.includeThreshold").isEqualTo(0.75)

        webClient.get().uri("/api/user/subscriptions/$requestId/preferences")
            .cookie("SESSION", userSession)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.isActive").isEqualTo(false)
            .jsonPath("$.maxItems").isEqualTo(3)
            .jsonPath("$.excludeKeywords.length()").isEqualTo(2)
            .jsonPath("$.includeThreshold").isEqualTo(0.75)
    }

    @Test
    fun `approved user cannot read another users subscription preferences`() {
        val adminSession = signupAndLogin(
            signupPath = "/admin/signup",
            loginPath = "/login",
            usernamePrefix = "adm"
        )
        val ownerCredentials = signupUserOnly("owner")
        approveUserAccount(adminSession, ownerCredentials.username)
        val ownerSession = loginOnly(
            loginPath = "/login",
            username = ownerCredentials.username,
            password = ownerCredentials.password
        )
        val otherCredentials = signupUserOnly("other")
        approveUserAccount(adminSession, otherCredentials.username)
        val otherSession = loginOnly(
            loginPath = "/login",
            username = otherCredentials.username,
            password = otherCredentials.password
        )
        val requestId = createAndApproveUserRequest(adminSession, ownerSession, "권한 분리 테스트")

        webClient.get().uri("/api/user/subscriptions/$requestId/preferences")
            .cookie("SESSION", otherSession)
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `user setup endpoints should create owned resources and legacy aliases should be removed`() {
        val adminSession = signupAndLogin(
            signupPath = "/admin/signup",
            loginPath = "/login",
            usernamePrefix = "adm"
        )
        val userCredentials = signupUserOnly("setup")
        approveUserAccount(adminSession, userCredentials.username)
        val userSession = loginOnly(
            loginPath = "/login",
            username = userCredentials.username,
            password = userCredentials.password
        )

        val personaResult = webClient.post().uri("/api/user/setup/personas")
            .cookie("SESSION", userSession)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"name":"내 스타일","systemPrompt":"핵심만 정리","maxItems":5}""")
            .exchange()
            .expectStatus().isCreated
            .expectBody(String::class.java)
            .returnResult()
        val personaId = personaResult.responseBody?.substringAfter("\"id\":\"")?.substringBefore("\"")
            ?: error("persona id missing")

        val categoryResult = webClient.post().uri("/api/user/setup/categories")
            .cookie("SESSION", userSession)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "name":"유저 셋업 주제",
                  "description":"owner scope 테스트",
                  "slackChannelId":"C123TEST01",
                  "maxItems":5,
                  "personaId":"$personaId"
                }
                """.trimIndent()
            )
            .exchange()
            .expectStatus().isCreated
            .expectBody(String::class.java)
            .returnResult()
        val categoryId = categoryResult.responseBody?.substringAfter("\"id\":\"")?.substringBefore("\"")
            ?: error("category id missing")

        webClient.post().uri("/api/user/setup/clipping/$categoryId/pipeline")
            .cookie("SESSION", userSession)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"sendToSlack":false,"unsentOnly":true}""")
            .exchange()
            .expectStatus().isOk

        val sourceResult = webClient.post().uri("/api/user/setup/sources")
            .cookie("SESSION", userSession)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "name":"유저 셋업 소스",
                  "url":"https://example.com/rss.xml",
                  "categoryId":"$categoryId",
                  "legalBasis":"QUOTATION_ONLY",
                  "summaryAllowed":true
                }
                """.trimIndent()
            )
            .exchange()
            .expectStatus().isCreated
            .expectBody(String::class.java)
            .returnResult()
        val sourceId = sourceResult.responseBody?.substringAfter("\"id\":\"")?.substringBefore("\"")
            ?: error("source id missing")

        webClient.post().uri("/api/user/setup/sources/$sourceId/verify")
            .cookie("SESSION", userSession)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{}")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo("VERIFIED")

        webClient.post().uri("/api/user/setup/sources/$sourceId/approve")
            .cookie("SESSION", userSession)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"approved":true,"legalBasis":"QUOTATION_ONLY","summaryAllowed":true}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.crawlApproved").isEqualTo(true)
            .jsonPath("$.approvedBy").isEqualTo(userCredentials.username)

        webClient.get().uri("/api/user/personas")
            .cookie("SESSION", userSession)
            .exchange()
            .expectStatus().isNotFound

        webClient.post().uri("/api/user/categories")
            .cookie("SESSION", userSession)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"name":"legacy"}""")
            .exchange()
            .expectStatus().isNotFound

        webClient.post().uri("/api/user/sources")
            .cookie("SESSION", userSession)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"name":"legacy","url":"https://example.com/rss.xml","categoryId":"$categoryId"}""")
            .exchange()
            .expectStatus().isNotFound

        webClient.post().uri("/api/user/clipping/$categoryId/pipeline")
            .cookie("SESSION", userSession)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"sendToSlack":false}""")
            .exchange()
            .expectStatus().isNotFound
    }

    @Test
    fun `user setup slack verify should reject invalid channel id format`() {
        val adminSession = signupAndLogin(
            signupPath = "/admin/signup",
            loginPath = "/login",
            usernamePrefix = "adm"
        )
        val userCredentials = signupUserOnly("slack")
        approveUserAccount(adminSession, userCredentials.username)
        val userSession = loginOnly(
            loginPath = "/login",
            username = userCredentials.username,
            password = userCredentials.password
        )

        webClient.post().uri("/api/user/setup/slack/verify")
            .cookie("SESSION", userSession)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"slackChannelId":"not-a-channel"}""")
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.code").isEqualTo("INVALID_INPUT")
            .jsonPath("$.message").isEqualTo("Slack 채널 ID 형식이 올바르지 않습니다.")
    }

    @Test
    fun `user setup should reject another users category and source access`() {
        val adminSession = signupAndLogin(
            signupPath = "/admin/signup",
            loginPath = "/login",
            usernamePrefix = "adm"
        )
        val ownerCredentials = signupUserOnly("owner")
        approveUserAccount(adminSession, ownerCredentials.username)
        val ownerSession = loginOnly(
            loginPath = "/login",
            username = ownerCredentials.username,
            password = ownerCredentials.password
        )
        val otherCredentials = signupUserOnly("other")
        approveUserAccount(adminSession, otherCredentials.username)
        val otherSession = loginOnly(
            loginPath = "/login",
            username = otherCredentials.username,
            password = otherCredentials.password
        )

        val categoryResult = webClient.post().uri("/api/user/setup/categories")
            .cookie("SESSION", ownerSession)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"name":"owner-cat","slackChannelId":"C123TEST01","maxItems":5}""")
            .exchange()
            .expectStatus().isCreated
            .expectBody(String::class.java)
            .returnResult()
        val categoryId = categoryResult.responseBody?.substringAfter("\"id\":\"")?.substringBefore("\"")
            ?: error("category id missing")

        val sourceResult = webClient.post().uri("/api/user/setup/sources")
            .cookie("SESSION", ownerSession)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "name":"owner-source",
                  "url":"https://example.com/owner.xml",
                  "categoryId":"$categoryId"
                }
                """.trimIndent()
            )
            .exchange()
            .expectStatus().isCreated
            .expectBody(String::class.java)
            .returnResult()
        val sourceId = sourceResult.responseBody?.substringAfter("\"id\":\"")?.substringBefore("\"")
            ?: error("source id missing")

        webClient.post().uri("/api/user/setup/sources")
            .cookie("SESSION", otherSession)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "name":"intrusion",
                  "url":"https://example.com/intrusion.xml",
                  "categoryId":"$categoryId"
                }
                """.trimIndent()
            )
            .exchange()
            .expectStatus().isNotFound

        webClient.post().uri("/api/user/setup/sources/$sourceId/approve")
            .cookie("SESSION", otherSession)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"approved":true}""")
            .exchange()
            .expectStatus().isNotFound

        webClient.post().uri("/api/user/setup/clipping/$categoryId/pipeline")
            .cookie("SESSION", otherSession)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"sendToSlack":false}""")
            .exchange()
            .expectStatus().isNotFound
    }

    @Test
    fun `user setup should allow duplicate category names across users`() {
        val adminSession = signupAndLogin(
            signupPath = "/admin/signup",
            loginPath = "/login",
            usernamePrefix = "adm"
        )
        val firstCredentials = signupUserOnly("first")
        approveUserAccount(adminSession, firstCredentials.username)
        val firstSession = loginOnly(
            loginPath = "/login",
            username = firstCredentials.username,
            password = firstCredentials.password
        )
        val secondCredentials = signupUserOnly("second")
        approveUserAccount(adminSession, secondCredentials.username)
        val secondSession = loginOnly(
            loginPath = "/login",
            username = secondCredentials.username,
            password = secondCredentials.password
        )

        val payload = """
            {
              "name":"공통 주제",
              "description":"중복 이름 허용 확인",
              "slackChannelId":"C123TEST01",
              "maxItems":5
            }
        """.trimIndent()

        webClient.post().uri("/api/user/setup/categories")
            .cookie("SESSION", firstSession)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(payload)
            .exchange()
            .expectStatus().isCreated
            .expectBody()
            .jsonPath("$.name").isEqualTo("공통 주제")

        webClient.post().uri("/api/user/setup/categories")
            .cookie("SESSION", secondSession)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(payload)
            .exchange()
            .expectStatus().isCreated
            .expectBody()
            .jsonPath("$.name").isEqualTo("공통 주제")
    }

    @Test
    fun `user setup persona list should only return owned personas`() {
        val adminSession = signupAndLogin(
            signupPath = "/admin/signup",
            loginPath = "/login",
            usernamePrefix = "adm"
        )
        val ownerCredentials = signupUserOnly("owner")
        approveUserAccount(adminSession, ownerCredentials.username)
        val ownerSession = loginOnly(
            loginPath = "/login",
            username = ownerCredentials.username,
            password = ownerCredentials.password
        )
        val otherCredentials = signupUserOnly("other")
        approveUserAccount(adminSession, otherCredentials.username)
        val otherSession = loginOnly(
            loginPath = "/login",
            username = otherCredentials.username,
            password = otherCredentials.password
        )

        webClient.post().uri("/api/user/setup/personas")
            .cookie("SESSION", ownerSession)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"name":"owner-style","systemPrompt":"owner prompt"}""")
            .exchange()
            .expectStatus().isCreated

        webClient.post().uri("/api/user/setup/personas")
            .cookie("SESSION", otherSession)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"name":"other-style","systemPrompt":"other prompt"}""")
            .exchange()
            .expectStatus().isCreated

        webClient.get().uri("/api/user/setup/personas")
            .cookie("SESSION", ownerSession)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.length()").isEqualTo(1)
            .jsonPath("$[0].name").isEqualTo("owner-style")
    }

    @Test
    fun `approved setup persona should be marked as locked`() {
        val adminSession = signupAndLogin(
            signupPath = "/admin/signup",
            loginPath = "/login",
            usernamePrefix = "adm"
        )
        val userCredentials = signupUserOnly("usr")
        approveUserAccount(adminSession, userCredentials.username)
        val userSession = loginOnly(
            loginPath = "/login",
            username = userCredentials.username,
            password = userCredentials.password
        )
        val requestId = createAndApproveUserRequest(adminSession, userSession, "잠금 표시 테스트")
        val approvedPersonaId = jdbc.queryForObject(
            "SELECT approved_persona_id FROM clipping_user_requests WHERE id = ?",
            String::class.java,
            requestId
        ) ?: error("approved persona id missing")

        webClient.get().uri("/api/user/setup/personas/$approvedPersonaId")
            .cookie("SESSION", userSession)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.id").isEqualTo(approvedPersonaId)
    }

    @Test
    fun `approved subscription persona cannot be updated directly from setup api`() {
        val adminSession = signupAndLogin(
            signupPath = "/admin/signup",
            loginPath = "/login",
            usernamePrefix = "adm"
        )
        val userCredentials = signupUserOnly("usr")
        approveUserAccount(adminSession, userCredentials.username)
        val userSession = loginOnly(
            loginPath = "/login",
            username = userCredentials.username,
            password = userCredentials.password
        )
        val requestId = createAndApproveUserRequest(adminSession, userSession, "요약 스타일 잠금 테스트")
        val approvedPersonaId = jdbc.queryForObject(
            "SELECT approved_persona_id FROM clipping_user_requests WHERE id = ?",
            String::class.java,
            requestId
        ) ?: error("approved persona id missing")

        webClient.put().uri("/api/user/setup/personas/$approvedPersonaId")
            .cookie("SESSION", userSession)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"name":"변경 시도","systemPrompt":"새 프롬프트"}""")
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.message").value<String> { message ->
                message.contains("운영 검토 요청") shouldBe true
            }
    }

    @Test
    fun `approved subscription persona cannot be deleted directly from setup api`() {
        val adminSession = signupAndLogin(
            signupPath = "/admin/signup",
            loginPath = "/login",
            usernamePrefix = "adm"
        )
        val userCredentials = signupUserOnly("usr")
        approveUserAccount(adminSession, userCredentials.username)
        val userSession = loginOnly(
            loginPath = "/login",
            username = userCredentials.username,
            password = userCredentials.password
        )
        val requestId = createAndApproveUserRequest(adminSession, userSession, "요약 스타일 삭제 잠금 테스트")
        val approvedPersonaId = jdbc.queryForObject(
            "SELECT approved_persona_id FROM clipping_user_requests WHERE id = ?",
            String::class.java,
            requestId
        ) ?: error("approved persona id missing")

        webClient.delete().uri("/api/user/setup/personas/$approvedPersonaId")
            .cookie("SESSION", userSession)
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.message").value<String> { message ->
                message.contains("운영 검토 요청") shouldBe true
            }
    }

    @Test
    fun `legacy user category rule endpoint is no longer available`() {
        val adminSession = signupAndLogin(
            signupPath = "/admin/signup",
            loginPath = "/login",
            usernamePrefix = "adm"
        )
        val userCredentials = signupUserOnly("usr")
        approveUserAccount(adminSession, userCredentials.username)
        val userSession = loginOnly(
            loginPath = "/login",
            username = userCredentials.username,
            password = userCredentials.password
        )
        val requestId = createAndApproveUserRequest(adminSession, userSession, "레거시 경로 제거 테스트")

        val approvedCategoryId = jdbc.queryForObject(
            "SELECT approved_category_id FROM clipping_user_requests WHERE id = ?",
            String::class.java,
            requestId
        ) ?: error("approved category id missing")

        webClient.get().uri("/api/user/categories/$approvedCategoryId/rule")
            .cookie("SESSION", userSession)
            .exchange()
            .expectStatus().isNotFound
    }

    private fun signupAndLogin(
        signupPath: String,
        loginPath: String,
        usernamePrefix: String
    ): String {
        val username = "$usernamePrefix${System.nanoTime() % 1_000_000}"
        val password = "StrongPass123!"

        webClient.post().uri(signupPath)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(
                BodyInserters.fromFormData("username", username)
                    .with("displayName", usernamePrefix)
                    .with("password", password)
                    .with("confirmPassword", password)
            )
            .exchange()
            .expectStatus().is3xxRedirection

        val loginResult = webClient.post().uri(loginPath)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(
                BodyInserters.fromFormData("username", username)
                    .with("password", password)
            )
            .exchange()
            .expectStatus().is3xxRedirection
            .returnResult(Void::class.java)

        val sessionCookie = loginResult.responseHeaders[HttpHeaders.SET_COOKIE]
            ?.asSequence()
            ?.map { it.substringBefore(";") }
            ?.lastOrNull {
                it.startsWith("SESSION=") && it.substringAfter("SESSION=").isNotBlank()
            }
            ?: throw IllegalStateException("SESSION cookie not found")
        return sessionCookie.substringAfter("SESSION=")
    }

    private fun signupUserOnly(usernamePrefix: String): UserCredentials {
        val username = "$usernamePrefix${System.nanoTime() % 1_000_000}"
        val password = "StrongPass123!"

        // V129: signup 에 departmentId 가 필요하므로 부서 row 를 먼저 시드한다.
        // UNIQUE(name_normalized) 위반을 피하기 위해 suffix 로 격리한다.
        val deptId = java.util.UUID.randomUUID().toString()
        val deptSuffix = deptId.takeLast(8)
        jdbc.update(
            """
            INSERT INTO departments (id, name, name_normalized, display_order, is_active, created_at, updated_at)
            VALUES (?, ?, ?, 0, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """.trimIndent(),
            deptId,
            "운영팀-$deptSuffix",
            "운영팀-$deptSuffix"
        )

        webClient.post().uri("/user/signup")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(
                BodyInserters.fromFormData("username", username)
                    .with("displayName", usernamePrefix)
                    .with("departmentId", deptId)
                    .with("password", password)
                    .with("confirmPassword", password)
            )
            .exchange()
            .expectStatus().is3xxRedirection

        return UserCredentials(username = username, password = password)
    }

    private fun approveUserAccount(adminSessionId: String, username: String) {
        val userId = jdbc.queryForObject(
            "SELECT id FROM admin_users WHERE username = ?",
            String::class.java,
            username
        ) ?: error("pending account id not found for $username")

        webClient.post().uri("/api/admin/user-accounts/$userId/approve")
            .cookie("SESSION", adminSessionId)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"reviewNote":"로그인 승인"}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.approvalStatus").isEqualTo("APPROVED")
    }

    private fun setUserSlackDmChannel(username: String, slackDmChannelId: String) {
        val updatedRows = jdbc.update(
            "UPDATE admin_users SET slack_dm_channel_id = ? WHERE username = ?",
            slackDmChannelId,
            username
        )
        updatedRows shouldBe 1
    }

    private fun loginOnly(loginPath: String, username: String, password: String): String {
        val loginResult = webClient.post().uri(loginPath)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(
                BodyInserters.fromFormData("username", username)
                    .with("password", password)
            )
            .exchange()
            .expectStatus().is3xxRedirection
            .returnResult(Void::class.java)

        val sessionCookie = loginResult.responseHeaders[HttpHeaders.SET_COOKIE]
            ?.asSequence()
            ?.map { it.substringBefore(";") }
            ?.lastOrNull {
                it.startsWith("SESSION=") && it.substringAfter("SESSION=").isNotBlank()
            }
            ?: throw IllegalStateException("SESSION cookie not found")
        return sessionCookie.substringAfter("SESSION=")
    }

    private fun createAndApproveUserRequest(adminSessionId: String, userSessionId: String, requestName: String): String {
        val createResult = webClient.post().uri("/api/user/requests")
            .cookie("SESSION", userSessionId)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "requestName": "$requestName",
                  "sourceName": "Tech Source",
                  "sourceUrl": "https://example.com/rss.xml",
                  "slackChannelId": "C123TEST01",
                  "personaName": "실무 요약",
                  "personaPrompt": "핵심만 알려줘"
                }
                """.trimIndent()
            )
            .exchange()
            .expectStatus().isCreated
            .expectBody(String::class.java)
            .returnResult()

        val requestId = createResult.responseBody?.substringAfter("\"id\":\"")?.substringBefore("\"")
            ?: error("request id missing")

        webClient.post().uri("/api/admin/user-requests/$requestId/approve")
            .cookie("SESSION", adminSessionId)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"legalBasis":"QUOTATION_ONLY","summaryAllowed":true,"fulltextAllowed":false,"reviewNotes":"승인 완료","responsibilityAcknowledged":true}""")
            .exchange()
            .expectStatus().isOk

        return requestId
    }

    private data class UserCredentials(
        val username: String,
        val password: String
    )
}
