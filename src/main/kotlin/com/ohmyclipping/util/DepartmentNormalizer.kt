package com.ohmyclipping.util

/**
 * 부서/팀 문자열 정규화 유틸.
 *
 * 규칙:
 * - null 은 null.
 * - 앞뒤 공백을 제거 (`trim`).
 * - 소문자로 통일 (`lowercase`).
 * - 연속 공백(스페이스/탭/줄바꿈 포함)을 단일 스페이스로 축소.
 * - 정규화 결과가 빈 문자열이면 null 을 반환해 DB 에 빈 값이 저장되지 않게 한다.
 *
 * 목적:
 * 같은 조직을 "영업팀", "영업 팀", "Sales" 처럼 다양한 표기로 입력해 분석 축이
 * 조용히 어긋나는 현상을 줄인다. 완전한 중복 제거(예: "영업팀" vs "sales")는
 * 이 함수가 책임지지 않으며, 스펠링/표기 매핑은 별도 관리한다.
 */
object DepartmentNormalizer {

    private val WHITESPACE_REGEX = Regex("\\s+")

    /** 부서/팀 입력 문자열을 표준 형태로 정규화한다. */
    fun normalize(raw: String?): String? {
        if (raw == null) return null
        // trim → lowercase → 연속 공백 축소 순서로 적용한다.
        val collapsed = raw.trim().lowercase().replace(WHITESPACE_REGEX, " ")
        return collapsed.ifEmpty { null }
    }
}
