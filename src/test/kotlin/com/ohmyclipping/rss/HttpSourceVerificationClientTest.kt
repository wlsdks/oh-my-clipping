package com.ohmyclipping.rss

import com.ohmyclipping.security.UrlSafetyValidator
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class HttpSourceVerificationClientTest {

    private val urlSafetyValidator = UrlSafetyValidator()
    private val client = HttpSourceVerificationClient(urlSafetyValidator)

    @Nested
    inner class `Google News robots bypass` {

        @Test
        fun `ROBOTS_BYPASS_HOSTS에 news_google_com이 포함되어 있다`() {
            HttpSourceVerificationClient.ROBOTS_BYPASS_HOSTS shouldContain "news.google.com"
        }

        @Test
        fun `일반 도메인은 bypass 대상이 아니다`() {
            ("example.com" in HttpSourceVerificationClient.ROBOTS_BYPASS_HOSTS) shouldBe false
        }
    }

    @Test
    fun `parseRobotsTxt should detect blocked path`() {
        val robots = """
            User-agent: *
            Disallow: /feed
            Disallow: /private
        """.trimIndent()
        client.parseRobotsTxt(robots, "/feed/rss") shouldBe true
        client.parseRobotsTxt(robots, "/private/data") shouldBe true
    }

    @Test
    fun `parseRobotsTxt should allow non-blocked path`() {
        val robots = """
            User-agent: *
            Disallow: /private
        """.trimIndent()
        client.parseRobotsTxt(robots, "/feed/rss") shouldBe false
    }

    @Test
    fun `parseRobotsTxt should handle empty disallow`() {
        val robots = """
            User-agent: *
            Disallow:
        """.trimIndent()
        client.parseRobotsTxt(robots, "/anything") shouldBe false
    }

    @Test
    fun `parseRobotsTxt should only check wildcard user-agent`() {
        val robots = """
            User-agent: Googlebot
            Disallow: /feed

            User-agent: *
            Disallow: /secret
        """.trimIndent()
        client.parseRobotsTxt(robots, "/feed/rss") shouldBe false
        client.parseRobotsTxt(robots, "/secret/data") shouldBe true
    }
}
