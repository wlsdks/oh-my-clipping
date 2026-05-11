package com.ohmyclipping.admin.mcp

import com.ohmyclipping.error.InvalidInputException
import com.ohmyclipping.mcp.McpRateLimiter
import com.ohmyclipping.mcp.mcpToolCall
import com.ohmyclipping.service.port.ClippingPipelinePort
import com.ohmyclipping.service.pipeline.toCollectResult
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Component

/**
 * Admin 도구 — RSS 수집 동기 실행.
 *
 * 운영자가 소스 점검 또는 테스트 목적으로 즉시 수집을 돌릴 때 사용한다.
 * 장시간 실행이 예상되면 [AdminCollectAsyncTool]을 사용한다.
 */
@Component
class AdminCollectTool(
    private val clippingPipelinePort: ClippingPipelinePort,
    private val rateLimiter: McpRateLimiter
) {

    companion object {
        /** 동기 수집이 허용되는 최대 hoursBack 상한. 이보다 크면 async 를 강제한다. */
        internal const val MAX_SYNC_HOURS_BACK = 6
    }

    @Tool(
        description = """
            RSS 피드를 동기로 즉시 수집한다 (요청 스레드 블로킹, ~수십 초 이내 완료).
            **언제 쓰나:** 단일 카테고리 테스트나 소스 상태 점검 목적으로 결과를 지금 바로 확인하고 싶을 때.
            **쓰지 말 것:** 수집이 오래 걸릴 가능성이 있을 때 — admin_collect_async 를 사용.
            **강제 규칙 (거부 조건):**
              1) categoryId 가 비어있으면 InvalidInputException → admin_collect_async 사용
              2) hoursBack > 6 이면 InvalidInputException → admin_collect_async 사용
            **파라미터:**
              - categoryId: 필수 — 단일 카테고리 ID
              - hoursBack: 선택 — 1~6 시간. 생략 시 시스템 런타임 설정값(defaultHoursBack)을 사용.
            **반환:** 수집 건수가 담긴 CollectResult.
        """,
    )
    fun admin_collect(
        @ToolParam(description = "수집 대상 카테고리 ID (필수)", required = false) categoryId: String?,
        @ToolParam(
            description = "몇 시간 이전까지 조회할지 (1~6, 동기 상한). 생략 시 런타임 defaultHoursBack 사용",
            required = false,
        ) hoursBack: Int?,
    ): String = mcpToolCall {
        // 강제 가드레일: 장기/전체 수집은 async 로 위임해 request timeout 을 피한다.
        if (categoryId.isNullOrBlank()) {
            throw InvalidInputException(
                "동기 수집은 categoryId 가 필수다. 전체 카테고리 수집은 admin_collect_async 를 사용하라."
            )
        }
        if (hoursBack != null && hoursBack > MAX_SYNC_HOURS_BACK) {
            throw InvalidInputException(
                "동기 수집은 hoursBack <= $MAX_SYNC_HOURS_BACK 인 짧은 테스트만 지원한다. " +
                    "장기 수집(hoursBack=$hoursBack) 은 admin_collect_async 를 사용하라."
            )
        }
        if (hoursBack != null && hoursBack <= 0) {
            throw InvalidInputException("동기 수집 hoursBack 은 1~$MAX_SYNC_HOURS_BACK 범위여야 한다.")
        }
        // 호출 빈도 제한: 카테고리 단위로 최대 20회/시간. 카테고리 필수화 후 dimension 은 항상 유효.
        rateLimiter.checkOrThrow(
            toolName = "admin_collect",
            maxRequests = 20,
            windowSeconds = 3600,
            dimension = categoryId
        )
        clippingPipelinePort.collect(categoryId, hoursBack).toCollectResult()
    }
}
