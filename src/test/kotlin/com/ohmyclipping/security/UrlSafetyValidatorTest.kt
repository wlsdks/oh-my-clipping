package com.ohmyclipping.security

import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class UrlSafetyValidatorTest {

    private val validator = UrlSafetyValidator()

    @Test
    fun `allows public http url`() {
        val uri = validator.validatePublicHttpUrl("https://93.184.216.34/path")
        uri.host shouldBe "93.184.216.34"
    }

    @Test
    fun `rejects localhost url`() {
        val ex = assertThrows<IllegalArgumentException> {
            validator.validatePublicHttpUrl("https://localhost/feed.xml")
        }
        ex.message shouldContain "localhost"
    }

    @Test
    fun `rejects private network ipv4`() {
        val ex = assertThrows<IllegalArgumentException> {
            validator.validatePublicHttpUrl("https://10.1.2.3/feed.xml")
        }
        ex.message shouldContain "내부 네트워크"
    }

    @Test
    fun `rejects loopback ipv4`() {
        val ex = assertThrows<IllegalArgumentException> {
            validator.validatePublicHttpUrl("https://127.0.0.1/feed.xml")
        }
        ex.message shouldContain "내부 네트워크"
    }

    @Test
    fun `rejects special purpose ipv4 ranges`() {
        val blockedUrls = listOf(
            "https://0.1.2.3/feed.xml",
            "https://100.64.0.1/feed.xml",
            "https://192.0.0.1/feed.xml",
            "https://192.0.2.1/feed.xml",
            "https://198.18.0.1/feed.xml",
            "https://198.51.100.1/feed.xml",
            "https://203.0.113.1/feed.xml",
            "https://224.0.0.1/feed.xml",
        )

        blockedUrls.forEach { url ->
            val ex = assertThrows<IllegalArgumentException> {
                validator.validatePublicHttpUrl(url)
            }
            ex.message shouldContain "내부 네트워크"
        }
    }

    @Test
    fun `rejects ipv4 mapped ipv6 loopback`() {
        val ex = assertThrows<IllegalArgumentException> {
            validator.validatePublicHttpUrl("https://[::ffff:127.0.0.1]/feed.xml")
        }
        ex.message shouldContain "내부 네트워크"
    }

    @Test
    fun `rejects integer encoded loopback ipv4`() {
        val ex = assertThrows<IllegalArgumentException> {
            validator.validatePublicHttpUrl("https://2130706433/feed.xml")
        }
        ex.message shouldContain "내부 네트워크"
    }

    @Test
    fun `rejects non http scheme`() {
        val ex = assertThrows<IllegalArgumentException> {
            validator.validatePublicHttpUrl("file:///etc/passwd")
        }
        ex.message shouldContain "http"
    }
}
