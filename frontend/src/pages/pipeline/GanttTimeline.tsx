import { cn } from "@/utils/cn";
import type { PipelineStepTraceRecord, PipelineStepName, PipelineStepStatusType } from "@/types/pipeline";

interface GanttTimelineProps {
  traces: PipelineStepTraceRecord[];
  totalDurationMs: number;
}

/** 파이프라인 스텝명 → 한국어 라벨 매핑 */
const STEP_LABELS: Record<PipelineStepName, string> = {
  COLLECT: "수집",
  SUMMARIZE: "요약",
  DIGEST: "다이제스트",
};

/** 스텝 상태별 바 색상 (절제된 톤) */
const BAR_COLORS: Record<PipelineStepStatusType, string> = {
  SUCCEEDED: "bg-[var(--status-success-text)] opacity-70",
  FAILED: "bg-[var(--status-danger-text)] opacity-70",
  SKIPPED: "bg-[var(--status-warning-text)] opacity-70",
  RUNNING: "bg-[var(--status-neutral-text)] opacity-70 animate-pulse",
};

/**
 * 파이프라인 실행 상세에서 각 스텝의 소요 시간을 Gantt 바 형태로 시각화한다.
 * traces가 없거나 totalDurationMs가 0 이하이면 null을 반환한다.
 */
export function GanttTimeline({ traces, totalDurationMs }: GanttTimelineProps) {
  if (!traces.length || totalDurationMs <= 0) return null;

  // 가장 이른 시작 시각을 기준점으로 사용
  const earliestMs = Math.min(...traces.map((t) => new Date(t.startedAt).getTime()));

  return (
    <div className="space-y-1.5 py-2">
      {traces.map((trace) => {
        const stepStartMs = new Date(trace.startedAt).getTime() - earliestMs;
        const stepDuration = trace.durationMs ?? 0;

        // 바 위치와 너비(%)
        const leftPct = (stepStartMs / totalDurationMs) * 100;
        const widthPct = Math.max((stepDuration / totalDurationMs) * 100, 2);

        return (
          <div key={trace.id} className="flex items-center gap-2">
            {/* 스텝 라벨 */}
            <span className="w-20 shrink-0 text-xs text-muted-foreground text-right">
              {STEP_LABELS[trace.step] ?? trace.step}
            </span>

            {/* 바 컨테이너 */}
            <div className="relative h-3 flex-1 rounded-full bg-muted/40">
              <div
                className={cn("absolute top-0 h-full rounded-full", BAR_COLORS[trace.status])}
                style={{ left: `${leftPct}%`, width: `${widthPct}%` }}
              />
            </div>

            {/* 소요시간 */}
            <span className="w-16 shrink-0 text-xs tabular-nums text-muted-foreground">
              {stepDuration >= 1000
                ? `${(stepDuration / 1000).toFixed(1)}초`
                : `${stepDuration}ms`}
            </span>
          </div>
        );
      })}
    </div>
  );
}
