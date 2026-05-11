package com.ohmyclipping.admin.dto

import com.ohmyclipping.error.StaleEditInfo
import com.fasterxml.jackson.annotation.JsonInclude

/**
 * 관리자 API에서 공통으로 사용하는 오류 응답 DTO.
 *
 * [staleEditInfo]는 낙관적 잠금 충돌(ConflictException with staleEditInfo)에서만 채워진다.
 * 일반 오류 응답에는 null이므로 직렬화에서 생략한다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ErrorResponse(
    val code: String,
    val message: String?,
    val traceId: String? = null,
    val error: String = code,
    val staleEditInfo: StaleEditInfo? = null
)
