package com.clipping.mcpserver.support

import com.clipping.mcpserver.error.DependencyFailureException
import com.clipping.mcpserver.error.ErrorCode

/**
 * Slack 발송 분야의 severity 힌트.
 *
 * 향후 F8 전용 채널 라우팅에서 사용할 수 있도록 상위 관찰성 레이어에 전달한다.
 */
enum class SlackFailureSeverity { INFO, WARN, CRITICAL }

/**
 * Slack 발송 실패에 대해 [SlackErrorCategory] 분류 결과와 severity 힌트를 함께
 * 상위 레이어(오케스트레이터/알림기)에 전달하기 위한 예외.
 *
 * `retryAfterSeconds` 가 설정된 경우 [com.clipping.mcpserver.service.DeliveryRetryOrchestrator]
 * 는 자체 지수 백오프 대신 이 값을 존중해야 한다 (현 구현은 힌트만 전달한다).
 */
class SlackDeliveryFailureException(
    val category: SlackErrorCategory,
    val slackErrorCode: String?,
    val severity: SlackFailureSeverity,
    message: String,
    cause: Throwable? = null,
    retryAfterSeconds: Long? = null
) : DependencyFailureException(
    message = message,
    cause = cause,
    errorCode = ErrorCode.DEPENDENCY_FAILURE,
    retryAfterSeconds = retryAfterSeconds
)
