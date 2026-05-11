import { motion } from "framer-motion";
import { cn } from "@/utils/cn";

type ConnectorStatus = "idle" | "flowing" | "complete";

interface PipelineConnectorProps {
  status: ConnectorStatus;
  itemCount?: number | null;
  vertical?: boolean;
}

const HORIZONTAL_WIDTH = 80;
const HORIZONTAL_HEIGHT = 24;
const VERTICAL_WIDTH = 24;
const VERTICAL_HEIGHT = 48;

const DOT_RADIUS = 3.5;

/** 파이프라인 노드 사이를 잇는 SVG 커넥터 */
export function PipelineConnector({
  status,
  itemCount,
  vertical = false,
}: PipelineConnectorProps) {
  const width = vertical ? VERTICAL_WIDTH : HORIZONTAL_WIDTH;
  const height = vertical ? VERTICAL_HEIGHT : HORIZONTAL_HEIGHT;

  return (
    <div
      className={cn(
        "relative flex shrink-0 items-center justify-center",
        vertical ? "flex-col" : "flex-row",
      )}
    >
      <svg
        width={width}
        height={height}
        viewBox={`0 0 ${width} ${height}`}
        fill="none"
        aria-hidden="true"
      >
        {vertical ? (
          <VerticalLine status={status} width={width} height={height} />
        ) : (
          <HorizontalLine status={status} width={width} height={height} />
        )}
      </svg>

      {/* 완료 상태일 때 아이템 수 라벨 */}
      {status === "complete" && itemCount != null && itemCount > 0 && (
        <span
          className={cn(
            "absolute text-xs font-medium text-[var(--status-success-text)]",
            vertical
              ? "left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 translate-x-3"
              : "left-1/2 top-0 -translate-x-1/2 -translate-y-1",
          )}
        >
          {itemCount.toLocaleString()}건
        </span>
      )}
    </div>
  );
}

/** 수평 라인 (좌→우) */
function HorizontalLine({
  status,
  width,
  height,
}: {
  status: ConnectorStatus;
  width: number;
  height: number;
}) {
  const y = height / 2;

  return (
    <>
      <line
        x1={0}
        y1={y}
        x2={width}
        y2={y}
        stroke={lineColor(status)}
        strokeWidth={1.5}
        strokeLinecap="round"
      />

      {status === "flowing" && (
        <motion.circle
          cx={0}
          cy={y}
          r={DOT_RADIUS}
          fill="var(--color-blue-500, #3b82f6)"
          animate={{ cx: [0, width] }}
          transition={{
            duration: 1.2,
            ease: "linear",
            repeat: Infinity,
          }}
        />
      )}
    </>
  );
}

/** 수직 라인 (위→아래) */
function VerticalLine({
  status,
  width,
  height,
}: {
  status: ConnectorStatus;
  width: number;
  height: number;
}) {
  const x = width / 2;

  return (
    <>
      <line
        x1={x}
        y1={0}
        x2={x}
        y2={height}
        stroke={lineColor(status)}
        strokeWidth={1.5}
        strokeLinecap="round"
      />

      {status === "flowing" && (
        <motion.circle
          cx={x}
          cy={0}
          r={DOT_RADIUS}
          fill="var(--color-blue-500, #3b82f6)"
          animate={{ cy: [0, height] }}
          transition={{
            duration: 1.2,
            ease: "linear",
            repeat: Infinity,
          }}
        />
      )}
    </>
  );
}

/** 상태별 선 색상 */
function lineColor(status: ConnectorStatus): string {
  switch (status) {
    case "idle":
      return "var(--color-gray-200, #e5e7eb)";
    case "flowing":
      return "var(--color-blue-400, #60a5fa)";
    case "complete":
      return "var(--status-success-text)";
  }
}
