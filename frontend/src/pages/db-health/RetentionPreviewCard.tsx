import { Archive } from "lucide-react";
import type { RetentionEligibleSummary } from "@/types/dbMetrics";

interface RetentionPreviewCardProps {
  retentionEligible: RetentionEligibleSummary;
}

function formatMb(bytes: number): string {
  const mb = bytes / (1024 * 1024);
  return mb < 1 ? `${(bytes / 1024).toFixed(0)} KB` : `약 ${mb.toFixed(0)} MB`;
}

export function RetentionPreviewCard({ retentionEligible }: RetentionPreviewCardProps) {
  const { rssItemsOlderThanCutoff, batchSummariesOlderThanCutoffExcludingAnchored, projectedBytesFreed } =
    retentionEligible;

  const hasData = rssItemsOlderThanCutoff > 0 || batchSummariesOlderThanCutoffExcludingAnchored > 0;

  return (
    <div className="rounded-2xl bg-card border shadow-sm p-6">
      <div className="flex items-center gap-2 mb-4">
        <Archive className="h-5 w-5 text-[var(--status-neutral-text)]" />
        <h2 className="text-base font-semibold">다음 정리 예상</h2>
      </div>

      {!hasData ? (
        <p className="text-sm text-muted-foreground py-2">정리 대상 데이터가 없어요</p>
      ) : (
        <div className="space-y-3">
          <div className="flex items-center justify-between">
            <span className="text-sm text-muted-foreground">원본 RSS 기사</span>
            <span className="text-sm font-semibold tabular-nums">
              {rssItemsOlderThanCutoff.toLocaleString()}건
            </span>
          </div>
          <div className="flex items-center justify-between">
            <span className="text-sm text-muted-foreground">AI 요약 (앵커 제외)</span>
            <span className="text-sm font-semibold tabular-nums">
              {batchSummariesOlderThanCutoffExcludingAnchored.toLocaleString()}건
            </span>
          </div>
          <div className="border-t pt-3">
            <div className="flex items-center justify-between">
              <span className="text-sm text-muted-foreground font-medium">예상 회수 용량</span>
              <span className="text-sm font-bold text-[var(--status-success-text)]">
                {formatMb(projectedBytesFreed)}
              </span>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
