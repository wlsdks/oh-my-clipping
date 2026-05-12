import { useEffect, useRef, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
} from "recharts";
import { Database, RefreshCw, AlertTriangle, XCircle } from "lucide-react";
import { Link } from "react-router-dom";
import { cn } from "@/utils/cn";
import { relativeTime } from "@/utils/date";
import { chartColor, TossTooltip, GRID_PROPS, AXIS_PROPS } from "@/utils/chartTheme";
import { dbMetricsKeys } from "@/queries/dbMetricsKeys";
import { dbMetricsService } from "@/services/dbMetricsService";
import type { DbMetricsSnapshot, ThresholdLevel } from "@/types/dbMetrics";
import { TopTablesChart } from "./TopTablesChart";
import { RetentionPreviewCard } from "./RetentionPreviewCard";

/* ── 상수 ── */

const REFRESH_COOLDOWN_MS = 30_000;

/* ── 임계값 → 시맨틱 색상 매핑 ── */

const THRESHOLD_COLORS: Record<ThresholdLevel, {
  bar: string;
  bg: string;
  text: string;
  bannerBg: string;
  bannerText: string;
}> = {
  ok: {
    bar: "bg-[var(--status-success-text)]",
    bg: "bg-[var(--status-success-bg)]",
    text: "text-[var(--status-success-text)]",
    bannerBg: "",
    bannerText: "",
  },
  warning: {
    bar: "bg-[var(--status-warning-text)]",
    bg: "bg-[var(--status-warning-bg)]",
    text: "text-[var(--status-warning-text)]",
    bannerBg: "bg-[var(--status-warning-bg)] border-[var(--status-warning-bg)]",
    bannerText: "text-[var(--status-warning-text)]",
  },
  critical: {
    bar: "bg-[var(--status-danger-text)]",
    bg: "bg-[var(--status-danger-bg)]",
    text: "text-[var(--status-danger-text)]",
    bannerBg: "bg-[var(--status-danger-bg)] border-[var(--status-danger-bg)]",
    bannerText: "text-[var(--status-danger-text)]",
  },
};

/* ── 스켈레톤 ── */

function SkeletonCard() {
  return (
    <div className="rounded-2xl bg-card border p-6 shadow-sm">
      <div className="h-5 w-32 animate-pulse rounded bg-muted mb-4" />
      <div className="space-y-3">
        <div className="h-4 w-full animate-pulse rounded bg-muted" />
        <div className="h-4 w-3/4 animate-pulse rounded bg-muted" />
        <div className="h-3 w-full animate-pulse rounded bg-muted mt-4" />
      </div>
    </div>
  );
}

/* ── 임계값 배너 ── */

function ThresholdBanner({ level }: { level: ThresholdLevel }) {
  if (level === "ok") return null;
  const colors = THRESHOLD_COLORS[level];
  const Icon = level === "critical" ? XCircle : AlertTriangle;
  const message =
    level === "critical"
      ? "DB 용량이 95% 를 초과했어요. 즉시 정리하거나 추가 용량을 확보하세요."
      : "DB 용량이 80% 를 넘었어요. 보관 기간을 조정하거나 추가 용량을 확보하세요.";

  return (
    <div
      role="alert"
      className={cn(
        "rounded-2xl border p-4 flex items-center gap-3",
        colors.bannerBg
      )}
    >
      <Icon className={cn("h-5 w-5 shrink-0", colors.bannerText)} />
      <span className={cn("text-sm font-medium flex-1", colors.bannerText)}>
        {message}
      </span>
      <Link
        to="/admin/runtime"
        className={cn(
          "text-sm font-semibold underline underline-offset-2 shrink-0",
          colors.bannerText
        )}
      >
        시스템 설정 →
      </Link>
    </div>
  );
}

/* ── KPI 카드 ── */

