import { useQuery } from "@tanstack/react-query";
import { Activity, Database, MessageSquare, Clock, Sparkles, Layers, RefreshCw } from "lucide-react";
import { computeSystemHealth } from "@/utils/systemHealth";
import { Badge } from "@/components/ui/badge";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { cn } from "@/utils/cn";
import { systemStatusKeys } from "@/queries/systemStatusKeys";
import { systemStatusService } from "@/services/systemStatusService";
import type { SystemStatusResponse } from "@/types/systemStatus";
import { PipelineChartsSection } from "./PipelineChartsSection";
import { SchedulerStatusPanel } from "./SchedulerStatusPanel";

/* ── 상수 ── */

const REFETCH_INTERVAL = 60_000;

/* ── 스켈레톤 ── */

function SkeletonCard() {
  return (
    <div className="rounded-2xl bg-card p-6 shadow-sm border">
      <div className="flex items-center gap-2 mb-4">
        <div className="h-5 w-5 animate-pulse rounded bg-muted" />
        <div className="h-5 w-24 animate-pulse rounded bg-muted" />
      </div>
      <div className="space-y-3">
        <div className="h-4 w-full animate-pulse rounded bg-muted" />
        <div className="h-4 w-3/4 animate-pulse rounded bg-muted" />
        <div className="h-4 w-1/2 animate-pulse rounded bg-muted" />
      </div>
    </div>
  );
}

/* ── 메모리 바 ── */

function MemoryBar({ usedMb, maxMb }: { usedMb: number; maxMb: number }) {
  const percent = maxMb > 0 ? (usedMb / maxMb) * 100 : 0;

  const fillColor =
    percent >= 95
      ? "bg-[var(--status-danger-text)]"
      : percent >= 80
        ? "bg-[var(--status-warning-text)]"
        : "bg-[var(--status-success-text)]";

  return (
    <div className="space-y-1">
      <div className="flex items-center justify-between text-xs text-muted-foreground">
        <span>메모리</span>
        <span className="tabular-nums">
          {usedMb.toLocaleString()}MB / {maxMb.toLocaleString()}MB ({percent.toFixed(1)}%)
        </span>
      </div>
      <div className="h-2 w-full rounded-full bg-muted">
        <div
          className={cn("h-full rounded-full transition-all duration-300", fillColor)}
          style={{ width: `${Math.min(percent, 100)}%` }}
        />
      </div>
    </div>
  );
}

/* ── 상태 점 ── */

function StatusDot({ active }: { active: boolean }) {
  return (
    <span
      className={cn(
        "inline-block h-2.5 w-2.5 rounded-full",
        active ? "bg-[var(--status-success-text)]" : "bg-[var(--status-danger-text)]"
      )}
    />
  );
}

/* ── 카드 공통 래퍼 ── */

function SectionCard({
  icon,
  title,
  children,
}: {
  icon: React.ReactNode;
  title: string;
  children: React.ReactNode;
}) {
  return (
    <div className="rounded-2xl bg-card p-6 shadow-sm border h-full">
      <div className="flex items-center gap-2 mb-4">
        {icon}
        <h2 className="text-base font-semibold">{title}</h2>
      </div>
      {children}
    </div>
  );
}

/* ── 서버 상태 카드 ── */

function ServerCard({ data }: { data: SystemStatusResponse["server"] }) {
  return (
    <SectionCard
      icon={<Activity className="h-5 w-5 text-[var(--status-success-text)]" />}
      title="서버 상태"
    >
      <div className="space-y-4">
        {/* Uptime */}
        <div>
          <p className="text-xs text-muted-foreground">Uptime</p>
          <p className="text-xl font-bold tabular-nums">{data.uptime}</p>
        </div>

        {/* Java + Profiles */}
        <div className="flex items-center justify-between">
          <div>
            <p className="text-xs text-muted-foreground">Java</p>
            <p className="text-sm font-medium">{data.javaVersion}</p>
          </div>
          <div className="flex items-center gap-1.5">
            {data.activeProfiles.map((profile) => (
              <Badge key={profile} variant="secondary">
                {profile}
              </Badge>
            ))}
          </div>
        </div>

        {/* 메모리 바 */}
        <MemoryBar usedMb={data.memoryUsedMb} maxMb={data.memoryMaxMb} />
      </div>
    </SectionCard>
  );
}

/* ── 데이터베이스 카드 ── */

