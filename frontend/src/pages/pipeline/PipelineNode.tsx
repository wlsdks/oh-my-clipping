import { motion } from "framer-motion";
import {
  CheckCircle2,
  AlertCircle,
  SkipForward,
  Loader2,
  Download,
  Brain,
  Send,
} from "lucide-react";
import { cn } from "@/utils/cn";
import type { PipelineStepName } from "@/types/pipeline";

/** PipelineNode에 표시할 상태 (IDLE은 아직 시작 전) */
export type NodeStatus = "IDLE" | "RUNNING" | "SUCCEEDED" | "FAILED" | "SKIPPED";

export interface PipelineNodeMetrics {
  itemsProcessed: number;
  itemsSkipped: number;
}

export interface PipelineNodeProps {
  step: PipelineStepName;
  status: NodeStatus;
  metrics?: PipelineNodeMetrics;
  durationMs?: number;
}

/** 스텝별 아이콘/라벨 매핑 */
const STEP_CONFIG: Record<PipelineStepName, { icon: typeof Download; label: string }> = {
  COLLECT: { icon: Download, label: "수집" },
  SUMMARIZE: { icon: Brain, label: "요약" },
  DIGEST: { icon: Send, label: "다이제스트" },
};

/** 상태별 좌측 보더 색상 */
const STATUS_BORDER: Record<NodeStatus, string> = {
  IDLE: "border-l-muted-foreground/30",
  RUNNING: "border-l-[var(--status-neutral-text)]",
  SUCCEEDED: "border-l-[var(--status-success-text)]",
  FAILED: "border-l-[var(--status-danger-text)]",
  SKIPPED: "border-l-[var(--status-warning-text)]",
};

/** 상태별 도트 색상 */
const STATUS_DOT: Record<NodeStatus, string> = {
  IDLE: "bg-muted-foreground/40",
  RUNNING: "bg-[var(--status-neutral-text)]",
  SUCCEEDED: "bg-[var(--status-success-text)]",
  FAILED: "bg-[var(--status-danger-text)]",
  SKIPPED: "bg-[var(--status-warning-text)]",
};

/** 상태별 텍스트 라벨 */
const STATUS_LABEL: Record<NodeStatus, string> = {
  IDLE: "대기 중",
  RUNNING: "실행 중",
  SUCCEEDED: "완료",
  FAILED: "실패",
  SKIPPED: "건너뜀",
};

import { formatDuration } from "./pipelineUtils";

/** 상태별 우측 아이콘 */
function StatusIcon({ status }: { status: NodeStatus }) {
  switch (status) {
    case "RUNNING":
      return <Loader2 className="size-4 animate-spin text-[var(--status-neutral-text)]" />;
    case "SUCCEEDED":
      return <CheckCircle2 className="size-4 text-[var(--status-success-text)]" />;
    case "FAILED":
      return <AlertCircle className="size-4 text-[var(--status-danger-text)]" />;
    case "SKIPPED":
      return <SkipForward className="size-4 text-[var(--status-warning-text)]" />;
    default:
      return null;
  }
}

export function PipelineNode({ step, status, metrics, durationMs }: PipelineNodeProps) {
  const { icon: StepIcon, label } = STEP_CONFIG[step];
  const isActive = status !== "IDLE";

  const card = (
    <div
      className={cn(
        "w-56 rounded-xl border border-border border-l-4 bg-card p-4 shadow-sm transition-shadow duration-200",
        STATUS_BORDER[status],
        status === "RUNNING" && "shadow-md",
      )}
    >
      {/* 헤더: 아이콘 + 라벨 */}
      <div className="flex items-center gap-2">
        <StepIcon className="size-5 text-muted-foreground" />
        <span className="text-sm font-semibold text-foreground">{label}</span>
      </div>

      {/* 상태 행: 도트 + 라벨 + 아이콘 */}
      <div className="mt-3 flex items-center gap-2">
        <span className={cn("size-2 shrink-0 rounded-full", STATUS_DOT[status])} />
        <span className="text-xs text-muted-foreground">{STATUS_LABEL[status]}</span>
        <span className="ml-auto">
          <StatusIcon status={status} />
        </span>
      </div>

      {/* 메트릭 (IDLE이 아닐 때만) */}
      {isActive && metrics && (
        <p className="mt-2 text-xs text-muted-foreground" data-testid="pipeline-node-metrics">
          {metrics.itemsProcessed}건 처리 · {metrics.itemsSkipped}건 건너뛰기
        </p>
      )}

      {/* 소요 시간 (IDLE, RUNNING이 아닐 때만) */}
      {isActive && status !== "RUNNING" && durationMs != null && (
        <p className="mt-1 text-xs text-muted-foreground/70" data-testid="pipeline-node-duration">
          {formatDuration(durationMs)}
        </p>
      )}
    </div>
  );

  // RUNNING 상태: 미세한 scale pulse 애니메이션
  if (status === "RUNNING") {
    return (
      <motion.div
        animate={{ scale: [1, 1.02, 1] }}
        transition={{ duration: 2, repeat: Infinity, ease: "easeInOut" }}
      >
        {card}
      </motion.div>
    );
  }

  return card;
}
