import { cn } from "@/utils/cn";
import type { UserAccountApproval } from "@/types/user";
import { getActivityStatus } from "./memberFilters";
import type { MemberFilters } from "./memberFilters";
import { Users, Activity, UserX, Bell } from "lucide-react";

interface MetricCardDef {
  key: string;
  label: string;
  icon: React.ElementType;
  warn: boolean;
  preset: Partial<MemberFilters> | null;
  count: (members: UserAccountApproval[]) => number;
}

const CARDS: MetricCardDef[] = [
  {
    key: "total",
    label: "총 회원",
    icon: Users,
    warn: false,
    preset: null,
    count: (m) => m.length,
  },
  {
    key: "active",
    label: "활성 (30일)",
    icon: Activity,
    warn: false,
    preset: { activityStatus: "ACTIVE" },
    count: (m) =>
      m.filter((u) => {
        const s = getActivityStatus(u.lastLoginAt);
        return s === "ACTIVE_7D" || s === "SEMI_ACTIVE_30D";
      }).length,
  },
  {
    key: "inactive",
    label: "장기 미접속",
    icon: UserX,
    warn: true,
    preset: { activityStatus: "INACTIVE" },
    count: (m) =>
      m.filter((u) => {
        const s = getActivityStatus(u.lastLoginAt);
        return s === "INACTIVE_30D" || s === "NEVER";
      }).length,
  },
  {
    key: "noSub",
    label: "구독 없음",
    icon: Bell,
    warn: true,
    preset: { subscription: "NONE" },
    count: (m) => m.filter((u) => (u.subscriptionCount ?? 0) === 0).length,
  },
];

interface MemberMetricCardsProps {
  members: UserAccountApproval[];
  activeCardKey: string | null;
  onCardClick: (
    preset: Partial<MemberFilters> | null,
    key: string | null,
  ) => void;
  loading?: boolean;
}

export function MemberMetricCards({
  members,
  activeCardKey,
  onCardClick,
  loading,
}: MemberMetricCardsProps) {
  if (loading) {
    return (
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
        {Array.from({ length: 4 }).map((_, i) => (
          <div
            key={i}
            className="animate-pulse rounded-2xl border border-border bg-muted/30 p-4 space-y-2"
          >
            <div className="h-3 w-16 rounded bg-muted" />
            <div className="h-7 w-12 rounded bg-muted" />
          </div>
        ))}
      </div>
    );
  }

  return (
    <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
      {CARDS.map((card) => {
        const value = card.count(members);
        const isSelected = activeCardKey === card.key;

        return (
          <button
            key={card.key}
            type="button"
            onClick={() =>
              onCardClick(card.preset, isSelected ? null : card.key)
            }
            className={cn(
              "rounded-2xl border p-4 text-left transition-colors duration-150 hover:bg-accent/50 cursor-pointer",
              isSelected
                ? "border-primary bg-primary/5 shadow-sm"
                : "border-border bg-card hover:border-primary/30",
            )}
          >
            <div className="flex items-center gap-1.5 mb-1">
              <card.icon
                className={cn(
                  "h-3.5 w-3.5",
                  card.warn ? "text-[var(--status-warning-text)]" : "text-muted-foreground",
                )}
              />
              <span className="text-xs text-muted-foreground">
                {card.label}
              </span>
            </div>
            <p
              className={cn(
                "text-2xl font-bold tabular-nums tracking-tight",
                card.warn &&
                  value > 0 &&
                  "text-[var(--status-warning-text)]",
              )}
            >
              {value}
              <span className="text-sm font-normal text-muted-foreground ml-0.5">
                명
              </span>
            </p>
          </button>
        );
      })}
    </div>
  );
}
