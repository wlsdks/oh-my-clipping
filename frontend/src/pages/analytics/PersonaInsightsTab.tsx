import { useQuery } from "@tanstack/react-query";
import { personaAnalyticsService } from "@/services/personaAnalyticsService";
import { personaAnalyticsKeys } from "@/queries/personaAnalyticsKeys";
import { EmptyState } from "@/components/shared/EmptyState";
import { Button } from "@/components/ui/button";
import type { SignalsResponse } from "@/types/personaAnalytics";
import { AtRiskPersonaSection } from "./persona-insights/AtRiskPersonaSection";
import { BatchFailureBanner } from "./persona-insights/BatchFailureBanner";
import { BatchFreshnessFooter } from "./persona-insights/BatchFreshnessFooter";
import { GrowthPersonaSection } from "./persona-insights/GrowthPersonaSection";
import { PortfolioSummary } from "./persona-insights/PortfolioSummary";
import { WeeklyTrendsChart } from "./persona-insights/WeeklyTrendsChart";

const REFETCH_INTERVAL_MS = 5 * 60 * 1000;
const STALE_TIME_MS = 60 * 1000;

/**
 * Analytics > Persona Insights 탭의 메인 컨테이너.
 *
 * Spec: docs/superpowers/specs/2026-04-17-persona-insights-redesign-design.md
 *
 * 페이지 구조 (§1):
 *   1. 배치 실패 배너 (조건부)
 *   2. 헤더 스트립 (주의 N · ↑ 성장 K · 유휴 L)
 *   3. 주의가 필요한 페르소나 섹션
 *   4. 잘 되고 있는 페르소나 섹션
 *   5. 주간 추이 차트
 *   6. 포트폴리오 요약
 *   7. 배치 신선도 푸터
 */
export function PersonaInsightsTab() {
  const signalsQuery = useQuery({
    queryKey: personaAnalyticsKeys.signals(4),
    queryFn: () => personaAnalyticsService.getSignals(4),
    staleTime: STALE_TIME_MS,
    refetchInterval: REFETCH_INTERVAL_MS,
    refetchIntervalInBackground: false,
  });

  const liveQuery = useQuery({
    queryKey: personaAnalyticsKeys.live(),
    queryFn: () => personaAnalyticsService.getLive(),
    staleTime: STALE_TIME_MS,
    refetchInterval: REFETCH_INTERVAL_MS,
    refetchIntervalInBackground: false,
  });

  const batchRunsQuery = useQuery({
    queryKey: personaAnalyticsKeys.batchRuns(),
    queryFn: () => personaAnalyticsService.getBatchRuns(1),
    staleTime: STALE_TIME_MS,
  });

  if (signalsQuery.isLoading || liveQuery.isLoading) return <PageSkeleton />;

  // 둘 다 에러면 완전 폴백.
  if (liveQuery.isError && signalsQuery.isError && !liveQuery.data) {
    return <PageError onRetry={() => {
      signalsQuery.refetch();
      liveQuery.refetch();
    }} />;
  }

  if (!liveQuery.data) {
    return (
      <EmptyState
        title="아직 통계 데이터가 없어요"
        description="아직 집계된 페르소나 통계가 없어요"
      />
    );
  }

  const signals = signalsQuery.data;
  const live = liveQuery.data;
  const latestRun = batchRunsQuery.data?.[0] ?? null;

  const bannerActive =
    !!latestRun && (latestRun.overallStatus === "FAILED" || isStale(latestRun.startedAt));

  return (
    <div className="space-y-8 pb-12">
      <BatchFailureBanner
        latestRun={latestRun}
        onRetry={() => batchRunsQuery.refetch()}
      />

      <div
        className={bannerActive ? "pointer-events-none opacity-50" : undefined}
        aria-hidden={bannerActive ? "true" : undefined}
      >
        <div className="space-y-8">
          <HeaderStrip signals={signals} />

          <section aria-labelledby="at-risk" id="at-risk" className="scroll-mt-6">
            <h2 id="at-risk" className="text-lg font-semibold mb-3">
              주의가 필요한 페르소나
            </h2>
            {signalsQuery.isError ? (
              <SignalsError onRetry={() => signalsQuery.refetch()} retrying={signalsQuery.isFetching} />
            ) : (
              <AtRiskPersonaSection
                items={signals?.risks ?? []}
                currentWeekIso={signals?.asOfWeekIso ?? ""}
                growthItems={signals?.growth ?? []}
              />
            )}
          </section>

          <section aria-labelledby="growth" id="growth" className="scroll-mt-6">
            <h2 id="growth" className="text-lg font-semibold mb-3">
              잘 되고 있는 페르소나
            </h2>
            {signalsQuery.isError ? (
              <SignalsError onRetry={() => signalsQuery.refetch()} retrying={signalsQuery.isFetching} />
            ) : (
              <GrowthPersonaSection
                items={signals?.growth ?? []}
                riskItems={signals?.risks ?? []}
              />
            )}
          </section>

          <section aria-labelledby="trends" id="trends" className="scroll-mt-6">
            <h2 id="trends" className="text-lg font-semibold mb-3">주간 추이</h2>
            <WeeklyTrendsChart />
          </section>

          <section
            aria-labelledby="portfolio"
            id="portfolio"
            className="scroll-mt-6"
          >
            <h2 id="portfolio" className="text-lg font-semibold mb-3">
              포트폴리오
            </h2>
            <PortfolioSummary live={live} excluded={signals?.excluded ?? []} />
          </section>

          <BatchFreshnessFooter
            asOfSnapshotDate={signals?.asOfSnapshotDate ?? live.asOf}
            isWeekComplete={signals?.isWeekComplete ?? true}
            latestRun={latestRun}
            weeks={12}
          />
        </div>
      </div>
    </div>
  );
}

