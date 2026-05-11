package com.ohmyclipping.admin

import com.ohmyclipping.error.ConflictException
import com.ohmyclipping.error.SignupException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.server.ServerWebExchange
import java.net.URI

@ControllerAdvice(assignableTypes = [AdminAuthController::class])
class AdminAuthExceptionHandler {
    private val signupErrorCodes = setOf(
        "signup_disabled",
        "invalid_input",
        "invalid_username",
        "invalid_password",
        "username_exists"
    )

    @ExceptionHandler(SignupException::class)
    fun handleSignupException(e: SignupException, exchange: ServerWebExchange): ResponseEntity<Void> {
        val errorCode = e.errorCode.code
        val basePath = signupBasePath(exchange)
        val redirectPath = if (signupErrorCodes.contains(errorCode)) {
            "$basePath?error=$errorCode"
        } else {
            "$basePath?error=invalid_input"
        }
        return redirect(redirectPath)
    }

    @ExceptionHandler(ConflictException::class)
    fun handleConflictException(e: ConflictException, exchange: ServerWebExchange): ResponseEntity<Void> =
        redirect("${signupBasePath(exchange)}?error=${e.errorCode.code}")

    @ExceptionHandler(Exception::class)
    fun handleUnknownError(exchange: ServerWebExchange): ResponseEntity<Void> =
        redirect("${signupBasePath(exchange)}?error=unknown")

    private fun redirect(path: String): ResponseEntity<Void> =
        ResponseEntity.status(HttpStatus.FOUND)
            .location(URI.create(path))
            .build()

    private fun signupBasePath(exchange: ServerWebExchange): String =
        if (exchange.request.path.value().startsWith("/user/")) {
            "/user/signup"
        } else {
            "/admin/signup"
        }
}
