package com.clipping.mcpserver.support

/**
 * Page/size 기반 OFFSET 계산 유틸.
 *
 * page 와 size 는 컨트롤러/서비스에서 이미 정책 범위로 정규화한 값을 넘긴다.
 * 곱셈은 Int overflow 를 피하기 위해 Long 으로 수행하고, JDBC OFFSET 이 음수로
 * 내려가지 않도록 Int.MAX_VALUE 에서 포화시킨다.
 */
object PaginationUtils {
    fun safeOffset(page: Int, size: Int): Int =
        (page.toLong().coerceAtLeast(0) * size.toLong().coerceAtLeast(0))
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
}
