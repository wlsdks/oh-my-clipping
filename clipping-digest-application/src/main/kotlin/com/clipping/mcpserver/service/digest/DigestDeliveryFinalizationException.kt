package com.clipping.mcpserver.service.digest

import com.clipping.mcpserver.error.ErrorCode
import com.clipping.mcpserver.error.ServiceException

/**
 * Slack 전송은 성공했지만 sent 마킹/통계 기록 같은 후처리가 실패했음을 나타낸다.
 * 워커가 재전송 대신 후처리 재시도로 분기할 수 있도록 부가 정보를 함께 전달한다.
 */
class DigestDeliveryFinalizationException(
    val categoryId: String,
    val channelId: String,
    val slackMessageTs: String?,
    val summaryIds: List<String>,
    val itemCount: Int,
    cause: Throwable
) : ServiceException(
    ErrorCode.DEPENDENCY_FAILURE,
    "Digest finalization failed after Slack send: category=$categoryId channel=$channelId",
    cause
)
