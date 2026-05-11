package com.clipping.mcpserver.support

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.net.URI

class UrlCanonicalizerTest {

    @Test
    fun `tracking parameters and fragment are removed`() {
        UrlCanonicalizer.canonicalizeToString(
            "HTTPS://Example.COM:443/news/1?utm_source=slack&id=42&fbclid=abc#comments"
        ) shouldBe "https://example.com/news/1?id=42"
    }

    @Test
    fun `content identifying query parameters are preserved and sorted`() {
        UrlCanonicalizer.canonicalizeToString(
            "https://example.com/article?b=2&a=1"
        ) shouldBe "https://example.com/article?a=1&b=2"
    }

    @Test
    fun `all tracking query parameters collapse to no query`() {
        UrlCanonicalizer.canonicalize(URI("http://example.com:80/a?utm_medium=rss&gclid=1")).toString() shouldBe
            "http://example.com/a"
    }

    @Test
    fun `blank URL remains blank`() {
        UrlCanonicalizer.canonicalizeToString("   ") shouldBe ""
    }

    @Test
    fun `invalid URL falls back to trimmed original`() {
        UrlCanonicalizer.canonicalizeToString(" https://example .com/news?utm_source=x ") shouldBe
            "https://example .com/news?utm_source=x"
    }

    @Test
    fun `encoded tracking parameter names are removed case insensitively`() {
        UrlCanonicalizer.canonicalizeToString(
            "https://example.com/a?UTM%5Fsource=mail&id=42&Ref=homepage"
        ) shouldBe "https://example.com/a?id=42"
    }

    @Test
    fun `duplicate content parameters are preserved for content identity`() {
        UrlCanonicalizer.canonicalizeToString(
            "https://example.com/search?tag=ai&tag=llm&utm_campaign=x"
        ) shouldBe "https://example.com/search?tag=ai&tag=llm"
    }

    @Test
    fun `non default ports are preserved`() {
        UrlCanonicalizer.canonicalizeToString(
            "HTTPS://Example.COM:8443/news?id=42&utm_source=x#fragment"
        ) shouldBe "https://example.com:8443/news?id=42"
    }

    @Test
    fun `encoded path and query values are not double encoded`() {
        UrlCanonicalizer.canonicalizeToString(
            "https://example.com/%EA%B8%B0%EC%82%AC?q=%ED%85%8C%EC%8A%A4%ED%8A%B8&utm_source=x"
        ) shouldBe "https://example.com/%EA%B8%B0%EC%82%AC?q=%ED%85%8C%EC%8A%A4%ED%8A%B8"
    }

    @Test
    fun `query parameter without value is preserved when it is not tracking`() {
        UrlCanonicalizer.canonicalizeToString(
            "https://example.com/news?print&utm_medium=rss&id=42"
        ) shouldBe "https://example.com/news?id=42&print"
    }

    @Test
    fun `ipv6 host is preserved while default port and tracking params are removed`() {
        UrlCanonicalizer.canonicalizeToString(
            "HTTP://[::1]:80/news?utm_source=x&id=42#section"
        ) shouldBe "http://[::1]/news?id=42"
    }