function DatabaseCard({ data }: { data: SystemStatusResponse["database"] }) {
  return (
    <SectionCard
      icon={<Database className="h-5 w-5 text-[var(--status-neutral-text)]" />}
      title="데이터베이스"
    >
      <div className="space-y-4">
        {/* 연결 상태 */}
        <div className="flex items-center gap-2">
          <StatusDot active={data.connected} />
          <span className="text-sm font-medium">
            {data.connected ? "연결됨" : "연결 끊김"}
          </span>
        </div>

        {/* 커넥션 풀 */}
        <div>
          <p className="text-xs text-muted-foreground mb-2">커넥션 풀</p>
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
            <div className="rounded-xl bg-muted/50 p-3 text-center">
              <p className="text-lg font-bold tabular-nums">{data.poolActive}</p>
              <p className="text-xs text-muted-foreground">사용 중</p>
            </div>
            <div className="rounded-xl bg-muted/50 p-3 text-center">
              <p className="text-lg font-bold tabular-nums">{data.poolIdle}</p>
              <p className="text-xs text-muted-foreground">대기</p>
            </div>
            <div className="rounded-xl bg-muted/50 p-3 text-center">
              <p className="text-lg font-bold tabular-nums">{data.poolTotal}</p>
              <p className="text-xs text-muted-foreground">전체</p>
            </div>
          </div>
        </div>
      </div>
    </SectionCard>
  );
}

/* ── Slack 카드 ── */

function SlackCard({ data }: { data: SystemStatusResponse["slack"] }) {
  return (
    <SectionCard
      icon={<MessageSquare className="h-5 w-5 text-primary" />}
      title="Slack 연결"
    >
      <div className="space-y-4">
        <div className="flex items-center justify-between">
          <span className="text-sm text-muted-foreground">연결 상태</span>
          <div className="flex items-center gap-2">
            <StatusDot active={data.healthy} />
            <span className="text-sm font-medium">
              {data.healthy ? "정상" : "연결 끊김"}
            </span>
          </div>
        </div>
        <div className="flex items-center justify-between">
          <span className="text-sm text-muted-foreground">봇 토큰</span>
          <span className="text-sm font-medium">
            {data.botTokenConfigured ? "설정됨" : "미설정"}
          </span>
        </div>
        <div className="flex items-center justify-between">
          <span className="text-sm text-muted-foreground">기본 채널</span>
          <span className="text-sm font-medium">
            {data.defaultChannelId ?? "미설정"}
          </span>
        </div>
        {data.lastCheckTime && (
          <div className="flex items-center justify-between">
            <span className="text-sm text-muted-foreground">마지막 검증</span>
            <span className="text-sm text-muted-foreground tabular-nums">
              {new Date(data.lastCheckTime).toLocaleString("ko-KR")}
            </span>
          </div>
        )}
      </div>
    </SectionCard>
  );
}

/* ── AI 카드 ── */

function AiCard({ data }: { data: SystemStatusResponse["ai"] }) {
  const stateLabel: Record<string, string> = {
    CLOSED: "정상",
    OPEN: "차단됨",
    HALF_OPEN: "복구 시도 중",
  };

  return (
    <SectionCard
      icon={<Sparkles className="h-5 w-5 text-[var(--status-warning-text)]" />}
      title="AI (Gemini)"
    >
      <div className="space-y-4">
        <div className="flex items-center justify-between">
          <span className="text-sm text-muted-foreground">자동 차단 상태</span>
          <div className="flex items-center gap-2">
            <StatusDot active={data.canCall} />
            <span className="text-sm font-medium">
              {stateLabel[data.circuitBreakerState] ?? data.circuitBreakerState}
            </span>
          </div>
        </div>
        <div className="flex items-center justify-between">
          <span className="text-sm text-muted-foreground">API 호출</span>
          <span className="text-sm font-medium">
            {data.canCall ? "가능" : "차단됨"}
          </span>
        </div>
        <div className="border-t pt-3 space-y-1.5">
          <div className="flex items-center justify-between text-xs text-muted-foreground">
            <span>연속 차단 횟수</span>
            <span className="font-semibold text-foreground">{data.consecutiveOpenCount}회</span>
          </div>
          <div className="flex items-center justify-between text-xs text-muted-foreground">
            <span>누적 차단 횟수</span>
            <span className="font-semibold text-foreground">{data.totalOpenCount}회</span>
          </div>
          <div className="flex items-center justify-between text-xs text-muted-foreground">
            <span>마지막 차단 시각</span>
            <span className="tabular-nums">
              {data.lastOpenedAt ? new Date(data.lastOpenedAt).toLocaleString("ko-KR") : "없음"}
            </span>
          </div>
        </div>
      </div>
    </SectionCard>
  );
}

