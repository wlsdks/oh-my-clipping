package com.ohmyclipping.support

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.net.URI

class GoogleNewsRssUrlBuilderTest {

    @Nested
    inner class `URL 생성` {

        @Test
        fun `키워드를 OR로 연결한 Google News RSS URL을 생성한다`() {
            val url = GoogleNewsRssUrlBuilder.buildUrl(listOf("MegaCorp", "갤럭시"))

            url.shouldNotBeNull()
            url shouldContain "OR"
            url shouldContain "hl=ko"
            url shouldContain "gl=KR"
        }

        @Test
        fun `키워드가 1개일 때 OR 없이 URL을 생성한다`() {
            val url = GoogleNewsRssUrlBuilder.buildUrl(listOf("MessengerCo"))

            url.shouldNotBeNull()
            url shouldNotContain "OR"
            url shouldContain "hl=ko"
            url shouldContain "gl=KR"
        }
    }

    @Nested
    inner class `빈 입력 처리` {

        @Test
        fun `빈 키워드 리스트는 null을 반환한다`() {
            val url = GoogleNewsRssUrlBuilder.buildUrl(emptyList())

            url.shouldBeNull()
        }

        @Test
        fun `모든 키워드가 공백이면 null을 반환한다`() {
            val url = GoogleNewsRssUrlBuilder.buildUrl(listOf("", "  "))

            url.shouldBeNull()
        }

        @Test
        fun `공백 키워드는 무시한다`() {
            val url = GoogleNewsRssUrlBuilder.buildUrl(listOf("GammaLearn", "", "  ", "DeltaClass"))

            url.shouldNotBeNull()
            // 유효한 키워드만 포함되어야 함 — 빈 세그먼트 없음
            url shouldNotContain "OR+OR"
            url shouldNotContain "+OR+OR+"
        }
    }

    @Nested
    inner class `인코딩` {

        @Test
        fun `특수문자가 포함된 키워드를 안전하게 인코딩한다`() {
            val url = GoogleNewsRssUrlBuilder.buildUrl(listOf("MegaCorp(주)"))

            url.shouldNotBeNull()
            // URI로 파싱 가능해야 함 (인코딩이 올바른 경우)
            URI(url) shouldBe URI(url)
        }
    }
}