    @TestFactory
    fun `canonicalization bad case matrix`(): List<DynamicTest> {
        val cases = listOf(
            // Tracking params: exact names, casing, and encoded names.
            "https://example.com/a?utm_source=x&id=1" to "https://example.com/a?id=1",
            "https://example.com/a?id=1&utm_medium=email" to "https://example.com/a?id=1",
            "https://example.com/a?utm_campaign=spring&id=1" to "https://example.com/a?id=1",
            "https://example.com/a?utm_term=ai&id=1" to "https://example.com/a?id=1",
            "https://example.com/a?utm_content=button&id=1" to "https://example.com/a?id=1",
            "https://example.com/a?UTM_SOURCE=x&id=1" to "https://example.com/a?id=1",
            "https://example.com/a?utm%5Fsource=x&id=1" to "https://example.com/a?id=1",
            "https://example.com/a?Utm%5FMedium=x&id=1" to "https://example.com/a?id=1",
            "https://example.com/a?fbclid=x&id=1" to "https://example.com/a?id=1",
            "https://example.com/a?gclid=x&id=1" to "https://example.com/a?id=1",
            "https://example.com/a?gbraid=x&id=1" to "https://example.com/a?id=1",
            "https://example.com/a?wbraid=x&id=1" to "https://example.com/a?id=1",
            "https://example.com/a?msclkid=x&id=1" to "https://example.com/a?id=1",
            "https://example.com/a?igshid=x&id=1" to "https://example.com/a?id=1",
            "https://example.com/a?mc_cid=x&id=1" to "https://example.com/a?id=1",
            "https://example.com/a?mc_eid=x&id=1" to "https://example.com/a?id=1",
            "https://example.com/a?spm=x&id=1" to "https://example.com/a?id=1",
            "https://example.com/a?ref=x&id=1" to "https://example.com/a?id=1",
            "https://example.com/a?ref_src=x&id=1" to "https://example.com/a?id=1",

            // Query identity: content params stay, order is stable, duplicates stay.
            "https://example.com/a?b=2&a=1" to "https://example.com/a?a=1&b=2",
            "https://example.com/a?z=9&m=5&a=1" to "https://example.com/a?a=1&m=5&z=9",
            "https://example.com/a?id=2&id=1" to "https://example.com/a?id=1&id=2",
            "https://example.com/a?tag=ai&tag=llm" to "https://example.com/a?tag=ai&tag=llm",
            "https://example.com/a?print&id=1" to "https://example.com/a?id=1&print",
            "https://example.com/a?empty=&id=1" to "https://example.com/a?empty=&id=1",
            "https://example.com/a?encoded=%ED%85%8C%EC%8A%A4%ED%8A%B8&id=1" to
                "https://example.com/a?encoded=%ED%85%8C%EC%8A%A4%ED%8A%B8&id=1",
            "https://example.com/a?space=hello+world&id=1" to "https://example.com/a?id=1&space=hello+world",

            // Fragment, case, port, and path normalization.
            "HTTPS://EXAMPLE.COM/a?id=1#frag" to "https://example.com/a?id=1",
            "http://EXAMPLE.COM:80/a?id=1#frag" to "http://example.com/a?id=1",
            "https://EXAMPLE.COM:443/a?id=1#frag" to "https://example.com/a?id=1",
            "http://EXAMPLE.COM:8080/a?id=1#frag" to "http://example.com:8080/a?id=1",
            "https://EXAMPLE.COM:8443/a?id=1#frag" to "https://example.com:8443/a?id=1",
            "https://example.com/a/../b?id=1&utm_source=x" to "https://example.com/b?id=1",
            "https://example.com/a/./b?id=1&utm_source=x" to "https://example.com/a/b?id=1",
            "https://example.com/%EA%B8%B0%EC%82%AC?b=2&a=1&utm_source=x" to
                "https://example.com/%EA%B8%B0%EC%82%AC?a=1&b=2",
            "HTTP://[::1]:80/a?id=1&utm_source=x#frag" to "http://[::1]/a?id=1",
            "HTTPS://[2001:db8::1]:443/a?id=1&utm_source=x#frag" to "https://[2001:db8::1]/a?id=1",

            // All tracking params collapse to no query.
            "https://example.com/a?utm_source=x&utm_medium=y" to "https://example.com/a",
            "https://example.com/a?fbclid=x&gclid=y" to "https://example.com/a",
            "https://example.com/a?ref=x&ref_src=y#frag" to "https://example.com/a",

            // Invalid or non-hierarchical input falls back instead of throwing.
            " https://example .com/a?utm_source=x " to "https://example .com/a?utm_source=x",
            "not a url?utm_source=x" to "not a url?utm_source=x",
            "mailto:test@example.com?utm_source=x" to "mailto:test@example.com?utm_source=x",
            "urn:example:test?utm_source=x" to "urn:example:test?utm_source=x"
        )

        val generatedTrackingCases = listOf(
            "utm_id",
            "utm_reader",
            "utm_name",
            "utm_social",
            "utm_social-type",
            "utm_brand",
            "utm_creative_format",
            "utm_marketing_tactic",
            "utm_source_platform",
            "utm_source"
        ).mapIndexed { index, param ->
            "https://example.com/generated-$index?$param=x&id=$index" to
                "https://example.com/generated-$index?id=$index"
        }

        return (cases + generatedTrackingCases).map { (raw, expected) ->
            DynamicTest.dynamicTest("$raw -> $expected") {
                UrlCanonicalizer.canonicalizeToString(raw) shouldBe expected
            }
        }
    }
}
