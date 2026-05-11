import { cn } from "@/utils/cn";
import type {
  PipelineRunRecord,
  PipelineStepName,
  PipelineStepStatusType,
} from "@/types/pipeline";
import { PipelineNode, type NodeStatus, type PipelineNodeMetrics } from "./PipelineNode";
import { PipelineConnector } from "./PipelineConnector";

interface PipelineHeroProps {
  run: PipelineRunRecord | null;
  isLoading: boolean;
}

/** 파이프라인 3단계 순서 */
const STEPS: PipelineStepName[] = ["COLLECT", "SUMMARIZE", "DIGEST"];

/** 스텝 trace status → NodeStatus 매핑 */
function mapTraceStatus(traceStatus: PipelineStepStatusType): NodeStatus {
  switch (traceStatus) {
    case "SUCCEEDED":
      return "SUCCEEDED";
    case "FAILED":
      return "FAILED";
    case "RUNNING":
      return "RUNNING";
    case "SKIPPED":
      return "SKIPPED";
  }
}

/**
 * run 데이터로부터 특정 스텝의 노드 상태를 결정한다.
 * - run이 없으면 IDLE
 * - 해당 스텝의 trace가 있으면 trace.status 사용
 * - run이 RUNNING + trace 없음 + 이전 스텝 모두 SUCCEEDED → RUNNING (현재 스텝)
 * - 그 외 → IDLE
 */
function getNodeStatus(run: PipelineRunRecord | null, step: PipelineStepName): NodeStatus {
  if (!run) return "IDLE";

  // 해당 스텝의 trace 조회
  const trace = run.stepTraces?.find((t) => t.step === step);
  if (trace) return mapTraceStatus(trace.status);

  // run이 실행 중이고 trace가 아직 없는 경우
  if (run.status === "RUNNING") {
    const stepIndex = STEPS.indexOf(step);

    // 이전 스텝들이 모두 SUCCEEDED인지 확인
    const allPreviousSucceeded = STEPS.slice(0, stepIndex).every((prevStep) => {
      const prevTrace = run.stepTraces?.find((t) => t.step === prevStep);
      return prevTrace?.status === "SUCCEEDED";
    });

    if (allPreviousSucceeded) return "RUNNING";
  }

  return "IDLE";
}

/**
 * 커넥터 상태 결정
 * - from=SUCCEEDED, to=RUNNING → "flowing"
 * - from=SUCCEEDED, to=(SUCCEEDED|FAILED) → "complete"
 * - 그 외 → "idle"
 */
function getConnectorStatus(
  fromStatus: NodeStatus,
  toStatus: NodeStatus,
): "idle" | "flowing" | "complete" {
  if (fromStatus !== "SUCCEEDED") return "idle";

  if (toStatus === "RUNNING") return "flowing";
  if (toStatus === "SUCCEEDED" || toStatus === "FAILED") return "complete";
  return "idle";
}

/** run 데이터에서 스텝별 메트릭 추출 */
function getMetrics(
  run: PipelineRunRecord | null,
  step: PipelineStepName,
): PipelineNodeMetrics | undefined {
  if (!run) return undefined;

  // 해당 스텝의 trace가 없으면 메트릭 없음
  const trace = run.stepTraces?.find((t) => t.step === step);
  if (!trace) return undefined;

  switch (step) {
    case "COLLECT":
      return { itemsProcessed: run.totalCollected, itemsSkipped: 0 };
    case "SUMMARIZE":
      return {
        itemsProcessed: run.totalSummarized,
        itemsSkipped: Math.max(0, run.totalCollected - run.totalSummarized),
      };
    case "DIGEST":
      return {
        itemsProcessed: run.totalDigestSelected,
        itemsSkipped: Math.max(0, run.totalSummarized - run.totalDigestSelected),
      };
  }
}

/** 커넥터에 표시할 아이템 수 (from 스텝 기준 출력 개수) */
function getItemCount(run: PipelineRunRecord | null, fromStep: PipelineStepName): number | null {
  if (!run) return null;

  switch (fromStep) {
    case "COLLECT":
      return run.totalCollected > 0 ? run.totalCollected : null;
    case "SUMMARIZE":
      return run.totalSummarized > 0 ? run.totalSummarized : null;
    default:
      return null;
  }
}

/** 스텝의 소요 시간 추출 */
function getDuration(
  run: PipelineRunRecord | null,
  step: PipelineStepName,
): number | undefined {
  if (!run) return undefined;
  const trace = run.stepTraces?.find((t) => t.step === step);
  return trace?.durationMs ?? undefined;
}

/** 파이프라인 3-노드 히어로 섹션 (수집 → 요약 → 다이제스트) */
export function PipelineHero({ run, isLoading }: PipelineHeroProps) {
  // 로딩 스켈레톤
  if (isLoading) {
    return (
      <div className="py-6">
        <div className="flex flex-col items-center gap-4 md:flex-row md:justify-center md:gap-0">
          {[0, 1, 2].map((i) => (
            <div key={i} className="flex items-center gap-0">
              <div className="h-36 w-56 animate-pulse rounded-xl bg-muted" />
              {/* 마지막 노드 뒤에는 커넥터 없음 */}
              {i < 2 && (
                <div className="hidden h-px w-20 animate-pulse bg-muted md:block" />
              )}
            </div>
          ))}
        </div>
      </div>
    );
  }

  // 각 스텝의 상태 계산
  const statuses = STEPS.map((step) => getNodeStatus(run, step));

  return (
    <div className="py-6">
      {/* 데스크톱: 수평 배치 / 모바일: 수직 배치 */}
      <div className="flex flex-col items-center gap-0 md:flex-row md:justify-center">
        {STEPS.map((step, idx) => (
          <div
            key={step}
            className={cn(
              "flex items-center",
              // 모바일: 세로 방향
              "flex-col md:flex-row",
            )}
          >
            <PipelineNode
              step={step}
              status={statuses[idx]}
              metrics={getMetrics(run, step)}
              durationMs={getDuration(run, step)}
            />

            {/* 마지막 노드 뒤에는 커넥터 없음 */}
            {idx < STEPS.length - 1 && (
              <>
                {/* 데스크톱: 수평 커넥터 */}
                <div className="hidden md:block">
                  <PipelineConnector
                    status={getConnectorStatus(statuses[idx], statuses[idx + 1])}
                    itemCount={getItemCount(run, step)}
                  />
                </div>
                {/* 모바일: 수직 커넥터 */}
                <div className="block md:hidden">
                  <PipelineConnector
                    status={getConnectorStatus(statuses[idx], statuses[idx + 1])}
                    itemCount={getItemCount(run, step)}
                    vertical
                  />
                </div>
              </>
            )}
          </div>
        ))}
      </div>

      {/* run 데이터 없음 안내 */}
      {!run && (
        <p className="mt-6 text-center text-sm text-muted-foreground">
          아직 실행 기록이 없어요
        </p>
      )}
    </div>
  );
}
