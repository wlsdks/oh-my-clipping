package com.ohmyclipping.dart

/**
 * DART 기업 코드 정보.
 *
 * @property corpCode DART 고유 기업 코드 (8자리)
 * @property corpName 기업명 (정식명칭)
 * @property stockCode 종목 코드 (6자리, 비상장사는 빈 문자열)
 */
data class DartCompany(
    val corpCode: String,
    val corpName: String,
    val stockCode: String
) {
    /** 상장사 여부 */
    val isListed: Boolean get() = stockCode.isNotBlank()
}
