/** 시스템 상태 API 응답 타입 */

export interface ServerStatus {
  uptime: string;
  javaVersion: string;
  activeProfiles: string[];
  memoryUsedMb: number;
  memoryMaxMb: number;
}

export interface DatabaseStatus {
  connected: boolean;
  poolActive: number;
  poolIdle: number;
  poolTotal: number;
}

export interface SlackStatus {
  botTokenConfigured: boolean;
  defaultChannelId: string | null;
  healthy: boolean;
  lastCheckTime: string | null;
}

export interface AiStatus {
  circuitBreakerState: string;
  canCall: boolean;
  consecutiveOpenCount: number;
  totalOpenCount: number;
  lastOpenedAt: string | null;
}

export interface JobQueueStatus {
  pendingJobs: number;
  threshold: number;
}

export interface SchedulerInfo {
  name: string;
  schedule: string;
  description: string;
  lastRunAt: string | null;
  lastResult: string | null;
}

export interface SystemStatusResponse {
  server: ServerStatus;
  database: DatabaseStatus;
  slack: SlackStatus;
  ai: AiStatus;
  jobQueue: JobQueueStatus;
  schedulers: SchedulerInfo[];
}
