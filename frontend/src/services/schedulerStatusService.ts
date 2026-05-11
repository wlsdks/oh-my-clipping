import { api } from "@/lib/kyInstance";
import type { SchedulerStatusItem } from "@/types/schedulerStatus";

/**
 * 스케줄러 실시간 상태 API 호출 모듈.
 * 시스템 상태 페이지의 스케줄러 패널에서 1분 단위 폴링으로 사용한다.
 */
export const schedulerStatusService = {
  /** 등록된 모든 스케줄러의 실행 이력/다음 실행 예정/최근 에러 목록을 조회한다. */
  list: (): Promise<SchedulerStatusItem[]> =>
    api.get("admin/schedulers/status").json(),
};
