interface PipelineRun {
  startedAt: string;
  totalCollected: number;
  totalSummarized: number;
  postedToSlack: boolean;
  status: string;
}

interface PipelineAggregation {
  collected: number;
  summarized: number;
  sent: number;
  lastSuccessAt: string | null;
}

/** 오늘 날짜의 파이프라인 실행을 집계한다. */
export function aggregateTodayPipeline(
  runs: PipelineRun[],
  todayDate?: string,
): PipelineAggregation {
  const today = todayDate ?? new Date().toLocaleDateString("en-CA", { timeZone: "Asia/Seoul" });
  const todayRuns = runs.filter((r) => r.startedAt.slice(0, 10) === today);

  const collected = todayRuns.reduce((sum, r) => sum + r.totalCollected, 0);
  const summarized = todayRuns.reduce((sum, r) => sum + r.totalSummarized, 0);
  const sent = todayRuns.filter((r) => r.postedToSlack).length;

  // 전체 runs에서 가장 최근 성공 실행 시각
  const succeeded = runs
    .filter((r) => r.status === "SUCCEEDED")
    .sort((a, b) => b.startedAt.localeCompare(a.startedAt));
  const lastSuccessAt = succeeded.length > 0 ? succeeded[0].startedAt : null;

  return { collected, summarized, sent, lastSuccessAt };
}