/* ── 작업 큐 카드 ── */

function JobQueueCard({ data }: { data: SystemStatusResponse["jobQueue"] }) {
  const percent = data.threshold > 0 ? (data.pendingJobs / data.threshold) * 100 : 0;
  const isOverloaded = data.pendingJobs > data.threshold;

  return (
    <SectionCard
      icon={<Layers className="h-5 w-5 text-[var(--status-neutral-text)]" />}
      title="작업 대기열"
    >
      <div className="space-y-4">
        <div className="flex items-center justify-between">
          <span className="text-sm text-muted-foreground">상태</span>
          <div className="flex items-center gap-2">
            <StatusDot active={!isOverloaded} />
            <span className="text-sm font-medium">
              {isOverloaded ? "처리 지연" : "정상"}
            </span>
          </div>
        </div>
        <div>
          <div className="flex items-center justify-between text-xs text-muted-foreground mb-1">
            <span>대기 작업</span>
            <span className="tabular-nums">
              {data.pendingJobs.toLocaleString()} / {data.threshold.toLocaleString()}
            </span>
          </div>
          <div className="h-2 w-full rounded-full bg-muted">
            <div
              className={cn(
                "h-full rounded-full transition-all duration-300",
                isOverloaded
                  ? "bg-[var(--status-danger-text)]"
                  : percent >= 70
                    ? "bg-[var(--status-warning-text)]"
                    : "bg-[var(--status-success-text)]",
              )}
              style={{ width: `${Math.min(percent, 100)}%` }}
            />
          </div>
        </div>
      </div>
    </SectionCard>
  );
}

/* ── 스케줄러 카드 ── */

