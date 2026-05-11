package com.ohmyclipping.service.dto

/**
 * 스케줄러 실시간 상태 조회 응답.
 *
 * 관리자 대시보드에서 스케줄러 별 헬스를 한눈에 확인할 수 있도록
 * 마지막 실행 결과 + 다음 실행 예정 시각 + 최근 에러 메시지를 하나의 구조로 제공한다.
 *
 * @property name 스케줄러 표시 이름
 * @property trackerKey 실행 이력을 추적할 때 쓰는 키. 매핑이 없으면 null
 * @property schedule 사람이 읽는 스케줄 라벨 ("매일 03:00", "fixedDelay 3초")
 * @property description 사용자 친화적 설명
 * @property lastRunAt 마지막 실행 시각 (ISO-8601 문자열, 미실행 시 null)
 * @property lastDurationMs 마지막 실행 소요 시간(ms)
 * @property lastResult "success" / "failure" / null (미실행)
 * @property lastError 실패한 경우 예외 메시지 일부 (성공/미실행 시 null)
 * @property nextRunAt 다음 실행 예정 시각 (cron 표현식이 있는 스케줄러만 계산)
 * @property status 현재 상태 라벨 ("IDLE" | "FAILED")
 * @property stalenessSeconds 마지막 실행 이후 경과 시간(초). 미실행 시 null
 */
data class SchedulerStatusResponse(
    val name: String,
    val trackerKey: String?,
    val schedule: String,
    val description: String,
    val lastRunAt: String?,
    val lastDurationMs: Long?,
    val lastResult: String?,
    val lastError: String?,
    val nextRunAt: String?,
    val status: String,
    val stalenessSeconds: Long?
)
