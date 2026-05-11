/**
 * 관리자 스케줄러 상태 조회 API 응답 타입.
 * `GET /api/admin/schedulers/status` 응답과 1:1 매핑된다.
 */
export interface SchedulerStatusItem {
  /** 스케줄러 표시 이름 */
  name: string;
  /** 실행 이력 추적 키 (매핑 없으면 null) */
  trackerKey: string | null;
  /** 사람이 읽는 스케줄 라벨 */
  schedule: string;
  /** 사용자 친화적 설명 */
  description: string;
  /** 마지막 실행 시각 (ISO-8601) */
  lastRunAt: string | null;
  /** 마지막 실행 소요 시간(ms) */
  lastDurationMs: number | null;
  /** "success" | "failure" | null */
  lastResult: "success" | "failure" | null;
  /** 마지막 실패 메시지 (성공/미실행 시 null) */
  lastError: string | null;
  /** 다음 실행 예정 시각 (cron 기반만 존재) */
  nextRunAt: string | null;
  /** "IDLE" | "FAILED" */
  status: "IDLE" | "FAILED";
  /** 마지막 실행 이후 경과(초) */
  stalenessSeconds: number | null;
}
