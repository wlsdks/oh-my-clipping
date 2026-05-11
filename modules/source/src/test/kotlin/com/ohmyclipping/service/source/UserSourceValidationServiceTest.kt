package com.ohmyclipping.service.source

import com.ohmyclipping.model.RssSource
import com.ohmyclipping.model.SourceLegalBasis
import com.ohmyclipping.service.port.SourceUrlSafetyPort
import com.ohmyclipping.store.RssSourceStore
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.URI

class UserSourceValidationServiceTest {

    private val sourceStore = mockk<RssSourceStore>()
    private val urlSafetyValidator = mockk<SourceUrlSafetyPort>()
    private val verificationClient = mockk<SourceVerificationClient>()

    private val service = UserSourceValidationService(
        sourceStore = sourceStore,
        urlSafetyValidator = urlSafetyValidator,
        sourceVerificationClient = verificationClient
    )

    private fun makeSource(
        url: String,
        legalBasis: SourceLegalBasis = SourceLegalBasis.QUOTATION_ONLY,
        name: String = "test"
    ) = RssSource(
        id = "s1",
        name = name,
        url = url,
        categoryId = "cat1",
        legalBasis = legalBasis
    )

    @Nested
    inner class `도메인 PROHIBITED 체크` {
        @Test
        fun `PROHIBITED 도메인이면 차단 결과를 반환한다`() {
            val url = "https://blocked.example.com/feed"
            every { urlSafetyValidator.validatePublicHttpUrl(url) } returns URI(url)
            every { sourceStore.list(any()) } returns listOf(
                makeSource("https://blocked.example.com/rss", SourceLegalBasis.PROHIBITED)
            )

            val result = service.validate(url)

            result.domainBlocked shouldBe true
            result.blockReason shouldBe "이 도메인은 저작권 사유로 사용이 금지되어 있어요."
        }
    }

    @Nested
    inner class `기존 도메인 정보 조회` {
        @Test
        fun `이미 등록된 도메인이면 기존 소스 정보를 반환한다`() {
            val url = "https://techcrunch.com/feed"
            every { urlSafetyValidator.validatePublicHttpUrl(url) } returns URI(url)
            every { sourceStore.list(any()) } returns listOf(
                makeSource("https://techcrunch.com/rss", name = "TechCrunch")
            )
            every { verificationClient.verify(URI(url)) } returns VerificationResult.VERIFIED

            val result = service.validate(url)

            result.existingSource?.name shouldBe "TechCrunch"
            result.existingSource?.legalBasis shouldBe "QUOTATION_ONLY"
            result.rssValid shouldBe true
        }
    }

    @Nested
    inner class `RSS 검증` {
        @Test
        fun `RSS가 유효하면 rssValid=true를 반환한다`() {
            val url = "https://example.com/feed"
            every { urlSafetyValidator.validatePublicHttpUrl(url) } returns URI(url)
            every { sourceStore.list(any()) } returns emptyList()
            every { verificationClient.verify(URI(url)) } returns VerificationResult.VERIFIED

            val result = service.validate(url)

            result.rssValid shouldBe true
            result.robotsAllowed shouldBe true
            result.domainBlocked shouldBe false
        }

        @Test
        fun `RSS 파싱 실패 시 rssValid=false를 반환한다`() {
            val url = "https://example.com/page"
            every { urlSafetyValidator.validatePublicHttpUrl(url) } returns URI(url)
            every { sourceStore.list(any()) } returns emptyList()
            every { verificationClient.verify(URI(url)) } returns VerificationResult.FEED_ERROR

            val result = service.validate(url)

            result.rssValid shouldBe false
            result.robotsAllowed shouldBe true
        }
    }

    @Nested
    inner class `robots_txt 차단` {
        @Test
        fun `robots_txt 차단 시 robotsAllowed=false를 반환한다`() {
            val url = "https://example.com/feed"
            every { urlSafetyValidator.validatePublicHttpUrl(url) } returns URI(url)
            every { sourceStore.list(any()) } returns emptyList()
            every { verificationClient.verify(URI(url)) } returns VerificationResult.ROBOTS_BLOCKED

            val result = service.validate(url)

            result.robotsAllowed shouldBe false
            result.rssValid shouldBe false
        }
    }

    @Nested
    inner class `외부 요청 실패` {
        @Test
        fun `외부 요청 예외 시 경고 상태를 반환한다`() {
            val url = "https://example.com/feed"
            every { urlSafetyValidator.validatePublicHttpUrl(url) } returns URI(url)
            every { sourceStore.list(any()) } returns emptyList()
            every { verificationClient.verify(URI(url)) } throws IOException("timeout")

            val result = service.validate(url)

            result.rssValid shouldBe false
            result.robotsAllowed shouldBe true
            result.domainBlocked shouldBe false
        }

        @Test
        fun `리다이렉트 URL 검증 실패 시 경고 상태와 기존 도메인 정보를 반환한다`() {
            val url = "https://example.com/feed"
            every { urlSafetyValidator.validatePublicHttpUrl(url) } returns URI(url)
            every { sourceStore.list(any()) } returns listOf(
                makeSource("https://example.com/rss", name = "Example RSS")
            )
            every { verificationClient.verify(URI(url)) } throws IllegalArgumentException("blocked redirect")

            val result = service.validate(url)

            result.rssValid shouldBe false
            result.robotsAllowed shouldBe true
            result.domainBlocked shouldBe false
            result.existingSource?.name shouldBe "Example RSS"
        }

        @Test
        fun `보안 예외 시 제출 가능 경고 상태를 반환한다`() {
            val url = "https://example.com/feed"
            every { urlSafetyValidator.validatePublicHttpUrl(url) } returns URI(url)
            every { sourceStore.list(any()) } returns emptyList()
            every { verificationClient.verify(URI(url)) } throws SecurityException("blocked by policy")

            val result = service.validate(url)

            result.rssValid shouldBe false
            result.robotsAllowed shouldBe true
            result.domainBlocked shouldBe false
            result.existingSource shouldBe null
        }
    }

    @Nested
    inner class `신규 도메인` {
        @Test
        fun `등록된 소스가 없으면 existingSource=null을 반환한다`() {
            val url = "https://new-site.com/feed"
            every { urlSafetyValidator.validatePublicHttpUrl(url) } returns URI(url)
            every { sourceStore.list(any()) } returns emptyList()
            every { verificationClient.verify(URI(url)) } returns VerificationResult.VERIFIED

            val result = service.validate(url)

            result.existingSource shouldBe null
            result.rssValid shouldBe true
        }
    }
}
