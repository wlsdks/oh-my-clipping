package com.ohmyclipping.store

/**
 * 자동 리포트 발송 중복 방지를 위한 영속 로그 저장소.
 * 같은 reportType + periodKey + channelId 조합은 한 번만 예약할 수 있다.
 */
interface ReportDeliveryLogStore {

    /**
     * 주간/월간 리포트 발송 슬롯을 예약한다.
     *
     * @return 생성된 로그 ID, 이미 같은 슬롯이 존재하면 null
     */
    fun tryReserve(
        reportType: String,
        periodKey: String,
        channelId: String
    ): String?

    /**
     * 예약된 리포트 발송을 성공 상태로 기록한다.
     *
     * @param snapshotId 생성된 트렌드 스냅샷 ID
     * @param slackMessageTs Slack 메시지 timestamp
     * @param durationMs 리포트 생성+발송에 걸린 시간(ms). null 허용(기존 호출자 호환)
     * @param itemsProcessed 리포트에 포함된 아이템 수. null 허용
     */
    fun markSent(
        id: String,
        snapshotId: String,
        slackMessageTs: String?,
        durationMs: Long? = null,
        itemsProcessed: Int? = null
    )

    /**
     * 예약된 리포트 발송을 실패 상태로 기록한다.
     *
     * @param snapshotId 실패 전에 생성된 스냅샷 ID
     * @param errorMessage 운영 확인용 실패 메시지
     * @param durationMs 실패까지 걸린 시간(ms). null 허용
     */
    fun markFailed(
        id: String,
        snapshotId: String?,
        errorMessage: String,
        durationMs: Long? = null
    )

    /**
     * 리포트 발송 이력을 최신순으로 조회한다.
     *
     * @param reportType 필터할 리포트 타입 (예: "WEEKLY", "MONTHLY"). null이면 전체.
     * @param limit 조회 개수 (최대 200 권장)
     * @return 최신 updated_at 기준 내림차순
     */
    fun listHistory(reportType: String?, limit: Int): List<HistoryEntry>

    /**
     * 지정 reportType과 기간 키에서 FAILED 상태인 슬롯을 조회한다.
     * 재시도 대상 선별에 사용한다.
     *
     * @return (id, channelId, errorMessage) 트리플 목록
     */
    fun findFailedByTypeAndPeriod(
        reportType: String,
        periodKey: String
    ): List<FailedDeliverySlot>

    /** 오래된 자동 리포트 발송 로그를 삭제한다. */
    fun deleteOlderThan(days: Int): Int

    /**
     * 단일 슬롯을 (reportType, periodKey, channelId)로 조회한다.
     * 재발송 허용 판정에 사용한다 — 마지막 발송 시각과 설정 변경 시각을 비교하기 위함.
     */
    fun findByKey(
        reportType: String,
        periodKey: String,
        channelId: String
    ): SlotInfo?

    /**
     * 지정 ID의 슬롯을 삭제한다. 설정 변경 후 재발송을 위해 기존 슬롯을 제거할 때 사용한다.
     */
    fun deleteById(id: String)

    data class FailedDeliverySlot(
        val id: String,
        val channelId: String,
        val errorMessage: String?
    )

    /**
     * 발송 슬롯 단건 정보. status와 updatedAt을 포함해 재발송 판정에 사용한다.
     */
    data class SlotInfo(
        val id: String,
        val status: String,
        val createdAt: java.time.Instant,
        val updatedAt: java.time.Instant
    )

    /**
     * 리포트 발송 이력 항목. 관리자 UI의 이력 드로어에 표시한다.
     */
    data class HistoryEntry(
        val id: String,
        val reportType: String,
        val periodKey: String,
        val channelId: String,
        val status: String,
        val snapshotId: String?,
        val slackMessageTs: String?,
        val errorMessage: String?,
        val durationMs: Long?,
        val itemsProcessed: Int?,
        val createdAt: java.time.Instant,
        val updatedAt: java.time.Instant
    )
}