function SchedulerCard({ data }: { data: SystemStatusResponse["schedulers"] }) {
  return (
    <SectionCard
      icon={<Clock className="h-5 w-5 text-[var(--status-warning-text)]" />}
      title="스케줄러 현황"
    >
      {data.length === 0 ? (
        <p className="text-sm text-muted-foreground py-4 text-center">
          등록된 스케줄러가 없어요
        </p>
      ) : (
        <div className="rounded-xl border bg-card overflow-hidden">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>이름</TableHead>
                <TableHead>스케줄</TableHead>
                <TableHead>설명</TableHead>
                <TableHead>마지막 실행</TableHead>
                <TableHead>결과</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {data.map((scheduler) => (
                <TableRow key={scheduler.name}>
                  <TableCell className="text-sm font-medium">
                    {scheduler.name}
                  </TableCell>
                  <TableCell className="text-sm text-muted-foreground font-mono">
                    {scheduler.schedule}
                  </TableCell>
                  <TableCell className="text-sm text-muted-foreground">
                    {scheduler.description}
                  </TableCell>
                  <TableCell className="text-sm text-muted-foreground tabular-nums">
                    {scheduler.lastRunAt ? new Date(scheduler.lastRunAt).toLocaleTimeString("ko-KR") : "—"}
                  </TableCell>
                  <TableCell className="text-sm">
                    {scheduler.lastResult === "success" && (
                      <span className="inline-flex items-center rounded-full bg-[var(--status-success-bg)] px-2 py-0.5 text-xs font-medium text-[var(--status-success-text)]">성공</span>
                    )}
                    {scheduler.lastResult === "failure" && (
                      <span className="inline-flex items-center rounded-full bg-[var(--status-danger-bg)] px-2 py-0.5 text-xs font-medium text-[var(--status-danger-text)]">실패</span>
                    )}
                    {scheduler.lastResult == null && (
                      <span className="text-xs text-muted-foreground" title="서버 시작 이후 아직 실행되지 않았어요">미실행</span>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}
    </SectionCard>
  );
}

/* ── 전체 건강도 뱃지 ── */

function HealthBadge({ data }: { data: SystemStatusResponse }) {
  const health = computeSystemHealth(data);
  if (health.ok) {
    return (
      <span className="inline-flex items-center gap-1.5 rounded-full bg-[var(--status-success-bg)] px-3 py-1 text-sm font-semibold text-[var(--status-success-text)]">
        <span className="inline-block h-2 w-2 rounded-full bg-[var(--status-success-text)]" />
        전체 정상
      </span>
    );
  }
  const issues: string[] = [];
  if (!health.dbOk) issues.push("DB 연결 끊김");
  if (!health.slackOk) issues.push("Slack 연결 끊김");
  if (!health.aiOk) issues.push("AI 서비스 일시 차단됨");
  if (!health.jobQueueOk) issues.push("작업 대기열 처리 지연");
  return (
    <span className="inline-flex items-center gap-1.5 rounded-full bg-[var(--status-warning-bg)] px-3 py-1 text-sm font-semibold text-[var(--status-warning-text)]">
      ⚠ 이상 {issues.length}건
    </span>
  );
}

/* ── 이상 경고 배너 ── */

function WarningBanner({ data }: { data: SystemStatusResponse }) {
  const health = computeSystemHealth(data);
  if (health.ok) return null;
  const issues: string[] = [];
  if (!health.dbOk) issues.push("DB 연결 끊김");
  if (!health.slackOk) issues.push("Slack 연결 끊김");
  if (!health.aiOk) issues.push("AI 서비스 일시 차단됨");
  if (!health.jobQueueOk) issues.push("작업 대기열 처리 지연");
  return (
    <div role="alert" className="rounded-2xl bg-[var(--status-warning-bg)] border border-[var(--status-warning-bg)] p-4 flex items-center gap-2">
      <span className="text-sm font-semibold text-[var(--status-warning-text)]">⚠ 이상 {issues.length}건</span>
      <span className="text-sm text-[var(--status-warning-text)]">{issues.join(" · ")}</span>
    </div>
  );
}

/* ── 갱신 인디케이터 ── */

function RefreshIndicator({
  dataUpdatedAt,
  isFetching,
  onRefresh,
}: {
  dataUpdatedAt: number;
  isFetching: boolean;
  onRefresh: () => void;
}) {
  const timeString = dataUpdatedAt
    ? new Date(dataUpdatedAt).toLocaleTimeString("ko-KR")
    : "—";
  return (
    <div className="flex items-center gap-2 text-xs text-muted-foreground">
      <span className="tabular-nums">마지막 갱신 {timeString}</span>
      <span className="text-border">·</span>
      <span>60초마다 자동</span>
      <button
        onClick={onRefresh}
        disabled={isFetching}
        aria-label="새로고침"
        aria-busy={isFetching}
        className="inline-flex items-center gap-1 rounded-lg border px-2.5 py-1 text-xs font-medium text-muted-foreground hover:bg-muted transition-colors disabled:opacity-50"
      >
        <RefreshCw className={cn("h-3 w-3", isFetching && "animate-spin")} />
        새로고침
      </button>
    </div>
  );
}

/* ── 메인 페이지 ── */

export function SystemStatusPage() {
  const { data, isLoading, isError, dataUpdatedAt, isFetching, refetch } = useQuery({
    queryKey: systemStatusKeys.status(),
    queryFn: () => systemStatusService.getStatus(),
    refetchInterval: REFETCH_INTERVAL,
    refetchIntervalInBackground: false
  });

  return (
    <div className="p-4 sm:p-6 space-y-5">
      {/* 헤더 */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
        <div className="flex items-center gap-3">
          <h1 className="text-2xl font-bold">시스템 상태</h1>
          {data && <HealthBadge data={data} />}
        </div>
        <RefreshIndicator
          dataUpdatedAt={dataUpdatedAt}
          isFetching={isFetching}
          onRefresh={() => refetch()}
        />
      </div>

      {/* 이상 경고 배너 */}
      {data && <WarningBanner data={data} />}

      {/* 에러 상태 */}
      {isError && (
        <div className="rounded-2xl bg-[var(--status-danger-bg)] border border-[var(--status-danger-bg)] p-6 text-center">
          <p className="text-sm text-[var(--status-danger-text)]">
            시스템 상태를 불러오는 중 문제가 발생했어요. 잠시 후 자동으로 다시 시도해요
          </p>
        </div>
      )}

      {/* 로딩 스켈레톤 */}
      {isLoading && (
        <>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            <SkeletonCard />
            <SkeletonCard />
            <SkeletonCard />
            <SkeletonCard />
            <SkeletonCard />
          </div>
          <SkeletonCard />
        </>
      )}

      {/* 데이터 렌더링 */}
      {data && (
        <>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            <ServerCard data={data.server} />
            <DatabaseCard data={data.database} />
            <SlackCard data={data.slack} />
            <AiCard data={data.ai} />
            <JobQueueCard data={data.jobQueue} />
          </div>
          <SchedulerCard data={data.schedulers} />
        </>
      )}

      {/* 스케줄러 실시간 상세 패널 — 다음 실행 시각, 마지막 에러 메시지, 소요시간까지 제공 */}
      <SchedulerStatusPanel />

      {/* 파이프라인 추이 차트 */}
      <PipelineChartsSection />
    </div>
  );
}
