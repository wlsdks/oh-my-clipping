package com.ohmyclipping.service

import com.ohmyclipping.config.AppProperties
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class TrackingUrlParserTest {

    private val app = AppProperties(baseUrl = "https://clipping.example.com")
    private val parser = TrackingUrlParser(app)

    @Nested
    inner class `path-based tracking URL` {

        @Test
        fun `slack path 형식에서 summaryId 를 추출한다`() {
            val url = "https://clipping.example.com/api/track/click/slack/sum-abc?url=https%3A%2F%2Fnews.test%2Fa"
            parser.extractSummaryId(url) shouldBe "sum-abc"
        }

        @Test
        fun `URL-encoded summaryId 를 디코드한다`() {
            // "sum abc" 를 URL-encode 한 경로
            val url = "https://clipping.example.com/api/track/click/slack/sum%20abc"
            parser.extractSummaryId(url) shouldBe "sum abc"
        }

        @Test
        fun `trailing path segment 가 있으면 매칭되지 않는다`() {
            val url = "https://clipping.example.com/api/track/click/slack/sum-1/extra"
            parser.extractSummaryId(url).shouldBeNull()
        }
    }

    @Nested
    inner class `legacy query tracking URL` {

        @Test
        fun `legacy sid 쿼리 파라미터를 추출한다`() {
            val url = "https://clipping.example.com/api/track/click?sid=sum-legacy&url=https%3A%2F%2Fx"
            parser.extractSummaryId(url) shouldBe "sum-legacy"
        }

        @Test
        fun `sid 파라미터가 없으면 null 을 반환한다`() {
            val url = "https://clipping.example.com/api/track/click?url=https%3A%2F%2Fx"
            parser.extractSummaryId(url).shouldBeNull()
        }

        @Test
        fun `legacy path 에 sid 만 있어도 정상 추출한다`() {
            val url = "https://clipping.example.com/api/track/click?sid=sum-only"
            parser.extractSummaryId(url) shouldBe "sum-only"
        }
    }

    @Nested
    inner class `host 검증` {

        @Test
        fun `다른 host 의 tracking-like URL 은 거부한다`() {
            val url = "https://evil.example.com/api/track/click/slack/sum-1"
            parser.extractSummaryId(url).shouldBeNull()
        }

        @Test
        fun `baseUrl 이 비어있으면 host 검증을 skip 한다`() {
            val openParser = TrackingUrlParser(AppProperties(baseUrl = ""))
            val url = "https://any.host/api/track/click/slack/sum-1"
            openParser.extractSummaryId(url) shouldBe "sum-1"
        }
    }

    @Nested
    inner class `비 tracking URL` {

        @Test
        fun `아예 다른 경로는 null`() {
            parser.extractSummaryId("https://clipping.example.com/dashboard").shouldBeNull()
        }

        @Test
        fun `malformed URL 은 null`() {
            parser.extractSummaryId("not a url at all").shouldBeNull()
        }

        @Test
        fun `빈 문자열은 null`() {
            parser.extractSummaryId("").shouldBeNull()
        }

        @Test
        fun `tracking path 지만 sid segment 가 비어있으면 매칭되지 않는다`() {
            // path regex 가 ([^/?#]+) 이므로 빈 sid 는 매칭되지 않음
            parser.extractSummaryId("https://clipping.example.com/api/track/click/slack/").shouldBeNull()
        }
    }
}
