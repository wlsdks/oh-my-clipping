package com.ohmyclipping.mcp

import com.ohmyclipping.config.RedisRateLimitService
import com.ohmyclipping.error.RateLimitExceededException
import com.ohmyclipping.observability.ClippingMetrics
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * MCP 도구 호출에 대한 공용 레이트 리밋 체커.
 *
 * actor 는 [McpCallerContext] 가 바인딩한 `tokenKid` 를 자동으로 읽어
 * 호출자(= 서비스 토큰) 단위로 쿼터를 분리한다. 토큰 지문이 없으면
 * `anonymous` 로 폴백하여 전역 공유 쿼터를 사용한다. 외부에서 `actor`
 * 를 명시하면 테스트/디버깅 목적으로 그 값을 그대로 사용한다.
 *
 * 내부적으로 [RedisRateLimitService.isRateLimitedForTool]에 위임하여
 * 슬라이딩 윈도우 카운터를 공유한다.
 */
@Component
class McpRateLimiter(
    private val redisRateLimitService: RedisRateLimitService,
    private val metrics: ClippingMetrics,
    private val clock: Clock = Clock.systemUTC(),
) {

    companion object {
        private val KST_ZONE: ZoneId = ZoneId.of("Asia/Seoul")
        private val KST_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("HH:mm:ss 'KST'").withZone(KST_ZONE)
    }

    /**
     * 레이트 리밋을 확인하고 초과 시 [RateLimitExceededException]을 투척한다.
     *
     * 초과 응답에는 한글 안내 메시지 + 절대 재시도 시각(KST + UTC epoch) 이 함께 실려
     * LLM 이 `Retry-After` 를 해석하기 위한 추가 계산 없이 바로 대기 시점을 알 수 있게 한다.
     *
     * @param toolName MCP 도구명 (예: `admin_send_digest`)
     * @param maxRequests 윈도우 내 허용 요청 수
     * @param windowSeconds 윈도우 길이(초)
     * @param dimension 툴 세분화 차원 (예: categoryId). null이면 전역 카운트
     * @param actor 호출 주체 식별자. null 이면 [McpCallerContext] 의 tokenKid
     *   → `anonymous` 순서로 자동 해석한다.
     */
    fun checkOrThrow(
        toolName: String,
        maxRequests: Int,
        windowSeconds: Long,
        dimension: String? = null,
        actor: String? = null
    ) {
        // 명시 actor 가 없으면 호출자 컨텍스트에서 토큰 지문을 끌어온다.
        val resolvedActor = actor ?: McpCallerContext.tokenKid() ?: "anonymous"
        // 슬라이딩 윈도우 한도 초과 시 클라이언트에게 Retry-After 힌트와 함께 예외를 던진다.
        val limited = redisRateLimitService.isRateLimitedForTool(
            toolName = toolName,
            actor = resolvedActor,
            dimension = dimension,
            maxRequests = maxRequests,
            windowSeconds = windowSeconds
        )
        if (limited) {
            // 레이트 리밋 거부 메트릭을 기록한다
            metrics.recordMcpRateLimitRejection(toolName, resolvedActor)
            // 재시도 절대 시각: 슬라이딩 윈도우 특성상 "최악의 대기" = 지금 + windowSeconds.
            // 이 값을 payload 에 노출해 LLM 이 즉시 재호출 loop 에 빠지지 않게 한다.
            val now = clock.instant()
            val retryAt = now.plusSeconds(windowSeconds)
            val retryAtKst = KST_FORMATTER.format(retryAt)
            throw RateLimitExceededException(
                message = "'$toolName' 호출 한도 초과 — ${windowSeconds}초당 최대 ${maxRequests}회. $retryAtKst 이후 다시 시도하세요.",
                retryAfterSeconds = windowSeconds,
                retryAt = retryAt,
            )
        }
    }
}
