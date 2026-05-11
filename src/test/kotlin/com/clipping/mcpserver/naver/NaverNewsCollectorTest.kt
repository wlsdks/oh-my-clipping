package com.clipping.mcpserver.naver

import com.clipping.mcpserver.adapter.out.naver.NaverNewsCollector
import com.clipping.mcpserver.config.NaverProperties
import com.clipping.mcpserver.observability.ClippingMetrics
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.ZoneOffset

private val testMetrics = mockk<ClippingMetrics>(relaxed = true) {
    every { recordExternalApiCall<Any>(any(), any(), any()) } answers {
        thirdArg<() -> Any>().invoke()
    }
}

class NaverNewsCollectorTest {

    @Nested
    inner class `isConfigured 검증` {

        @Test
        fun `client-id와 secret이 모두 설정되면 true`() {
            val props = NaverProperties(clientId = "test-id", clientSecret = "test-secret")
            val collector = NaverNewsCollector(props, testMetrics)
            collector.isConfigured() shouldBe true
        }

        @Test
        fun `client-id가 비어있으면 false`() {
            val props = NaverProperties(clientId = "", clientSecret = "test-secret")
            val collector = NaverNewsCollector(props, testMetrics)
            collector.isConfigured() shouldBe false
        }

        @Test
        fun `client-secret이 비어있으면 false`() {
            val props = NaverProperties(clientId = "test-id", clientSecret = "")
            val collector = NaverNewsCollector(props, testMetrics)
            collector.isConfigured() shouldBe false
        }

        @Test
        fun `둘 다 비어있으면 false`() {
            val props = NaverProperties()
            val collector = NaverNewsCollector(props, testMetrics)
            collector.isConfigured() shouldBe false
        }
    }

    @Nested
    inner class `searchNews - API 미설정 시` {

        @Test
        fun `API 키가 미설정이면 빈 리스트 반환`() {
            val props = NaverProperties()
            val collector = NaverNewsCollector(props, testMetrics)
            collector.searchNews("테스트").shouldBeEmpty()
        }

        @Test
        fun `빈 쿼리는 빈 리스트 반환`() {
            val props = NaverProperties(clientId = "id", clientSecret = "secret")
            val collector = NaverNewsCollector(props, testMetrics)
            collector.searchNews("").shouldBeEmpty()
        }

        @Test
        fun `공백만 있는 쿼리도 빈 리스트 반환`() {
            val props = NaverProperties(clientId = "id", clientSecret = "secret")
            val collector = NaverNewsCollector(props, testMetrics)
            collector.searchNews("   ").shouldBeEmpty()
        }
    }

    @Nested
    inner class `stripHtmlTags 검증` {

        @Test
        fun `b 태그를 제거한다`() {
            NaverNewsCollector.stripHtmlTags("<b>키워드</b> 뉴스") shouldBe "키워드 뉴스"
        }

        @Test
        fun `여러 HTML 태그를 모두 제거한다`() {
            NaverNewsCollector.stripHtmlTags("<b>MegaCorp</b>전자 <i>실적</i> 발표") shouldBe "MegaCorp 실적 발표"
        }

        @Test
        fun `HTML 엔티티를 디코딩한다`() {
            NaverNewsCollector.stripHtmlTags("A &amp; B &lt;C&gt;") shouldBe "A & B <C>"
        }

        @Test
        fun `따옴표 엔티티를 디코딩한다`() {
            NaverNewsCollector.stripHtmlTags("&quot;hello&quot; &#39;world&#39;") shouldBe "\"hello\" 'world'"
        }

        @Test
        fun `태그 없는 텍스트는 그대로 반환`() {
            NaverNewsCollector.stripHtmlTags("일반 텍스트") shouldBe "일반 텍스트"
        }

        @Test
        fun `빈 문자열은 빈 문자열 반환`() {
            NaverNewsCollector.stripHtmlTags("") shouldBe ""
        }

        @Test
        fun `앞뒤 공백을 제거한다`() {
            NaverNewsCollector.stripHtmlTags("  텍스트  ") shouldBe "텍스트"
        }
    }

