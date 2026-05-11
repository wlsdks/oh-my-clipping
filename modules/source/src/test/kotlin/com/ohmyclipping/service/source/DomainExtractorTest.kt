package com.ohmyclipping.service.source

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class DomainExtractorTest {

    @Nested
    inner class `일반 도메인 추출` {
        @Test
        fun `단순 도메인에서 등록 도메인을 추출한다`() {
            DomainExtractor.extract("https://techcrunch.com/feed") shouldBe "techcrunch.com"
        }

        @Test
        fun `서브도메인이 있는 URL에서 등록 도메인을 추출한다`() {
            DomainExtractor.extract("https://news.example.com/rss") shouldBe "example.com"
        }

        @Test
        fun `www 접두사가 있는 URL을 처리한다`() {
            DomainExtractor.extract("https://www.bbc.com/news") shouldBe "bbc.com"
        }
    }

    @Nested
    inner class `ccSLD 도메인 추출` {
        @Test
        fun `co_kr 도메인에서 3세그먼트를 추출한다`() {
            DomainExtractor.extract("https://news.example-press.co.kr/feed") shouldBe "example-press.co.kr"
        }

        @Test
        fun `or_kr 도메인을 처리한다`() {
            DomainExtractor.extract("https://www.bok.or.kr/portal") shouldBe "bok.or.kr"
        }

        @Test
        fun `co_jp 도메인을 처리한다`() {
            DomainExtractor.extract("https://news.yahoo.co.jp/rss") shouldBe "yahoo.co.jp"
        }

        @Test
        fun `co_uk 도메인을 처리한다`() {
            DomainExtractor.extract("https://www.bbc.co.uk/news") shouldBe "bbc.co.uk"
        }

        @Test
        fun `com_au 도메인을 처리한다`() {
            DomainExtractor.extract("https://www.news.com.au/feed") shouldBe "news.com.au"
        }
    }

    @Nested
    inner class `extractFromHost` {
        @Test
        fun `호스트에서 직접 도메인을 추출한다`() {
            DomainExtractor.extractFromHost("news.example.com") shouldBe "example.com"
        }

        @Test
        fun `ccSLD 호스트에서 3세그먼트를 추출한다`() {
            DomainExtractor.extractFromHost("blog.example-press.co.kr") shouldBe "example-press.co.kr"
        }

        @Test
        fun `단일 세그먼트는 null을 반환한다`() {
            DomainExtractor.extractFromHost("localhost") shouldBe null
        }
    }

    @Nested
    inner class `엣지 케이스` {
        @Test
        fun `잘못된 URL은 null을 반환한다`() {
            DomainExtractor.extract("not-a-url") shouldBe null
        }

        @Test
        fun `빈 문자열은 null을 반환한다`() {
            DomainExtractor.extract("") shouldBe null
        }

        @Test
        fun `스킴 없는 URL은 null을 반환한다`() {
            DomainExtractor.extract("example.com/feed") shouldBe null
        }

        @Test
        fun `대소문자를 구분하지 않는다`() {
            DomainExtractor.extract("https://NEWS.Example.COM/rss") shouldBe "example.com"
        }
    }
}
