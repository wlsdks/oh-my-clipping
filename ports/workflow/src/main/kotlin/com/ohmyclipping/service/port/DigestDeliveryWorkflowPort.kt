package com.ohmyclipping.service.port

/**
 * Prepared digest 생성/발송/후처리 복구를 제공하는 앱 경계 포트.
 *
 * SlackDigestWorker 는 스케줄, 대상 예약, retry orchestration 만 담당하고
 * 실제 digest 생성/전송/finalization 세부 구현은 이 포트 뒤 adapter 에 둔다.
 *
 * 반환 타입은 엔진 파이프라인 DTO 와 공유하는 [PipelineDigestResult] 다.
 * prepared digest 와 pipeline digest 는 동일한 실행 결과 스냅샷이므로
 * 별도의 워크플로 전용 DTO 를 두지 않는다.
 */
interface DigestDeliveryWorkflowPort {
    fun prepareDigest(
        categoryId: String,
        maxItems: Int?,
        unsentOnly: Boolean?,
        sendToSlack: Boolean?,
        slackChannelId: String?,
    ): PipelineDigestResult

    fun sendPreparedDigest(
        categoryId: String,
        preparedDigest: PipelineDigestResult,
        slackChannelId: String,
        categoryNameOverride: String? = null,
    ): PipelineDigestResult

    fun finalizePreparedDigest(categoryId: String, preparedDigest: PipelineDigestResult): Int
}
