package com.ohmyclipping.model

/**
 * 저작권 검토 상태 필터.
 *
 * `expected_review_at` 컬럼과 현재 시각 비교로 판별한다:
 * - [EXPIRED]         : `expected_review_at` 이 이미 지난 소스 — 즉시 재검토 필요
 * - [EXPIRING_SOON]   : `expected_review_at` 까지 30일 이내 — 곧 재검토 예정
 * - [NEVER_REVIEWED]  : `terms_reviewed_at` 이 NULL 이고 `crawl_approved = true` — 한 번도 검토 기록 없음
 * - [VALID]           : 위 모든 조건에 해당하지 않음 — 정상
 *
 * 필터 값은 HTTP 쿼리 파라미터로 받아 대/소문자 무시로 파싱한다.
 */
enum class SourceComplianceStatus {
    EXPIRED,
    EXPIRING_SOON,
    NEVER_REVIEWED,
    VALID;

    companion object {
        /** 만료 임박으로 판단할 임계값(30일) */
        const val EXPIRING_SOON_DAYS: Long = 30L

        /** 대/소문자 무시로 값을 파싱한다. 매칭 실패 시 null 을 반환해 서비스 레이어가 거부할 수 있게 한다. */
        fun fromRaw(raw: String?): SourceComplianceStatus? {
            if (raw.isNullOrBlank()) return null
            return entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) }
        }
    }
}
