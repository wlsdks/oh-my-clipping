import type { SystemStatusResponse } from "@/types/systemStatus";

export interface SystemHealth {
  ok: boolean;
  dbOk: boolean;
  slackOk: boolean;
  aiOk: boolean;
  jobQueueOk: boolean;
  message: string;
}

/**
 * 시스템 상태 응답에서 한 줄 배너 표시용 health 요약을 만든다.
 */
export function computeSystemHealth(status: SystemStatusResponse | undefined): SystemHealth {
  const dbOk = status?.database?.connected ?? true;
  const slackOk = status?.slack?.botTokenConfigured ?? true;
  const aiOk = status?.ai?.canCall ?? true;
  const jobQueueOk = status?.jobQueue
    ? status.jobQueue.pendingJobs <= status.jobQueue.threshold
    : true;
  const ok = dbOk && slackOk && aiOk && jobQueueOk;

  if (ok) {
    return { ok: true, dbOk, slackOk, aiOk, jobQueueOk, message: "모든 시스템 정상" };
  }

  const issues: string[] = [];
  if (!dbOk) issues.push("DB 연결 끊김");
  if (!slackOk) issues.push("Slack 연결 끊김");
  if (!aiOk) issues.push("AI 요청 일시 차단됨");
  if (!jobQueueOk) issues.push("작업 대기열 처리 지연");
  return {
    ok: false,
    dbOk,
    slackOk,
    aiOk,
    jobQueueOk,
    message: `시스템 이상: ${issues.join(" · ")}`,
  };
}
