package com.ohmyclipping.service.dto.admin

import com.ohmyclipping.model.SourceLegalBasis

/**
 * 소스 법적 정책 정규화 결과 모델.
 */
data class AdminSourcePolicy(
    val legalBasis: SourceLegalBasis,
    val summaryAllowed: Boolean,
    val fulltextAllowed: Boolean
)
