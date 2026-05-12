package com.ohmyclipping.service.source

import com.ohmyclipping.error.InvalidInputException
import com.ohmyclipping.model.SourceLegalBasis
import com.ohmyclipping.service.dto.ExistingSourceInfo
import com.ohmyclipping.service.dto.UrlValidationResult
import com.ohmyclipping.service.port.SourceUrlSafetyPort
import com.ohmyclipping.store.RssSourceStore
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import java.io.IOException

private val log = KotlinLogging.logger {}

/**
 * 유저 RSS 제출 시 5단계 사전 검증을 수행한다.
 *
 * 검증 순서: DB 조회(마이크로초) → 외부 HTTP(수초)
 * 1. 도메인 PROHIBITED 체크 (DB)
 * 2. 기존 도메인 정보 조회 (DB)
 * 3. RSS + robots.txt 검증 (HTTP)
 */
@Service
class UserSourceValidationService(
    private val sourceStore: RssSourceStore,
    private val urlSafetyValidator: SourceUrlSafetyPort,
    private val sourceVerificationClient: SourceVerificationClient
) {

    /**
     * URL에 대해 5단계 사전 검증을 수행한다.
     *
     * @param url 사용자가 입력한 RSS URL
     * @return 검증 결과
     */
    fun validate(url: String): UrlValidationResult {
        // URL 형식과 SSRF 위험을 먼저 검증한다.
        val uri = urlSafetyValidator.validatePublicHttpUrl(url)
        val domain = DomainExtractor.extract(uri.toString())

        // 단계 1: 도메인 PROHIBITED 체크 — DB에서 같은 도메인의 소스를 찾아 금지 여부를 확인한다.
        val allSources = sourceStore.list()
        val domainSources = if (domain != null) {
            allSources.filter { DomainExtractor.extract(it.url) == domain }
        } else {
            emptyList()
        }

        val prohibitedSource = domainSources.find { it.legalBasis == SourceLegalBasis.PROHIBITED }
        if (prohibitedSource != null) {
            return UrlValidationResult(
                domainBlocked = true,
                blockReason = "이 도메인은 저작권 사유로 사용이 금지되어 있어요."
            )
        }

        // 단계 2: 기존 도메인 정보 조회 — 이미 등록된 소스가 있으면 안내한다.
        val existingInfo = domainSources.firstOrNull()?.let {
            ExistingSourceInfo(name = it.name, legalBasis = it.legalBasis.name)
        }

        // 단계 3: RSS 파싱 + robots.txt 검증 (HTTP 요청)
        val verificationResult = try {
            sourceVerificationClient.verify(uri)
        } catch (e: IOException) {
            log.warn(e) { "URL 검증 중 외부 요청 실패: $url" }
            // API 오류/타임아웃 시 경고만 표시하고 제출을 허용한다.
            return UrlValidationResult(
                rssValid = false,
                robotsAllowed = true,
                existingSource = existingInfo
            )
        } catch (e: InvalidInputException) {
            log.warn(e) { "URL 검증 중 리다이렉트 URL 검증 실패: $url" }
            return UrlValidationResult(
                rssValid = false,
                robotsAllowed = true,
                existingSource = existingInfo
            )
        } catch (e: IllegalArgumentException) {
            log.warn(e) { "URL 검증 중 리다이렉트 URL 검증 실패: $url" }
            return UrlValidationResult(
                rssValid = false,
                robotsAllowed = true,
                existingSource = existingInfo
            )
        } catch (e: SecurityException) {
            log.warn(e) { "URL 검증 중 외부 요청 실패: $url" }
            // API 오류/타임아웃 시 경고만 표시하고 제출을 허용한다.
            return UrlValidationResult(
                rssValid = false,
                robotsAllowed = true,
                existingSource = existingInfo
            )
        }

        return UrlValidationResult(
            rssValid = verificationResult == VerificationResult.VERIFIED,
            robotsAllowed = verificationResult != VerificationResult.ROBOTS_BLOCKED,
            existingSource = existingInfo
        )
    }
}
