package com.ohmyclipping.service.port

/**
 * 공통 알림 이벤트 계약.
 *
 * 이벤트 enum은 발송 도메인별로 나누되 dedup/retry/severity 정책은 같은 shape으로 유지한다.
 */
sealed interface NotificationEvent {
    val channel: NotificationChannel
    val dedupKeyTemplate: String?
    val dedupWindowMinutes: Long
    val maxRetries: Int
    val severity: NotificationSeverity
    val eventName: String
}

/**
 * 운영 채널 알림 이벤트.
 */
enum class OpsNotificationEvent(
    override val dedupKeyTemplate: String?,
    override val dedupWindowMinutes: Long,
    override val maxRetries: Int,
    override val severity: NotificationSeverity
) : NotificationEvent {
    /**
     * RSS 소스 연속 실패로 자동 비활성화.
     * 운영 조치: 관리자 > 소스 관리 페이지에서 해당 소스 URL 접근성을 확인한다.
     */
    SOURCE_AUTO_DISABLED("source:{sourceId}", 1440, 1, NotificationSeverity.WARN),

    /**
     * 비활성화 소스 재시도 결과.
     */
    SOURCE_RETRY_RESULT("source-retry:{date}", 1440, 1, NotificationSeverity.INFO),

    /**
     * 일일 AI 비용 임계값 초과.
     */
    COST_THRESHOLD_EXCEEDED("cost:{date}", 1440, 1, NotificationSeverity.WARN),

    /**
     * 월 LLM 예산 초과로 요약 자동 중단.
     */
    BUDGET_EXCEEDED("budget:{date}", 1440, 1, NotificationSeverity.CRITICAL),

    /**
     * 월 LLM 예산의 90% 임계값 도달 경고.
     */
    BUDGET_CRITICAL(null, 0, 1, NotificationSeverity.WARN),

    /**
     * 구독자 없는 카테고리 자동 비활성화.
     */
    EMPTY_CATEGORY_CLEANUP("empty-cat:{date}", 1440, 1, NotificationSeverity.INFO),

    /**
     * 비동기 작업 최대 재시도 초과로 최종 실패.
     */
    JOB_PERMANENTLY_FAILED(null, 0, 1, NotificationSeverity.CRITICAL),

    /**
     * 키워드 급변 감지.
     */
    KEYWORD_VOLATILITY("keyword:{date}", 1440, 0, NotificationSeverity.INFO),

    /**
     * Gemini API 서킷 브레이커 OPEN 전환.
     */
    CIRCUIT_BREAKER_OPEN("cb:{circuitBreaker}", 30, 1, NotificationSeverity.CRITICAL),

    /**
     * 가입 승인 SLA 임계 초과.
     */
    USER_APPROVAL_SLA_EXCEEDED(null, 0, 1, NotificationSeverity.WARN),

    /**
     * 사용자 구독 신청 검토 SLA 임계 초과.
     */
    CLIPPING_REQUEST_SLA_EXCEEDED(null, 0, 1, NotificationSeverity.WARN),

    /**
     * RSS 소스 검증 SLA 임계 초과.
     */
    SOURCE_REQUEST_SLA_EXCEEDED(null, 0, 1, NotificationSeverity.WARN);

    override val channel: NotificationChannel = NotificationChannel.OPS
    override val eventName: String get() = name
}

/**
 * 사용자 DM 알림 이벤트.
 */
enum class UserNotificationEvent(
    override val dedupKeyTemplate: String?,
    override val dedupWindowMinutes: Long,
    override val maxRetries: Int,
    override val severity: NotificationSeverity
) : NotificationEvent {
    /** 구독 승인 완료 DM. */
    SUBSCRIPTION_APPROVED(null, 0, 1, NotificationSeverity.INFO),

    /** 구독 반려 DM. */
    SUBSCRIPTION_REJECTED(null, 0, 1, NotificationSeverity.INFO),

    /** 다이제스트 발송 실패 DM. */
    DELIVERY_FAILED("delivery-fail:{userId}:{categoryId}", 1440, 0, NotificationSeverity.WARN);

    override val channel: NotificationChannel = NotificationChannel.USER_DM
    override val eventName: String get() = name
}

/**
 * 운영 요청 채널 알림 이벤트.
 */
enum class OpsRequestNotificationEvent(
    override val dedupKeyTemplate: String?,
    override val dedupWindowMinutes: Long,
    override val maxRetries: Int,
    override val severity: NotificationSeverity
) : NotificationEvent {
    /** 신규 가입 요청 알림. */
    USER_SIGNUP_REQUESTED(null, 0, 1, NotificationSeverity.INFO),

    /** 가입 승인 완료 알림. */
    USER_ACCOUNT_APPROVED(null, 0, 1, NotificationSeverity.INFO),

    /** 가입 반려 알림. */
    USER_ACCOUNT_REJECTED(null, 0, 1, NotificationSeverity.INFO),

    /** 비밀번호 초기화 완료 알림. */
    PASSWORD_RESET_COMPLETED(null, 0, 1, NotificationSeverity.INFO),

    /** 구독 신청 알림. */
    SUBSCRIPTION_SUBMITTED(null, 0, 1, NotificationSeverity.INFO),

    /** 구독 승인 운영 알림. */
    SUBSCRIPTION_APPROVED_OPS(null, 0, 1, NotificationSeverity.INFO),

    /** 구독 반려 운영 알림. */
    SUBSCRIPTION_REJECTED_OPS(null, 0, 1, NotificationSeverity.INFO),

    /** 구독 철회 알림. */
    SUBSCRIPTION_WITHDRAWN(null, 0, 1, NotificationSeverity.INFO);

    override val channel: NotificationChannel = NotificationChannel.OPS_REQUEST
    override val eventName: String get() = name
}

enum class NotificationChannel {
    /** 운영/관리자 Slack 채널 (파이프라인 로그) */
    OPS,
    /** 운영 요청 알림 채널 (가입/구독/초기화) */
    OPS_REQUEST,
    /** 사용자 개인 Slack DM */
    USER_DM
}

enum class NotificationSeverity(val prefix: String) {
    INFO(":information_source: "),
    WARN(":warning: "),
    CRITICAL(":rotating_light: ")
}
