/** 예산 사용률 색상 임계값 (퍼센트). */
const BUDGET_THRESHOLDS = { warning: 75, danger: 90 } as const;

interface CostBudgetBarProps {
  usedPercent: number;
  spentUsd: number;
  budgetUsd: number;
}

/** 월 예산 대비 사용률을 수평 프로그레스 바로 표시한다. */
export function CostBudgetBar({ usedPercent, spentUsd, budgetUsd }: CostBudgetBarProps) {
  const color =
    usedPercent < BUDGET_THRESHOLDS.warning
      ? "var(--status-success-text)"
      : usedPercent < BUDGET_THRESHOLDS.danger
        ? "var(--status-warning-text)"
        : "var(--status-danger-text)";

  return (
    <div className="space-y-2">
      <div className="flex justify-between text-sm">
        <span className="text-muted-foreground">월 예산 사용률</span>
        <span className="font-medium">
          ${spentUsd.toFixed(2)} / ${budgetUsd.toFixed(2)} ({Math.round(usedPercent)}%)
        </span>
      </div>
      <div className="h-2.5 rounded-full bg-muted overflow-hidden">
        <div
          className="h-full rounded-full transition-all duration-500"
          style={{
            width: `${Math.min(usedPercent, 100)}%`,
            backgroundColor: color,
          }}
        />
      </div>
    </div>
  );
}
