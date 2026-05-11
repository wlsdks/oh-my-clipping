import {
  Bar,
  BarChart,
  CartesianGrid,
  ReferenceLine,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { EmptyState } from "@/shared/ui/EmptyState";
import { AXIS_PROPS, GRID_PROPS, TossTooltip } from "@/utils/chartTheme";
import type { ScoreDistribution } from "@/types/reviewPolicy";

interface ScoreDistributionChartProps {
  distribution: ScoreDistribution;
  /** INCLUDE 자동 승인 임계값 (0.0 ~ 1.0). null 이면 가이드 라인 생략 */
  threshold: number | null;
}

/**
 * threshold 값이 속한 bucket 의 range 문자열을 찾는다.
 *
 * buckets 는 "0.0-0.1" 같은 하한-상한 포맷이라고 가정한다.
 * 어느 버킷에도 속하지 않으면 null (차트에 수직선을 표시하지 않는다).
 */
function findThresholdBucket(
  buckets: ScoreDistribution["buckets"],
  threshold: number | null,
): string | null {
  if (threshold === null) return null;
  for (const bucket of buckets) {
    // "0.5-0.6" 같은 포맷을 파싱한다. 파싱 실패는 건너뛴다.
    const [lowRaw, highRaw] = bucket.range.split("-");
    const low = Number(lowRaw);
    const high = Number(highRaw);
    if (Number.isNaN(low) || Number.isNaN(high)) continue;
    if (threshold >= low && threshold < high) return bucket.range;
  }
  // 마지막 버킷의 상한(예: 1.0) 과 정확히 같으면 마지막 버킷에 포함시킨다.
  const last = buckets[buckets.length - 1];
  if (last) {
    const [, highRaw] = last.range.split("-");
    if (Number(highRaw) === threshold) return last.range;
  }
  return null;
}

/**
 * 리뷰 항목 importance_score 의 10-bucket 히스토그램.
 *
 * - totalCount === 0 이면 EmptyState 를 노출한다.
 * - threshold 가 buckets 범위 안이면 `data-testid="threshold-line"` 수직 점선을 추가한다.
 */
export function ScoreDistributionChart({
  distribution,
  threshold,
}: ScoreDistributionChartProps) {
  if (distribution.totalCount === 0) {
    return (
      <div className="rounded-2xl border bg-card p-6">
        <EmptyState
          icon="📊"
          title="데이터가 없습니다"
          description="최근 기간 동안 집계된 리뷰 항목이 없습니다."
        />
      </div>
    );
  }

  const thresholdRange = findThresholdBucket(distribution.buckets, threshold);

  return (
    <div className="rounded-2xl border bg-card p-4">
      {/* 상단 요약 라인 — 평균/중앙값/총건수 */}
      <div className="mb-3 flex flex-wrap items-center gap-x-4 gap-y-1 text-sm text-muted-foreground">
        <span>
          평균 <span className="font-medium text-foreground">{distribution.meanScore.toFixed(2)}</span>
        </span>
        <span aria-hidden="true">·</span>
        <span>
          중앙값 <span className="font-medium text-foreground">{distribution.medianScore.toFixed(2)}</span>
        </span>
        <span aria-hidden="true">·</span>
        <span>
          총 <span className="font-medium text-foreground">{distribution.totalCount.toLocaleString()}</span>건
        </span>
      </div>

      {/*
        threshold 가 설정되어 있으면 접근성 친화적 sentinel 을 노출한다.
        - 시각적으로는 차트 안의 ReferenceLine 으로 표시되지만,
          jsdom 은 recharts SVG 를 렌더하지 않으므로 테스트에서는 이 요소로 검증한다.
        - 스크린리더는 "임계값 0.70" 을 한 번 읽어 실제 차트 라인 정보를 얻는다.
      */}
      {thresholdRange !== null && threshold !== null && (
        <div
          data-testid="threshold-line"
          data-threshold={threshold}
          data-bucket={thresholdRange}
          className="sr-only"
        >
          임계값 {threshold.toFixed(2)}
        </div>
      )}

      <div className="h-[200px] w-full">
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={distribution.buckets} margin={{ top: 8, right: 16, bottom: 4, left: 0 }}>
            <CartesianGrid {...GRID_PROPS} />
            <XAxis dataKey="range" {...AXIS_PROPS} />
            <YAxis allowDecimals={false} {...AXIS_PROPS} />
            <Tooltip content={<TossTooltip />} />
            <Bar dataKey="count" name="건수" fill="var(--color-primary)" radius={[4, 4, 0, 0]} />
            {thresholdRange && (
              <ReferenceLine
                x={thresholdRange}
                stroke="var(--color-primary)"
                strokeDasharray="4 4"
                // 수평 레이블을 상단에 살짝 얹어 가이드임을 명시한다.
                label={{
                  value: `임계값 ${threshold?.toFixed(2) ?? ""}`,
                  position: "top",
                  fill: "var(--color-primary)",
                  fontSize: 11,
                }}
              />
            )}
          </BarChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
}
