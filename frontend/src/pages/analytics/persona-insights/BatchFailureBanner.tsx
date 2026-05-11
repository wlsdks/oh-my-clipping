import { AlertTriangle } from "lucide-react";
import { Button } from "@/components/ui/button";
import type { PersonaBatchRunDto } from "@/types/personaAnalytics";
import { formatKoreanDateTime } from "@/utils/date";

interface BatchFailureBannerProps {
  latestRun: PersonaBatchRunDto | null | undefined;
  onRetry: () => void;
}

const STALE_THRESHOLD_MS = 24 * 60 * 60 * 1000;

/**
 * 상단 빨간 배너. Spec §1 — 배치 실패 or 24h 이상 지연 시 노출.
 * 그 외에는 null 을 반환해 DOM 에 흔적을 남기지 않는다.
 */
export function BatchFailureBanner({
  latestRun,
  onRetry,
}: BatchFailureBannerProps) {
  if (!latestRun) return null;

  const failed = latestRun.overallStatus === "FAILED";
  const stale = isStale(latestRun.startedAt);

  if (!failed && !stale) return null;

  const heading = failed
    ? "어제 배치 집계가 실패했어요"
    : "배치 집계가 24시간 이상 지연되고 있어요";

  const detail = failed
    ? latestRun.errorMessage
      ? `실패 사유: ${latestRun.errorMessage}`
      : "실패 사유를 확인하고 다시 집계해 주세요."
    : `마지막 시작: ${formatKoreanDateTime(latestRun.startedAt)}`;

  return (
    <div
      role="alert"
      className="rounded-2xl border border-[var(--status-danger-text)]/30 bg-[var(--status-danger-bg)] p-4 flex flex-wrap items-start gap-3"
    >
      <AlertTriangle
        className="h-5 w-5 flex-shrink-0 text-[var(--status-danger-text)]"
        aria-hidden
      />
      <div className="flex-1 min-w-0 space-y-1">
        <p className="font-semibold text-[var(--status-danger-text)]">
          {heading}
        </p>
        <p className="text-sm text-[var(--status-danger-text)]/90">{detail}</p>
        <p className="text-xs text-[var(--status-danger-text)]/80">
          아래 위험/성장 카드는 마지막 성공 기준 데이터일 수 있어요.
        </p>
      </div>
      <Button size="sm" variant="destructive" onClick={onRetry}>
        다시 집계
      </Button>
    </div>
  );
}

function isStale(startedAt: string | null | undefined): boolean {
  if (!startedAt) return false;
  const started = Date.parse(startedAt);
  if (Number.isNaN(started)) return false;
  return Date.now() - started >= STALE_THRESHOLD_MS;
}
