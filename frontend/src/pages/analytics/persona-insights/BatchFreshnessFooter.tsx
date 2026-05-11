import type { PersonaBatchRunDto } from "@/types/personaAnalytics";
import { BackfillButton } from "./BackfillButton";

interface BatchFreshnessFooterProps {
  asOfSnapshotDate: string;
  isWeekComplete: boolean;
  latestRun?: PersonaBatchRunDto | null;
  weeks: number;
}

/**
 * 페이지 하단 배치 신선도 푸터.
 * Spec §1.7 — 마지막 집계 시각 + 재집계 CTA + 진행 중 주 caveat.
 */
export function BatchFreshnessFooter({
  asOfSnapshotDate,
  isWeekComplete,
  weeks,
}: BatchFreshnessFooterProps) {
  const weekIso = asOfSnapshotDate ? deriveWeekIso(asOfSnapshotDate) : null;

  return (
    <footer className="flex flex-wrap items-center justify-between gap-3 border-t pt-4 text-sm text-muted-foreground">
      <p>
        {asOfSnapshotDate
          ? `마지막 집계: ${asOfSnapshotDate}${weekIso ? ` (ISO ${weekIso})` : ""}`
          : "아직 집계된 주차가 없어요"}
        {!isWeekComplete && (
          <span className="ml-2 text-xs">
            · 현재 주 진행 중 — 마지막 완결 주 기준
          </span>
        )}
      </p>
      <BackfillButton weeks={weeks} />
    </footer>
  );
}

/**
 * `YYYY-MM-DD` 형태의 week_start 를 `WNN` 단축 라벨로 변환한다.
 * 단순 표시용이라 정확한 ISO 주차 계산은 생략 — 이미 계산된 asOfWeekIso 를
 * 소유한 호출자가 있으면 그쪽을 직접 넘겨줄 수 있도록 별도 prop 을 두지 않는다.
 */
function deriveWeekIso(snapshotDate: string): string | null {
  const parsed = new Date(snapshotDate);
  if (Number.isNaN(parsed.getTime())) return null;
  // ISO 8601 week 계산 (월요일 기준)
  const d = new Date(
    Date.UTC(parsed.getFullYear(), parsed.getMonth(), parsed.getDate()),
  );
  const dayNum = d.getUTCDay() || 7;
  d.setUTCDate(d.getUTCDate() + 4 - dayNum);
  const isoYear = d.getUTCFullYear();
  const firstThursday = new Date(Date.UTC(isoYear, 0, 4));
  const firstThursdayDay = firstThursday.getUTCDay() || 7;
  firstThursday.setUTCDate(firstThursday.getUTCDate() + 4 - firstThursdayDay);
  const week =
    1 + Math.round((d.getTime() - firstThursday.getTime()) / (7 * 24 * 3_600_000));
  return `${isoYear}-W${String(week).padStart(2, "0")}`;
}
