import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { AlertTriangle, ChevronDown } from "lucide-react";
import { Button } from "@/components/ui/button";
import { sourceKeys } from "@/queries/sourceKeys";
import { sourceService } from "@/services/sourceService";
import type { CoverageGap } from "@/services/sourceService";

const DEFAULT_VISIBLE = 3;

/** 커버리지 갭 경고 배너 — severity별 스타일 분기 */
function bannerClasses(severity: string): string {
  if (severity === "HIGH") {
    return "bg-[var(--status-danger-bg)] text-[var(--status-danger-text)]";
  }
  return "bg-[var(--status-warning-bg)] text-[var(--status-warning-text)]";
}

export function CoverageGapBanner() {
  const [expanded, setExpanded] = useState(false);
  const { data } = useQuery({
    queryKey: sourceKeys.coverageGaps(),
    queryFn: () => sourceService.getCoverageGaps(),
    staleTime: 5 * 60 * 1000,
  });

  const gaps: CoverageGap[] = data?.gaps ?? [];

  if (gaps.length === 0) return null;

  // HIGH severity 먼저, 그 다음 나머지
  const sorted = [...gaps].sort((a, b) => {
    if (a.severity === b.severity) return 0;
    return a.severity === "HIGH" ? -1 : 1;
  });
  const visible = expanded ? sorted : sorted.slice(0, DEFAULT_VISIBLE);
  const hiddenCount = sorted.length - DEFAULT_VISIBLE;

  return (
    <div className="space-y-2">
      {visible.map((gap, i) => (
        <div
          key={`${gap.categoryId}-${gap.type}-${i}`}
          className={`rounded-lg border px-4 py-3 flex items-center gap-2.5 text-sm ${bannerClasses(gap.severity)}`}
        >
          <AlertTriangle className="h-4 w-4 shrink-0" />
          <span>
            {gap.categoryName}: {gap.detail}
          </span>
        </div>
      ))}

      {hiddenCount > 0 && (
        <Button
          variant="ghost"
          size="sm"
          className="w-full text-muted-foreground"
          onClick={() => setExpanded((v) => !v)}
        >
          {expanded ? (
            "접기"
          ) : (
            <>
              <ChevronDown size={14} className="mr-1" />
              {hiddenCount}개 더 보기
            </>
          )}
        </Button>
      )}
    </div>
  );
}
