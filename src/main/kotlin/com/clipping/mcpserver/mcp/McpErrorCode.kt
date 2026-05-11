package com.clipping.mcpserver.mcp

import com.clipping.mcpserver.error.AccessForbiddenException
import com.clipping.mcpserver.error.ConflictException
import com.clipping.mcpserver.error.DependencyFailureException
import com.clipping.mcpserver.error.InvalidInputException
import com.clipping.mcpserver.error.InvalidStateException
import com.clipping.mcpserver.error.NotFoundException
import com.clipping.mcpserver.error.RateLimitExceededException

/**
 * MCP JSON-RPC 에러 코드 매핑.
 *
 * 표준 JSON-RPC 2.0 코드(-327xx)와 MCP SDK 1.1.1 표준 코드(-320xx)를 포함하고,
 * 서비스 고유 커스텀 코드는 -32020~-32029 범위를 사용한다.
 */
enum class McpErrorCode(val code: Int) {
    PARSE_ERROR(-32700),
    INVALID_REQUEST(-32600),
    METHOD_NOT_FOUND(-32601),
    INVALID_PARAMS(-32602),
    INTERNAL_ERROR(-32603),

    /** MCP SDK 표준 — 리소스 미존재 */
    RESOURCE_NOT_FOUND(-32002),

    /** 커스텀 — 접근 권한 없음 */
    FORBIDDEN(-32021),

    /** 커스텀 — 요청 속도 제한 초과 */
    RATE_LIMITED(-32022),

    /** 커스텀 — 외부 의존성 호출 실패 */
    DEPENDENCY_FAILURE(-32023),

    /** 커스텀 — 입력 값 유효성 오류 */
    VALIDATION_ERROR(-32024),

    /** 커스텀 — 상태 충돌/중복 */
    CONFLICT(-32025);

    companion object {
        /**
         * 서비스 예외를 대응하는 MCP 에러 코드로 변환한다.
         * 알려지지 않은 예외는 [INTERNAL_ERROR]로 매핑된다.
         */
        fun from(e: Exception): McpErrorCode = when (e) {
            is NotFoundException -> RESOURCE_NOT_FOUND
            is AccessForbiddenException -> FORBIDDEN
            is InvalidInputException -> VALIDATION_ERROR
            is InvalidStateException -> VALIDATION_ERROR
            is ConflictException -> CONFLICT
            is RateLimitExceededException -> RATE_LIMITED
            is DependencyFailureException -> DEPENDENCY_FAILURE
            is IllegalArgumentException -> INVALID_PARAMS
            else -> INTERNAL_ERROR
        }
    }
}
