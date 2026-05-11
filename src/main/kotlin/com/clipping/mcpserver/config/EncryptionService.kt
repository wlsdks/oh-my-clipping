package com.clipping.mcpserver.config

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

private val log = KotlinLogging.logger {}

/**
 * AES-256-GCM 기반 대칭키 암호화 서비스.
 *
 * 민감한 값(Slack 토큰 등)을 DB에 저장하기 전에 암호화하고,
 * 읽을 때 복호화한다. 암호화 키는 환경변수 `ENCRYPTION_KEY`로 주입한다.
 *
 * 키가 설정되지 않으면 암호화/복호화를 건너뛰고 평문을 그대로 반환한다(로컬 개발 편의).
 * `clipping.security.fail-fast=true` 에서는 키 미설정/초기화 실패 시 부팅 실패.
 */
@Service
class EncryptionService(
    @Value("\${ENCRYPTION_KEY:}") private val encryptionKeyBase64: String,
    @Value("\${clipping.security.fail-fast:false}") private val failFast: Boolean = false
) {

    companion object {
        private const val ALGORITHM = "AES/GCM/NoPadding"
        private const val KEY_ALGORITHM = "AES"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128
    }

    private val secretKey: SecretKeySpec? = initKey()

    /**
     * Base64 인코딩된 암호화 키를 SecretKeySpec으로 변환한다.
     * 키가 없거나 잘못된 형식이면 null을 반환한다 (fail-fast=false 일 때).
     * fail-fast=true 이면 부팅 시 IllegalStateException 을 던져 서버 시작을 막는다.
     */
    private fun initKey(): SecretKeySpec? {
        if (encryptionKeyBase64.isBlank()) {
            val base = "ENCRYPTION_KEY 미설정 — 암호화를 건너뛰고 평문으로 저장합니다."
            check(!failFast) {
                "$base clipping.security.fail-fast=true 환경에서는 허용하지 않습니다."
            }
            log.warn { "$base 운영 환경에서는 반드시 설정하세요." }
            return null
        }
        return try {
            val keyBytes = Base64.getDecoder().decode(encryptionKeyBase64)
            require(keyBytes.size == 32) { "ENCRYPTION_KEY는 256비트(32바이트) Base64여야 합니다. 현재: ${keyBytes.size}바이트" }
            SecretKeySpec(keyBytes, KEY_ALGORITHM)
        } catch (e: IllegalArgumentException) {
            val base = "ENCRYPTION_KEY 초기화 실패 — 암호화를 건너뜁니다."
            if (failFast) {
                throw IllegalStateException(
                    "$base clipping.security.fail-fast=true 환경에서는 허용하지 않습니다.",
                    e
                )
            }
            log.error(e) { base }
            null
        }
    }

    /**
     * 키 설정 여부를 반환한다.
     */
    fun isEnabled(): Boolean = secretKey != null

    /**
     * 평문을 AES-256-GCM으로 암호화하고 Base64로 인코딩한다.
     *
     * 키가 설정되지 않으면 평문을 그대로 반환한다.
     *
     * @param plaintext 암호화할 평문
     * @return Base64 인코딩된 암호문 (IV + ciphertext)
     */
    fun encrypt(plaintext: String): String {
        val key = secretKey ?: return plaintext
        if (plaintext.isBlank()) return plaintext

        // 매 호출마다 새로운 IV를 생성하여 동일 평문도 다른 암호문을 생성한다.
        val iv = ByteArray(GCM_IV_LENGTH)
        SecureRandom().nextBytes(iv)

        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        // IV와 암호문을 연결하여 단일 Base64 문자열로 반환한다.
        val combined = iv + ciphertext
        return Base64.getEncoder().encodeToString(combined)
    }

    /**
     * Base64 인코딩된 암호문을 복호화하여 평문을 반환한다.
     *
     * 키가 설정되지 않거나 복호화에 실패하면 원본 문자열을 그대로 반환한다.
     * (마이그레이션 기간 중 평문과 암호문이 혼재할 수 있으므로 fail-open)
     *
     * @param ciphertext Base64 인코딩된 암호문
     * @return 복호화된 평문
     */
    fun decrypt(ciphertext: String): String {
        val key = secretKey ?: return ciphertext
        if (ciphertext.isBlank()) return ciphertext

        return try {
            val combined = Base64.getDecoder().decode(ciphertext)
            if (combined.size < GCM_IV_LENGTH) return ciphertext

            val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
            val encrypted = combined.copyOfRange(GCM_IV_LENGTH, combined.size)

            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        } catch (e: IllegalArgumentException) {
            log.warn(e) { "복호화 실패 — 평문으로 간주합니다. 키 로테이션 후 이 메시지가 반복되면 데이터 마이그레이션이 필요합니다." }
            ciphertext
        } catch (e: GeneralSecurityException) {
            // 복호화 실패 시 평문일 가능성이 있으므로 원본을 반환한다.
            // 프로덕션에서 키 로테이션 후 이 경고가 발생하면 데이터 마이그레이션이 필요하다.
            log.warn(e) { "복호화 실패 — 평문으로 간주합니다. 키 로테이션 후 이 메시지가 반복되면 데이터 마이그레이션이 필요합니다." }
            ciphertext
        }
    }
}
