// frontend/src/pages/user-accounts/components/ApprovalSummaryCards.tsx
import { Clock, XCircle, CheckCircle2 } from "lucide-react";
import { cn } from "@/utils/cn";

interface ApprovalSummaryCardsProps {
  pendingCount: number;
  rejectedCount: number;
  weeklyProcessedCount: number;
  activeCard: "PENDING" | "REJECTED" | null;
  onCardClick: (card: "PENDING" | "REJECTED" | null) => void;
  isLoading?: boolean;
}

const cards = [
  { key: "PENDING" as const, label: "승인 대기", icon: Clock, clickable: true },
  { key: "REJECTED" as const, label: "반려", icon: XCircle, clickable: true },
  { key: null, label: "이번 주 처리", icon: CheckCircle2, clickable: false },
] as const;

export function ApprovalSummaryCards({
  pendingCount, rejectedCount, weeklyProcessedCount,
  activeCard, onCardClick, isLoading,
}: ApprovalSummaryCardsProps) {
  const counts: Record<string, number> = {
    PENDING: pendingCount,
    REJECTED: rejectedCount,
    WEEKLY: weeklyProcessedCount,
  };

  if (isLoading) {
    return (
      <div className="grid grid-cols-3 gap-3">
        {Array.from({ length: 3 }).map((_, i) => (
          <div key={i} className="rounded-2xl border bg-card p-4 animate-pulse">
            <div className="h-3 w-16 bg-muted rounded mb-2 mx-auto" />
            <div className="h-7 w-10 bg-muted rounded mx-auto" />
          </div>
        ))}
      </div>
    );
  }

  return (
    <div className="grid grid-cols-3 gap-3">
      {cards.map((card) => {
        const isActive = card.key !== null && activeCard === card.key;
        const count = card.key ? counts[card.key] : counts.WEEKLY;
        const Icon = card.icon;
        return (
          <button
            key={card.label}
            type="button"
            disabled={!card.clickable}
            onClick={() => {
              if (!card.clickable || card.key === null) return;
              onCardClick(isActive ? null : card.key);
            }}
            className={cn(
              "rounded-2xl border bg-card p-4 text-center transition-all",
              card.clickable && "cursor-pointer hover:shadow-sm",
              !card.clickable && "cursor-default",
              isActive && "border-primary bg-primary/5 shadow-sm",
            )}
          >
            <div className="flex items-center justify-center gap-1.5 mb-1">
              <Icon size={14} className="text-muted-foreground" />
              <span className="text-xs text-muted-foreground">{card.label}</span>
            </div>
            <div className={cn(
              "text-2xl font-bold",
              card.key === "PENDING" && "text-primary",
              (card.key === "REJECTED" || card.key === null) && "text-foreground",
            )}>
              {count}
            </div>
          </button>
        );
      })}
    </div>
  );
}
