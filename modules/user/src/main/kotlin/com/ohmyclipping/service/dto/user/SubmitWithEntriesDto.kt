package com.ohmyclipping.service.dto.user

import jakarta.validation.Valid
import jakarta.validation.constraints.Size

data class SubmitWithEntriesRequest(
    @field:Size(min = 1, max = 200, message = "카테고리 이름은 1~200자여야 합니다")
    val categoryName: String,
    @field:Valid
    @field:Size(min = 1, max = 50, message = "항목은 1~50개만 입력할 수 있어요")
    val entries: List<EntryDto>,
    @field:Size(max = 1000, message = "설명은 1000자 이내여야 합니다")
    val description: String? = null,
    /** 위자드에서 사용자가 고른 발송 프리셋 — WEEKDAYS / EVERYDAY / CUSTOM. 누락 시 WEEKDAYS 기본값. */
    val deliveryPreset: String? = null,
    /** 위자드 요일 선택 (CUSTOM 또는 명시적 세팅 시). 프리셋이 있으면 프리셋이 우선. */
    val deliveryDays: List<String>? = null,
    /** 위자드 발송 시각 (0~23, KST 기준). 누락 시 9시 기본값. */
    val deliveryHour: Int? = null
)

data class EntryError(
    val index: Int,
    val value: String,
    val reason: String
)

data class SubmitWithEntriesResponse(
    val requestId: String,
    val status: String,   // "submitted" | "partial" | "rejected"
    val errors: List<EntryError> = emptyList()
)

/** 알려진 reason 상수 — 클라이언트 localizeReason 매핑과 동기. */
object EntryErrorReason {
    const val COMPETITOR_WATCHLIST_CONFLICT = "COMPETITOR_WATCHLIST_CONFLICT"
    const val INVALID_STOCK_CODE = "INVALID_STOCK_CODE"
    const val DUPLICATE_IN_REQUEST = "DUPLICATE_IN_REQUEST"
    const val RATE_LIMITED = "RATE_LIMITED"
    const val VALIDATION_FAILED = "VALIDATION_FAILED"
}
