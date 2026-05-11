import { TrendingUp, TrendingDown, Minus } from "lucide-react";
import { Link } from "react-router-dom";
import { cn } from "@/utils/cn";
import { InfoTooltip } from "@/components/shared/InfoTooltip";

export type KpiCardStatus = "success" | "warning" | "danger" | "neutral";

export interface KpiCardProps {
  label: string;
  value: string;
  subtitle?: string;
  tooltip?: string;
  trend?: { value: string; direction: "up" | "down" | "neutral" };
  loading?: boolean;
  onClick?: () => void;
  href?: string;
  status?: KpiCardStatus;
}

const statusBorderClass: Record<string, string> = {
  success: "border-l-4 border-l-[var(--status-success-text)]",
  warning: "border-l-4 border-l-[var(--status-warning-text)]",
  danger: "border-l-4 border-l-[var(--status-danger-text)]",
};

export function KpiCard({ label, value, subtitle, tooltip, trend, loading, onClick, href, status }: KpiCardProps) {
  if (loading) {
    return (
      <div className="animate-pulse rounded-xl border border-border bg-muted/30 p-4 space-y-2">
        <div className="h-3 w-16 rounded bg-muted" />
        <div className="h-7 w-24 rounded bg-muted" />
        <div className="h-3 w-20 rounded bg-muted" />
      </div>
    );
  }

  const isClickable = !!href || !!onClick;

  const content = (
    <>
      <div className="flex items-center gap-1">
        <p className="text-xs text-muted-foreground">{label}</p>
        {tooltip && <InfoTooltip content={tooltip} ariaLabel={`${label} 설명`} />}
      </div>
      <p className="text-2xl font-bold tabular-nums tracking-tight">{value}</p>
      {subtitle && (
        <p className="text-xs text-muted-foreground">{subtitle}</p>
      )}
      {trend && (
        <span
          className={cn(
            "inline-flex items-center gap-0.5 text-xs font-medium",
            trend.direction === "up" && "text-[var(--status-success-text)]",
            trend.direction === "down" && "text-[var(--status-danger-text)]",
            trend.direction === "neutral" && "text-muted-foreground",
          )}
        >
          {trend.direction === "up" && <TrendingUp size={12} />}
          {trend.direction === "down" && <TrendingDown size={12} />}
          {trend.direction === "neutral" && <Minus size={12} />}
          {trend.value}
        </span>
      )}
    </>
  );

  const baseClass = cn(
    "rounded-xl border border-border bg-card p-4 space-y-1",
    status && status !== "neutral" && statusBorderClass[status],
    isClickable && "cursor-pointer hover:border-primary/50 transition-colors",
  );

  if (href) {
    return (
      <Link to={href} className={cn("block", baseClass)}>
        {content}
      </Link>
    );
  }

  if (onClick) {
    return (
      <button type="button" onClick={onClick} className={cn("w-full text-left", baseClass)}>
        {content}
      </button>
    );
  }

  return (
    <div className={baseClass}>
      {content}
    </div>
  );
}