    @Nested
    inner class `parseRfc822Date 검증` {

        @Test
        fun `정상 RFC 822 날짜를 파싱한다`() {
            val result = NaverNewsCollector.parseRfc822Date("Fri, 03 Apr 2026 09:30:00 +0900")
            result.shouldNotBeNull()
            val zoned = result.atZone(ZoneOffset.ofHours(9))
            zoned.year shouldBe 2026
            zoned.monthValue shouldBe 4
            zoned.dayOfMonth shouldBe 3
            zoned.hour shouldBe 9
            zoned.minute shouldBe 30
        }

        @Test
        fun `빈 문자열은 null 반환`() {
            NaverNewsCollector.parseRfc822Date("").shouldBeNull()
        }

        @Test
        fun `잘못된 형식은 null 반환`() {
            NaverNewsCollector.parseRfc822Date("not-a-date").shouldBeNull()
        }
    }

    @Nested
    inner class `parseResponse 검증` {

        private val collector = NaverNewsCollector(NaverProperties(clientId = "id", clientSecret = "secret"), testMetrics)

        @Test
        fun `정상 응답을 파싱한다`() {
            val json = """
            {
                "lastBuildDate": "Fri, 03 Apr 2026 09:00:00 +0900",
                "total": 2,
                "start": 1,
                "display": 2,
                "items": [
                    {
                        "title": "<b>MegaCorp</b>전자 실적 발표",
                        "originallink": "https://example.com/1",
                        "link": "https://n.news.naver.com/1",
                        "description": "<b>MegaCorp</b>전자가 1분기 실적을 발표했다.",
                        "pubDate": "Fri, 03 Apr 2026 08:00:00 +0900"
                    },
                    {
                        "title": "AI 반도체 시장 성장",
                        "originallink": "https://example.com/2",
                        "link": "https://n.news.naver.com/2",
                        "description": "AI 반도체 시장이 빠르게 성장하고 있다.",
                        "pubDate": "Fri, 03 Apr 2026 07:30:00 +0900"
                    }
                ]
            }
            """.trimIndent()

            val result = collector.parseResponse(json)

            result shouldHaveSize 2
            result[0].title shouldBe "MegaCorp 실적 발표"
            result[0].link shouldBe "https://n.news.naver.com/1"
            result[0].description shouldBe "MegaCorp가 1분기 실적을 발표했다."
            result[0].publishedAt.shouldNotBeNull()

            result[1].title shouldBe "AI 반도체 시장 성장"
        }

        @Test
        fun `빈 items 배열은 빈 리스트 반환`() {
            val json = """{"items": []}"""
            collector.parseResponse(json) shouldHaveSize 0
        }

        @Test
        fun `items 필드가 없으면 빈 리스트 반환`() {
            val json = """{"total": 0}"""
            collector.parseResponse(json).shouldBeEmpty()
        }

        @Test
        fun `잘못된 JSON은 빈 리스트 반환`() {
            collector.parseResponse("not json").shouldBeEmpty()
        }

        @Test
        fun `title이 비어있는 항목은 건너뛴다`() {
            val json = """
            {
                "items": [
                    { "title": "", "link": "https://example.com/1", "description": "desc", "pubDate": "" },
                    { "title": "정상 제목", "link": "https://example.com/2", "description": "desc", "pubDate": "" }
                ]
            }
            """.trimIndent()

            val result = collector.parseResponse(json)
            result shouldHaveSize 1
            result[0].title shouldBe "정상 제목"
        }

        @Test
        fun `link가 비어있는 항목은 건너뛴다`() {
            val json = """
            {
                "items": [
                    { "title": "제목", "link": "", "description": "desc", "pubDate": "" }
                ]
            }
            """.trimIndent()

            collector.parseResponse(json).shouldBeEmpty()
        }
    }
}
