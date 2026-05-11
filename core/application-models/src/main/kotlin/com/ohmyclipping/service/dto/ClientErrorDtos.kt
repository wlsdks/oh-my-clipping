package com.ohmyclipping.service.dto

/**
 * 프론트엔드 ErrorBoundary에서 포착한 렌더링 예외를 서버로 보고할 때 사용하는 페이로드.
 *
 * 현재는 관찰 용도로만 쓰이며 서버는 감사 성격의 로그만 남긴다.
 * 필드 제약은 남용/로그 폭주 방지를 위해 컨트롤러에서 검증한다.
 */
data class ClientErrorReport(
    /** 에러 메시지. 필수, 최대 2000자. */
    val message: String,
    /** JS Error.stack 문자열. 선택, 최대 10000자. */
    val stack: String? = null,
    /** React errorInfo.componentStack 문자열. 선택, 최대 10000자. */
    val componentStack: String? = null,
    /** 에러가 발생한 페이지 경로(+쿼리). 선택, 최대 500자. */
    val url: String? = null,
    /** 브라우저 User-Agent. 선택, 최대 500자. */
    val userAgent: String? = null,
    /** React Minified 에러 코드 (예: "185"). 선택, 최대 50자. */
    val reactErrorCode: String? = null,
    /** 부가 태그 맵(A/B 플래그, 기능 플래그 등). 최대 20 entry, key/value 각 100자. */
    val tags: Map<String, String>? = null
)