function KpiCard({ data }: { data: DbMetricsSnapshot }) {
  const { databaseSizeMegabytes, limitBytes, databaseSizePercentOfLimit, thresholdLevel } = data;
  const colors = THRESHOLD_COLORS[thresholdLevel];
  const limitMb = Math.round(limitBytes / (1024 * 1024));
  const percent = Math.min(databaseSizePercentOfLimit, 100);

  const thresholdLabel: Record<ThresholdLevel, string> = {
    ok: "정상",
    warning: "주의",
    critical: "위험",
  };

  return (
    <div className="rounded-2xl bg-card border shadow-sm p-6">
      <div className="flex items-center gap-2 mb-5">
        <Database className="h-5 w-5 text-[var(--status-neutral-text)]" />
        <h2 className="text-base font-semibold">현재 DB 크기</h2>
        <span
          className={cn(
            "ml-auto inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold",
            colors.bg,
            colors.text
          )}
        >
          {thresholdLabel[thresholdLevel]}
        </span>
      </div>

      {/* 큰 수치 표시 */}
      <div className="flex items-baseline gap-2 mb-1">
        <span className="text-4xl font-bold tabular-nums">{databaseSizeMegabytes.toLocaleString()}</span>
        <span className="text-lg font-medium text-muted-foreground">MB</span>
        <span className="text-muted-foreground text-sm">/ {limitMb.toLocaleString()} MB</span>
      </div>
      <p className={cn("text-2xl font-bold tabular-nums mb-4", colors.text)}>
        {databaseSizePercentOfLimit.toFixed(1)}%
      </p>

      {/* 진행 바 */}
      <div className="h-3 w-full rounded-full bg-muted">
        <div
          className={cn("h-full rounded-full transition-all duration-500", colors.bar)}
          style={{ width: `${percent}%` }} // 퍼센트는 0-100 동적 값이라 Tailwind 유틸리티로 표현 불가 — inline style 필요
        />
      </div>
      <div className="flex items-center justify-between mt-1.5 text-xs text-muted-foreground">
        <span>0 MB</span>
        <span className="tabular-nums">{limitMb.toLocaleString()} MB (한도)</span>
      </div>
    </div>
  );
}

/* ── 7일 성장 스파크라인 ── */

function GrowthSparkline({ bytes }: { bytes: number[] }) {
  const data = bytes.map((b, i) => ({
    day: `D-${bytes.length - 1 - i}`,
    bytes: b,
  }));

  function formatKb(bytes: number): string {
    if (bytes >= 1_048_576) return `${(bytes / 1_048_576).toFixed(1)}MB`;
    return `${(bytes / 1024).toFixed(0)}KB`;
  }

  return (
    <ResponsiveContainer width="100%" height={120}>
      <BarChart data={data} margin={{ top: 8, right: 8, left: 8, bottom: 4 }}>
        <CartesianGrid strokeDasharray={GRID_PROPS.strokeDasharray} stroke={GRID_PROPS.stroke} vertical={false} />
        <XAxis dataKey="day" {...AXIS_PROPS} tick={{ ...AXIS_PROPS.tick, fontSize: 10 }} />
        <YAxis {...AXIS_PROPS} tick={{ ...AXIS_PROPS.tick, fontSize: 10 }} tickFormatter={formatKb} width={50} />
        <Tooltip
          content={
            <TossTooltip formatter={(val) => formatKb(val)} />
          }
        />
        <Bar dataKey="bytes" fill={chartColor(0)} radius={[3, 3, 0, 0]} isAnimationActive={false} />
      </BarChart>
    </ResponsiveContainer>
  );
}

/* ── 갱신 표시 + 버튼 ── */

interface RefreshControlProps {
  lastRefreshedAt: string;
  isFetching: boolean;
  cooldownActive: boolean;
  onRefresh: () => void;
}

function RefreshControl({ lastRefreshedAt, isFetching, cooldownActive, onRefresh }: RefreshControlProps) {
  return (
    <div className="flex items-center gap-2 text-xs text-muted-foreground">
      <span className="tabular-nums">{relativeTime(lastRefreshedAt)} 갱신</span>
      <span className="text-border">·</span>
      <button
        onClick={onRefresh}
        disabled={isFetching || cooldownActive}
        aria-label="수동 새로고침"
        aria-busy={isFetching}
        className="inline-flex items-center gap-1 rounded-lg border px-2.5 py-1 text-xs font-medium text-muted-foreground hover:bg-muted transition-colors disabled:opacity-50"
      >
        <RefreshCw className={cn("h-3 w-3", isFetching && "animate-spin")} />
        {cooldownActive ? "30초 대기 중" : "새로고침"}
      </button>
    </div>
  );
}

