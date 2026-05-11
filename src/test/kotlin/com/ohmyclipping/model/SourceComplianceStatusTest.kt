package com.ohmyclipping.model

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class SourceComplianceStatusTest {

    @Nested
    inner class `fromRaw 파싱` {
        @Test
        fun `정확한 대문자 enum 이름은 그대로 파싱한다`() {
            SourceComplianceStatus.fromRaw("EXPIRED") shouldBe SourceComplianceStatus.EXPIRED
            SourceComplianceStatus.fromRaw("EXPIRING_SOON") shouldBe SourceComplianceStatus.EXPIRING_SOON
            SourceComplianceStatus.fromRaw("NEVER_REVIEWED") shouldBe SourceComplianceStatus.NEVER_REVIEWED
            SourceComplianceStatus.fromRaw("VALID") shouldBe SourceComplianceStatus.VALID
        }

        @Test
        fun `소문자 입력은 대소문자 무시로 파싱한다`() {
            SourceComplianceStatus.fromRaw("expired") shouldBe SourceComplianceStatus.EXPIRED
            SourceComplianceStatus.fromRaw("Expiring_Soon") shouldBe SourceComplianceStatus.EXPIRING_SOON
        }

        @Test
        fun `앞뒤 공백은 trim 한다`() {
            SourceComplianceStatus.fromRaw("  VALID  ") shouldBe SourceComplianceStatus.VALID
        }

        @Test
        fun `null, 빈 문자열, 공백만 있는 문자열은 null 을 반환한다`() {
            SourceComplianceStatus.fromRaw(null) shouldBe null
            SourceComplianceStatus.fromRaw("") shouldBe null
            SourceComplianceStatus.fromRaw("   ") shouldBe null
        }

        @Test
        fun `알 수 없는 값은 null 을 반환한다 (서비스 레이어가 거부 처리)`() {
            SourceComplianceStatus.fromRaw("UNKNOWN") shouldBe null
            SourceComplianceStatus.fromRaw("ok") shouldBe null
        }
    }
}