const STALE_THRESHOLD_MS = 24 * 60 * 60 * 1000;

function isStale(startedAt: string | null | undefined): boolean {
  if (!startedAt) return false;
  const started = Date.parse(startedAt);
  if (Number.isNaN(started)) return false;
  return Date.now() - started >= STALE_THRESHOLD_MS;
}

interface HeaderStripProps {
  signals: SignalsResponse | undefined;
}

function HeaderStrip({ signals }: HeaderStripProps) {
  if (!signals) {
    return (
      <p className="text-sm text-muted-foreground" data-testid="header-strip">
        신호를 불러오는 중이에요…
      </p>
    );
  }

  const risks = signals.risks;
  const growth = signals.growth;
  const newRiskCount = risks.filter((r) => r.persistentWeeks === 1).length;
  const idleCount = risks.filter((r) => r.riskType === "IDLE").length;

  return (
    <p
      className="text-sm text-muted-foreground tabular-nums"
      data-testid="header-strip"
    >
      <span className="text-[var(--status-danger-text)] font-medium">
        주의 {risks.length}
      </span>
      {newRiskCount > 0 && ` (NEW ${newRiskCount})`}
      <span className="mx-2">·</span>
      <span className="text-[var(--status-success-text)] font-medium">
        성장 {growth.length}
      </span>
      <span className="mx-2">·</span>
      <span>유휴 {idleCount}</span>
    </p>
  );
}

interface SignalsErrorProps {
  onRetry: () => void;
  retrying?: boolean;
}

function SignalsError({ onRetry, retrying }: SignalsErrorProps) {
  // 재시도 중이면 스켈레톤 복귀 없이 에러 UI 유지 (AGENTS.md 피드백 패턴).
  return (
    <div
      role="alert"
      className="rounded-2xl border border-[var(--status-danger-text)]/30 bg-[var(--status-danger-bg)] p-4 flex items-center justify-between gap-3"
    >
      <div>
        <p className="font-semibold text-[var(--status-danger-text)]">
          신호를 불러오지 못했어요
        </p>
        <p className="text-sm text-[var(--status-danger-text)]/80">
          잠시 후 다시 시도해 주세요.
        </p>
      </div>
      <Button
        size="sm"
        variant="outline"
        onClick={onRetry}
        disabled={retrying}
      >
        {retrying ? "시도 중…" : "다시 시도"}
      </Button>
    </div>
  );
}

function PageSkeleton() {
  return (
    <div className="space-y-8 animate-pulse">
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
        {Array.from({ length: 4 }).map((_, i) => (
          <div key={i} className="rounded-2xl border bg-card h-24" />
        ))}
      </div>
      <div className="rounded-2xl border bg-card h-64" />
      <div className="rounded-2xl border bg-card h-48" />
    </div>
  );
}

function PageError({ onRetry }: { onRetry: () => void }) {
  return (
    <div className="rounded-2xl border bg-card p-8 text-center">
      <p className="font-semibold">데이터를 불러오지 못했어요</p>
      <p className="text-sm text-muted-foreground mt-1">
        잠시 후 다시 시도해 주세요.
      </p>
      <button
        type="button"
        className="mt-4 text-sm text-primary hover:underline"
        onClick={onRetry}
      >
        다시 시도
      </button>
    </div>
  );
}
