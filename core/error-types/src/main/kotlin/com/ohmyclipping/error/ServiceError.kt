package com.ohmyclipping.error

import org.springframework.http.HttpStatus

/**
 * 도메인 공통 에러 코드 정의.
 */
enum class ErrorCode(
    val code: String,
    val status: HttpStatus,
    val defaultMessage: String
) {
    INVALID_INPUT("INVALID_INPUT", HttpStatus.BAD_REQUEST, "요청이 유효하지 않습니다."),
    NOT_FOUND("NOT_FOUND", HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    FORBIDDEN("FORBIDDEN", HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    CONFLICT("CONFLICT", HttpStatus.CONFLICT, "요청한 데이터가 이미 존재하거나 충돌이 발생했습니다."),
    SIGNUP_DISABLED("signup_disabled", HttpStatus.FORBIDDEN, "현재 회원가입이 비활성화되어 있습니다."),
    SIGNUP_INVALID_USERNAME("invalid_username", HttpStatus.BAD_REQUEST, "사용자명 형식이 올바르지 않습니다."),
    SIGNUP_INVALID_PASSWORD("invalid_password", HttpStatus.BAD_REQUEST, "비밀번호 형식이 올바르지 않습니다."),
    SIGNUP_USERNAME_EXISTS("username_exists", HttpStatus.CONFLICT, "이미 존재하는 사용자명입니다."),
    SIGNUP_INVALID_INPUT("invalid_input", HttpStatus.BAD_REQUEST, "회원가입 입력값이 올바르지 않습니다."),
    INVALID_STATE("INVALID_STATE", HttpStatus.UNPROCESSABLE_ENTITY, "요청한 작업을 현재 상태에서 처리할 수 없습니다."),
    RATE_LIMITED("RATE_LIMITED", HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."),
    DEPENDENCY_FAILURE("DEPENDENCY_FAILURE", HttpStatus.BAD_GATEWAY, "외부 연동 오류로 요청을 처리할 수 없습니다."),
    INTERNAL_ERROR("INTERNAL_ERROR", HttpStatus.INTERNAL_SERVER_ERROR, "시스템 내부 오류가 발생했습니다.")
}

/**
 * 서비스 계층 공통 예외의 루트 타입.
 */
open class ServiceException(
    val errorCode: ErrorCode,
    override val message: String = errorCode.defaultMessage,
    cause: Throwable? = null
) : RuntimeException(message, cause)

/**
 * 입력 값 유효성/요청 형식 오류.
 */
open class InvalidInputException(
    override val message: String = ErrorCode.INVALID_INPUT.defaultMessage,
    errorCode: ErrorCode = ErrorCode.INVALID_INPUT,
    cause: Throwable? = null
) : ServiceException(errorCode, message, cause)

/**
 * 조회 대상 미존재 오류.
 */
class NotFoundException(
    override val message: String = ErrorCode.NOT_FOUND.defaultMessage,
    cause: Throwable? = null,
    errorCode: ErrorCode = ErrorCode.NOT_FOUND
) : ServiceException(errorCode, message, cause)

/**
 * 상태 전이/중복 충돌 오류.
 *
 * 낙관적 잠금 충돌(STALE_EDIT)도 이 예외를 사용한다.
 * [staleEditInfo]가 채워져 있으면 GlobalExceptionHandler가 409 응답 body에 포함해
 * 프론트에서 "최신 편집자" 안내와 변경 필드 diff를 보여줄 수 있게 한다.
 */
class ConflictException(
    override val message: String = ErrorCode.CONFLICT.defaultMessage,
    cause: Throwable? = null,
    errorCode: ErrorCode = ErrorCode.CONFLICT,
    val staleEditInfo: StaleEditInfo? = null
) : ServiceException(errorCode, message, cause)

/**
 * 낙관적 잠금 충돌 시 프론트로 전달하는 편집 상태 스냅샷.
 *
 * 프론트 PR 2에서 이 정보를 바탕으로 "OOO가 먼저 저장했습니다" 모달과 diff 뷰를 렌더한다.
 *
 * @property code 충돌 분류 코드. 현재는 고정값 `STALE_EDIT`.
 * @property latestUpdatedAt 서버가 보고 있는 최신 updated_at.
 *                           프론트는 이 값을 그대로 expectedUpdatedAt으로 재전송하면 저장이 성공한다.
 * @property latestEditorName 최근 수정자 display name. 미상이면 "관리자"로 익명화한다.
 * @property changedFieldNames 이번 patch에서 사용자가 바꾸려고 한 필드 이름 목록.
 *                             프론트 diff 뷰 힌트용이며, 비어 있어도 된다.
 */
data class StaleEditInfo(
    val code: String = "STALE_EDIT",
    val latestUpdatedAt: java.time.Instant,
    val latestEditorName: String,
    val changedFieldNames: List<String> = emptyList()
)

/**
 * 처리 불가한 상태 오류.
 */
class InvalidStateException(
    override val message: String = ErrorCode.INVALID_STATE.defaultMessage,
    cause: Throwable? = null,
    errorCode: ErrorCode = ErrorCode.INVALID_STATE
) : ServiceException(errorCode, message, cause)

/**
 * 레이트 리밋 초과 오류.
 *
 * MCP 도구/HTTP API 호출에서 사용하며, 재시도 대기 시간을 초 단위로 전달한다.
 *
 * @param retryAt 절대 재시도 시각(UTC epoch). LLM 이 자동 재시도 loop 에 빠지지 않도록
 *   MCP 응답 payload 에 ISO-8601 문자열로 노출된다. null 이면 호출자가 retryAfterSeconds
 *   로 계산해야 한다.
 */
class RateLimitExceededException(
    override val message: String = ErrorCode.RATE_LIMITED.defaultMessage,
    val retryAfterSeconds: Long = 60,
    val retryAt: java.time.Instant? = null,
    cause: Throwable? = null,
    errorCode: ErrorCode = ErrorCode.RATE_LIMITED
) : ServiceException(errorCode, message, cause)

/**
 * 외부 의존성 호출 실패.
 *
 * @param retryAfterSeconds rate limit 응답에 한해 Retry-After 헤더 값을 전달한다.
 *                          오케스트레이터가 지수 백오프 대신 이 값을 존중하도록 힌트를 제공한다.
 */
open class DependencyFailureException(
    override val message: String = ErrorCode.DEPENDENCY_FAILURE.defaultMessage,
    cause: Throwable? = null,
    errorCode: ErrorCode = ErrorCode.DEPENDENCY_FAILURE,
    val retryAfterSeconds: Long? = null
) : ServiceException(errorCode, message, cause)

/**
 * 접근 권한 없음 오류 (예: 봇이 채널에 없는 경우).
 */
class AccessForbiddenException(
    override val message: String = ErrorCode.FORBIDDEN.defaultMessage,
    cause: Throwable? = null,
    errorCode: ErrorCode = ErrorCode.FORBIDDEN
) : ServiceException(errorCode, message, cause)

/**
 * 배치/비동기 작업 실행 실패.
 *
 * 작업 경계에서는 retry/FAILED 전환을 유지해야 하므로 원인 예외를 이 타입으로 감싼다.
 * [operationalMessage] 는 기존 last_error/운영 로그 호환성을 위해 원인 메시지를 그대로 보존한다.
 */
class BatchJobExecutionException(
    val jobId: String,
    val jobType: String,
    val operationalMessage: String,
    cause: Throwable
) : ServiceException(
    errorCode = ErrorCode.INTERNAL_ERROR,
    message = "Batch job failed: jobId=$jobId, jobType=$jobType, reason=$operationalMessage",
    cause = cause
)

/**
 * require/requireNotNull 같은 가드 처리용 공통 유틸.
 */
inline fun ensureValid(
    condition: Boolean,
    errorCode: ErrorCode = ErrorCode.INVALID_INPUT,
    message: () -> String
) {
    if (!condition) throw InvalidInputException(message(), errorCode)
}

/**
 * null 검증 가드.
 */
inline fun <T> requireFound(
    value: T?,
    errorCode: ErrorCode = ErrorCode.NOT_FOUND,
    message: () -> String
): T {
    return value ?: throw NotFoundException(message(), errorCode = errorCode)
}
