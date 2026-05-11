package com.ohmyclipping.config

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.Base64

class EncryptionServiceTest {

    /** 테스트용 256비트 키 (32바이트). */
    private val testKeyBase64: String = Base64.getEncoder().encodeToString(ByteArray(32) { it.toByte() })

    @Nested
    inner class `암호화 키가 설정된 경우` {

        private val sut = EncryptionService(testKeyBase64)

        @Test
        fun `isEnabled가 true를 반환한다`() {
            sut.isEnabled() shouldBe true
        }

        @Test
        fun `암호화 후 복호화하면 원본과 동일하다`() {
            val plaintext = "xoxb-slack-bot-token-1234567890"
            val encrypted = sut.encrypt(plaintext)
            val decrypted = sut.decrypt(encrypted)

            decrypted shouldBe plaintext
        }

        @Test
        fun `암호문은 원문과 다르다`() {
            val plaintext = "secret-value"
            val encrypted = sut.encrypt(plaintext)

            encrypted shouldNotBe plaintext
            encrypted shouldNotContain "secret-value"
        }

        @Test
        fun `동일 평문도 매번 다른 암호문을 생성한다`() {
            val plaintext = "same-input"
            val encrypted1 = sut.encrypt(plaintext)
            val encrypted2 = sut.encrypt(plaintext)

            encrypted1 shouldNotBe encrypted2
        }

        @Test
        fun `빈 문자열은 암호화하지 않고 그대로 반환한다`() {
            sut.encrypt("") shouldBe ""
            sut.encrypt("  ") shouldBe "  "
        }

        @Test
        fun `빈 문자열은 복호화하지 않고 그대로 반환한다`() {
            sut.decrypt("") shouldBe ""
        }

        @Test
        fun `잘못된 암호문은 원본을 그대로 반환한다`() {
            val invalidCiphertext = "this-is-not-encrypted"
            sut.decrypt(invalidCiphertext) shouldBe invalidCiphertext
        }

        @Test
        fun `한글 평문도 암호화 및 복호화가 가능하다`() {
            val plaintext = "한글 토큰 값 테스트"
            val encrypted = sut.encrypt(plaintext)
            val decrypted = sut.decrypt(encrypted)

            decrypted shouldBe plaintext
        }
    }

    @Nested
    inner class `암호화 키가 없는 경우` {

        private val sut = EncryptionService("")

        @Test
        fun `isEnabled가 false를 반환한다`() {
            sut.isEnabled() shouldBe false
        }

        @Test
        fun `encrypt는 평문을 그대로 반환한다`() {
            val plaintext = "xoxb-slack-bot-token"
            sut.encrypt(plaintext) shouldBe plaintext
        }

        @Test
        fun `decrypt는 입력을 그대로 반환한다`() {
            val input = "some-value"
            sut.decrypt(input) shouldBe input
        }
    }

    @Nested
    inner class `잘못된 암호화 키` {

        @Test
        fun `16바이트 키는 무시되고 평문 모드로 동작한다`() {
            val shortKey = Base64.getEncoder().encodeToString(ByteArray(16) { it.toByte() })
            val sut = EncryptionService(shortKey)

            sut.isEnabled() shouldBe false
            sut.encrypt("test") shouldBe "test"
        }

        @Test
        fun `유효하지 않은 Base64는 무시되고 평문 모드로 동작한다`() {
            val sut = EncryptionService("not-valid-base64!!!")

            sut.isEnabled() shouldBe false
            sut.encrypt("test") shouldBe "test"
        }
    }

    @Nested
    inner class `fail-fast 플래그` {

        @Test
        fun `fail-fast=true 이고 키 미설정이면 생성 시 IllegalStateException 을 던진다`() {
            val ex = shouldThrow<IllegalStateException> {
                EncryptionService(encryptionKeyBase64 = "", failFast = true)
            }
            ex.message!! shouldContain "ENCRYPTION_KEY"
            ex.message!! shouldContain "fail-fast"
        }

        @Test
        fun `fail-fast=true 이고 키 초기화 실패(잘못된 Base64)면 IllegalStateException 을 던진다`() {
            val ex = shouldThrow<IllegalStateException> {
                EncryptionService(encryptionKeyBase64 = "not-valid-base64!!!", failFast = true)
            }
            ex.message!! shouldContain "초기화 실패"
        }

        @Test
        fun `fail-fast=false 이면 키 미설정이어도 정상 생성되고 평문 모드로 동작한다 (기본 동작 회귀)`() {
            val sut = EncryptionService(encryptionKeyBase64 = "", failFast = false)
            sut.isEnabled() shouldBe false
            sut.encrypt("x") shouldBe "x"
        }
    }
}