/* ── 메인 페이지 ── */

export function DbHealthPage() {
  const queryClient = useQueryClient();
  const [cooldownActive, setCooldownActive] = useState(false);
  const cooldownTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const { data, isLoading, isError, isFetching, refetch } = useQuery({
    queryKey: dbMetricsKeys.snapshot(),
    queryFn: () => dbMetricsService.getSnapshot(false),
    staleTime: 60_000,
  });

  function handleManualRefresh() {
    if (cooldownActive) return;
    // 강제 새로고침 — forceRefresh=true 로 서버 캐시 우회
    queryClient.invalidateQueries({ queryKey: dbMetricsKeys.snapshot() });
    void dbMetricsService.getSnapshot(true).then((fresh) => {
      queryClient.setQueryData(dbMetricsKeys.snapshot(), fresh);
    });
    setCooldownActive(true);
    cooldownTimerRef.current = setTimeout(() => {
      setCooldownActive(false);
      cooldownTimerRef.current = null;
    }, REFRESH_COOLDOWN_MS);
  }

  useEffect(() => {
    return () => {
      if (cooldownTimerRef.current) {
        clearTimeout(cooldownTimerRef.current);
      }
    };
  }, []);

  return (
    <div className="p-4 sm:p-6 space-y-5">
      {/* 헤더 */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
        <h1 className="text-2xl font-bold">DB 상태</h1>
        {data && (
          <RefreshControl
            lastRefreshedAt={data.lastRefreshedAt}
            isFetching={isFetching}
            cooldownActive={cooldownActive}
            onRefresh={handleManualRefresh}
          />
        )}
      </div>

      {/* 임계값 배너 */}
      {data && <ThresholdBanner level={data.thresholdLevel} />}

      {/* 에러 상태 */}
      {isError && (
        <div className="rounded-2xl bg-[var(--status-danger-bg)] border border-[var(--status-danger-bg)] p-6 text-center space-y-3">
          <p className="text-sm text-[var(--status-danger-text)]">
            DB 상태를 불러오는 중 문제가 발생했어요.
          </p>
          <button
            onClick={() => void refetch()}
            className="inline-flex items-center gap-1.5 rounded-lg bg-[var(--status-danger-text)] text-white px-3 py-1.5 text-xs font-medium hover:opacity-90 transition-opacity"
          >
            <RefreshCw className="h-3.5 w-3.5" />
            다시 시도
          </button>
        </div>
      )}

      {/* 로딩 스켈레톤 */}
      {isLoading && (
        <div className="space-y-4">
          <SkeletonCard />
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <SkeletonCard />
            <SkeletonCard />
          </div>
        </div>
      )}

      {/* 데이터 렌더링 */}
      {data && (
        <>
          {/* KPI 카드 */}
          <KpiCard data={data} />

          {/* Top 10 테이블 차트 */}
          <div className="rounded-2xl bg-card border shadow-sm p-6">
            <h2 className="text-base font-semibold mb-4">테이블별 크기 (Top {data.topTables.length})</h2>
            <TopTablesChart tables={data.topTables} />
          </div>

          {/* 하단 두 칸 */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {/* 7일 성장 스파크라인 */}
            <div className="rounded-2xl bg-card border shadow-sm p-6">
              <h2 className="text-base font-semibold mb-1">7일 일별 증가량</h2>
              <p className="text-xs text-muted-foreground mb-3">
                평균{" "}
                <span className="font-semibold text-foreground">
                  {data.dailyGrowth.avgDailyBytes >= 1_048_576
                    ? `${(data.dailyGrowth.avgDailyBytes / 1_048_576).toFixed(1)} MB`
                    : `${(data.dailyGrowth.avgDailyBytes / 1024).toFixed(0)} KB`}
                </span>
                /일
              </p>
              <GrowthSparkline bytes={data.dailyGrowth.lastSevenDaysBytes} />
            </div>

            {/* 보관 정리 예상 카드 */}
            <RetentionPreviewCard retentionEligible={data.retentionEligible} />
          </div>
        </>
      )}
    </div>
  );
}
