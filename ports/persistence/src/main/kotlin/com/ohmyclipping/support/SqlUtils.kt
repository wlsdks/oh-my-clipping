package com.ohmyclipping.support

/**
 * SQL LIKE 절에서 사용자 입력을 안전하게 처리하기 위한 유틸리티.
 *
 * `%`, `_`, `\` 등 LIKE 와일드카드 문자를 이스케이프하여
 * SQL 인젝션 및 의도하지 않은 패턴 매칭을 방지한다.
 */
object SqlUtils {

    /**
     * SQL LIKE 절에 안전하게 바인딩할 수 있도록 와일드카드 문자를 이스케이프한다.
     *
     * 이스케이프 대상: `\` → `\\`, `%` → `\%`, `_` → `\_`
     *
     * @param input 사용자 입력 문자열
     * @return LIKE 와일드카드가 이스케이프된 문자열
     */
    fun escapeLike(input: String): String =
        input
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
}
