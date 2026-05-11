package com.ohmyclipping.admin

import com.ohmyclipping.admin.dto.ErrorResponse
import com.ohmyclipping.error.ConflictException
import com.ohmyclipping.error.ServiceException
import com.ohmyclipping.error.StaleEditInfo
import com.ohmyclipping.observability.ErrorSlackNotifier
import com.ohmyclipping.service.digest.EngineInvalidInputException
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.validation.BindException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.bind.support.WebExchangeBindException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.ServerWebInputException
import java.time.format.DateTimeParseException

private val logger = KotlinLogging.logger {}

@RestControllerAdvice(annotations = [RestController::class])
class GlobalExceptionHandler(
    private val errorSlackNotifier: ErrorSlackNotifier
) {

    @ExceptionHandler(ServiceException::class)
    fun handleServiceException(e: ServiceException, exchange: ServerWebExchange): ResponseEntity<ErrorResponse> {
        // ConflictException에 staleEditInfo가 있으면 응답 body에 포함해 프론트가 최신 상태를 바로 읽을 수 있게 한다.
        val staleEditInfo = (e as? ConflictException)?.staleEditInfo
        return ResponseEntity.status(e.errorCode.status).body(
            errorResponse(
                code = e.errorCode.code,
                message = e.message,
                exchange = exchange,
                staleEditInfo = staleEditInfo
            )
        )
    }

    @ExceptionHandler(EngineInvalidInputException::class)
    fun handleEngineInvalidInput(e: EngineInvalidInputException, exchange: ServerWebExchange): ResponseEntity<ErrorResponse> =
        ResponseEntity.badRequest().body(
            errorResponse(
                code = "INVALID_INPUT",
                message = e.message,
                exchange = exchange
            )
        )

    /**
     * 채널 유니크 제약 위반(uq_category_channel, uq_request_pending_channel) 처리.
     * 기타 DB 무결성 오류는 500 핸들러로 위임한다.
     */
    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDataIntegrityViolation(
        e: DataIntegrityViolationException,
        exchange: ServerWebExchange
    ): ResponseEntity<ErrorResponse> {
        val causeMessage = e.rootCause?.message ?: e.message ?: ""
        // 채널 유니크 인덱스 위반인지 확인
        if (causeMessage.contains("uq_category_channel") || causeMessage.contains("uq_request_pending_channel")) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(
                errorResponse(
                    code = "CHANNEL_ALREADY_SUBSCRIBED",
                    message = "이 채널에 이미 구독이 있습니다.",
                    exchange = exchange
                )
            )
        }
        // DB 무결성 위반도 심각한 에러이므로 Slack 알림
        errorSlackNotifier.notifyError(exchange, e)
        return handleGeneric(e, exchange)
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleBadRequest(e: IllegalArgumentException, exchange: ServerWebExchange): ResponseEntity<ErrorResponse> =
        ResponseEntity.badRequest().body(
            errorResponse(
                code = "INVALID_INPUT",
                message = e.message,
                exchange = exchange
            )
        )

    @ExceptionHandler(NoSuchElementException::class)
    fun handleNotFound(e: NoSuchElementException, exchange: ServerWebExchange): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            errorResponse(
                code = "NOT_FOUND",
                message = e.message,
                exchange = exchange
            )
        )

    @ExceptionHandler(WebExchangeBindException::class)
    fun handleValidationException(e: WebExchangeBindException, exchange: ServerWebExchange): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(
                errorResponse(
                    code = "VALIDATION_ERROR",
                    message = e.bindingResult.allErrors.joinToString("; ") { it.defaultMessage ?: "입력값이 올바르지 않습니다." },
                    exchange = exchange
                )
            )

    @ExceptionHandler(MethodArgumentNotValidException::class, BindException::class)
    fun handleBeanValidationException(e: Exception, exchange: ServerWebExchange): ResponseEntity<ErrorResponse> {
        val message = when (e) {
            is MethodArgumentNotValidException -> e.bindingResult.allErrors.joinToString("; ") { it.defaultMessage ?: "입력값이 올바르지 않습니다." }
            is BindException -> e.bindingResult.allErrors.joinToString("; ") { it.defaultMessage ?: "입력값이 올바르지 않습니다." }
            else -> "입력값이 올바르지 않습니다."
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(
                errorResponse(
                    code = "VALIDATION_ERROR",
                    message = message,
                    exchange = exchange
                )
            )
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatchException(
        e: MethodArgumentTypeMismatchException,
        exchange: ServerWebExchange
    ): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            errorResponse(
                code = "TYPE_MISMATCH",
                message = "파라미터 '${e.name}'의 값이 올바르지 않습니다: ${e.value}",
                exchange = exchange
            )
        )

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleNotReadable(e: HttpMessageNotReadableException, exchange: ServerWebExchange): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            errorResponse(
                code = "INVALID_PAYLOAD",
                message = "요청 본문을 읽을 수 없습니다.",
                exchange = exchange
            )
        )

    @ExceptionHandler(DateTimeParseException::class)
    fun handleDateTimeParseException(e: DateTimeParseException, exchange: ServerWebExchange): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            errorResponse(
                code = "INVALID_DATE",
                message = "날짜 형식이 올바르지 않습니다.",
                exchange = exchange
            )
        )

    @ExceptionHandler(Exception::class)
    fun handleGeneric(e: Exception, exchange: ServerWebExchange): ResponseEntity<ErrorResponse> {
        logger.error(e) { "Unhandled exception on ${exchange.request.method} ${exchange.request.path}" }
        // 테스트 기간 에러 추적용 Slack 알림
        errorSlackNotifier.notifyError(exchange, e)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(
                errorResponse(
                    code = "INTERNAL_ERROR",
                    message = "예기치 않은 오류가 발생했습니다.",
                    exchange = exchange
                )
            )
    }

    @ExceptionHandler(ResponseStatusException::class)
    fun handleResponseStatusException(e: ResponseStatusException, exchange: ServerWebExchange): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(e.statusCode)
            .body(
                errorResponse(
                    code = "HTTP_ERROR",
                    message = e.reason ?: "요청을 처리할 수 없습니다.",
                    exchange = exchange
                )
            )

    @ExceptionHandler(ServerWebInputException::class)
    fun handleWebInputException(e: ServerWebInputException, exchange: ServerWebExchange): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(
                errorResponse(
                    code = "INVALID_INPUT",
                    message = e.reason ?: "요청 입력이 올바르지 않습니다.",
                    exchange = exchange
                )
            )

    private fun errorResponse(
        code: String,
        message: String?,
        exchange: ServerWebExchange,
        staleEditInfo: StaleEditInfo? = null
    ): ErrorResponse {
        val traceId = exchange.request.id
        return ErrorResponse(
            code = code,
            message = message,
            traceId = traceId,
            staleEditInfo = staleEditInfo
        )
    }
}
