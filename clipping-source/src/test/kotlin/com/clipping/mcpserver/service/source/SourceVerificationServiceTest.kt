package com.clipping.mcpserver.service.source

import com.clipping.mcpserver.error.NotFoundException
import com.clipping.mcpserver.model.RssSource
import com.clipping.mcpserver.service.port.SourceUrlSafetyPort
import com.clipping.mcpserver.store.RssSourceStore
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URI

class SourceVerificationServiceTest {

    private val sourceStore = mockk<RssSourceStore>()
    private val urlSafetyValidator = mockk<SourceUrlSafetyPort>()
    private val verificationClient = mockk<SourceVerificationClient>()

    private val service = SourceVerificationService(sourceStore, urlSafetyValidator, verificationClient)

    @Nested
    inner class `verify 메서드` {

        @Test
        fun `존재하지 않는 sourceId이면 NotFoundException을 던진다`() {
            every { sourceStore.findById("missing-src") } returns null

            val exception = shouldThrow<NotFoundException> {
                service.verify("missing-src")
            }

            exception.message shouldBe "Source not found: missing-src"
            verify(exactly = 0) { urlSafetyValidator.validatePublicHttpUrl(any()) }
            verify(exactly = 0) { sourceStore.updateVerificationStatus(any(), any()) }
        }

        @Test
        fun `URL 안전성 검증 실패 시 BLOCKED_URL 결과를 반환하고 DB를 갱신한다`() {
            val source = testSource("src-blocked")
            every { sourceStore.findById("src-blocked") } returns source
            every { urlSafetyValidator.validatePublicHttpUrl(source.url) } throws
                IllegalArgumentException("Private IP address is not allowed")
            every { sourceStore.updateVerificationStatus("src-blocked", "BLOCKED_URL") } just Runs

            val result = service.verify("src-blocked")

            result shouldBe VerificationResult.BLOCKED_URL
            verify(exactly = 1) { sourceStore.updateVerificationStatus("src-blocked", "BLOCKED_URL") }
            verify(exactly = 0) { verificationClient.verify(any()) }
        }

        @Test
        fun `타임아웃 발생 시 TIMEOUT 결과를 반환하고 DB를 갱신한다`() {
            val source = testSource("src-timeout")
            val uri = URI("https://example.com/rss")
            every { sourceStore.findById("src-timeout") } returns source
            every { urlSafetyValidator.validatePublicHttpUrl(source.url) } returns uri
            every { verificationClient.verify(uri) } throws SocketTimeoutException("Read timed out")
            every { sourceStore.updateVerificationStatus("src-timeout", "TIMEOUT") } just Runs

            val result = service.verify("src-timeout")

            result shouldBe VerificationResult.TIMEOUT
            verify(exactly = 1) { sourceStore.updateVerificationStatus("src-timeout", "TIMEOUT") }
        }

        @Test
        fun `정상 검증 시 VERIFIED 결과를 반환하고 DB를 갱신한다`() {
            val source = testSource("src-ok")
            val uri = URI("https://example.com/rss")
            every { sourceStore.findById("src-ok") } returns source
            every { urlSafetyValidator.validatePublicHttpUrl(source.url) } returns uri
            every { verificationClient.verify(uri) } returns VerificationResult.VERIFIED
            every { sourceStore.updateVerificationStatus("src-ok", "VERIFIED") } just Runs

            val result = service.verify("src-ok")

            result shouldBe VerificationResult.VERIFIED
            verify(exactly = 1) { sourceStore.updateVerificationStatus("src-ok", "VERIFIED") }
        }

        @Test
        fun `일반 예외 발생 시 FEED_ERROR 결과를 반환하고 DB를 갱신한다`() {
            val source = testSource("src-err")
            val uri = URI("https://example.com/rss")
            every { sourceStore.findById("src-err") } returns source
            every { urlSafetyValidator.validatePublicHttpUrl(source.url) } returns uri
            every { verificationClient.verify(uri) } throws IOException("XML parse error")
            every { sourceStore.updateVerificationStatus("src-err", "FEED_ERROR") } just Runs

            val result = service.verify("src-err")

            result shouldBe VerificationResult.FEED_ERROR
            verify(exactly = 1) { sourceStore.updateVerificationStatus("src-err", "FEED_ERROR") }
        }

        @Test
        fun `검증 결과가 ROBOTS_BLOCKED이면 해당 상태로 DB를 갱신한다`() {
            val source = testSource("src-robots")
            val uri = URI("https://example.com/rss")
            every { sourceStore.findById("src-robots") } returns source
            every { urlSafetyValidator.validatePublicHttpUrl(source.url) } returns uri
            every { verificationClient.verify(uri) } returns VerificationResult.ROBOTS_BLOCKED
            every { sourceStore.updateVerificationStatus("src-robots", "ROBOTS_BLOCKED") } just Runs

            val result = service.verify("src-robots")

            result shouldBe VerificationResult.ROBOTS_BLOCKED
            verify(exactly = 1) { sourceStore.updateVerificationStatus("src-robots", "ROBOTS_BLOCKED") }
        }
    }

    private fun testSource(id: String): RssSource =
        RssSource(
            id = id,
            name = "테스트 소스",
            url = "https://example.com/rss",
            categoryId = "cat-1"
        )
}
