import { CheckCircle, AlertTriangle, XCircle, Archive } from "lucide-react";
import { Button } from "@/components/ui/button";
import { cn } from "@/utils/cn";
import { relativeTime } from "@/utils/date";
import type { HealthLevel } from "./sourceHelpers";

interface SourcesHeaderProps {
  healthyCount: number;
  warningCount: number;
  errorCount: number;
  archivedCount: number;
  totalCount: number;
  lastPipelineTime?: string | null;
  activeFilter: HealthLevel | null;
  onAddClick: () => void;
  onFilterClick: (filter: HealthLevel) => void;
}

const HEALTH_CARDS: {
  key: HealthLevel;
  label: string;
  icon: typeof CheckCircle;
  bg: string;
  iconColor: string;
}[] = [
  {
    key: "healthy",
    label: "정상",
    icon: CheckCircle,
    bg: "bg-[var(--status-success-bg)]",
    iconColor: "text-[var(--status-success-text)]",
  },
  {
    key: "warning",
    label: "주의",
    icon: AlertTriangle,
    bg: "bg-[var(--status-warning-bg)]",
    iconColor: "text-[var(--status-warning-text)]",
  },
  {
    key: "error",
    label: "에러",
    icon: XCircle,
    bg: "bg-[var(--status-danger-bg)]",
    iconColor: "text-[var(--status-danger-text)]",
  },
  {
    key: "archived",
    label: "보관",
    icon: Archive,
    bg: "bg-muted",
    iconColor: "text-muted-foreground",
  },
];

function getCount(key: HealthLevel, props: SourcesHeaderProps): number {
  switch (key) {
    case "healthy": return props.healthyCount;
    case "warning": return props.warningCount;
    case "error": return props.errorCount;
    case "archived": return props.archivedCount;
    default: return 0;
  }
}

function getPercent(count: number, total: number): string {
  if (total === 0) return "0%";
  return `${Math.round((count / total) * 100)}%`;
}

export function SourcesHeader(props: SourcesHeaderProps) {
  const { lastPipelineTime, onAddClick, onFilterClick, activeFilter, totalCount } = props;

  return (
    <div className="space-y-4">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-xl sm:text-2xl font-bold">뉴스 소스</h1>
          {lastPipelineTime && (
            <p className="text-sm text-muted-foreground mt-1">
              마지막 파이프라인 {relativeTime(lastPipelineTime)}
            </p>
          )}
        </div>
        <Button onClick={onAddClick}>소스 추가</Button>
      </div>

      {/* 헬스 요약 카드 */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
        {HEALTH_CARDS.map((card) => {
          const count = getCount(card.key, props);
          const isSelected = activeFilter === card.key;
          const Icon = card.icon;
          return (
            <button
              key={card.key}
              type="button"
              className={cn(
                "rounded-xl border p-3 text-left transition-all hover:ring-1 hover:ring-primary/30",
                "bg-card",
                isSelected && "ring-2 ring-primary bg-primary/5",
              )}
              onClick={() => onFilterClick(card.key)}
            >
              <div className="flex items-center gap-2 mb-1">
                <div className={cn("rounded-lg p-1.5", card.bg)}>
                  <Icon size={14} className={card.iconColor} />
                </div>
                <span className="text-xs text-muted-foreground">{card.label}</span>
              </div>
              <div className="flex items-baseline gap-1.5">
                <span className="text-lg font-bold tabular-nums">{count}</span>
                <span className="text-xs text-muted-foreground">
                  {getPercent(count, totalCount)}
                </span>
              </div>
            </button>
          );
        })}
      </div>
    </div>
  );
}
