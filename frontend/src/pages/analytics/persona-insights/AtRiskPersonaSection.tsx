import { CheckCircle } from "lucide-react";
import type { GrowthSignalItem, RiskSignalItem } from "@/types/personaAnalytics";
import { sortByPersistentWeeksAsc } from "@/utils/personaRisk";
import { AtRiskPersonaCard } from "./AtRiskPersonaCard";

interface AtRiskPersonaSectionProps {
  items: RiskSignalItem[];
  currentWeekIso: string;
  /** For cross-reference: personas that also appear in the growth list. */
  growthItems: GrowthSignalItem[];
}

/**
 * 주의가 필요한 페르소나 카드 리스트 렌더러.
 * Spec §1 — 비어 있으면 녹색 "✓" 카드로 유지한다 (섹션 숨김 금지).
 */
export function AtRiskPersonaSection({
  items,
  currentWeekIso,
  growthItems,
}: AtRiskPersonaSectionProps) {
  if (items.length === 0) {
    return <EmptyRiskCard />;
  }

  // 정렬: persistentWeeks ASC → tie 는 변화폭 큰 것 우선.
  const sorted = sortByPersistentWeeksAsc(items, (item) => {
    const d = item.details;
    if (d.type === "ENGAGEMENT_DROP") return Math.abs(d.deltaPp);
    if (d.type === "CHURN_EXCESS") return d.churnedSubs;
    // IDLE
    return d.consecutiveWeeks;
  });

  const growthPersonaIds = new Set(growthItems.map((g) => g.personaId));

  return (
    <div className="grid gap-3 md:grid-cols-2">
      {sorted.map((item) => (
        <AtRiskPersonaCard
          key={`${item.personaId}:${item.riskType}`}
          item={item}
          currentWeekIso={currentWeekIso}
          crossReference={
            growthPersonaIds.has(item.personaId)
              ? "이 페르소나는 이번 주 성장 신호도 함께 가집니다."
              : undefined
          }
        />
      ))}
    </div>
  );
}

function EmptyRiskCard() {
  return (
    <div
      className="rounded-2xl border bg-[var(--status-success-bg)] p-5 flex items-center gap-3"
      role="status"
    >
      <CheckCircle
        className="h-5 w-5 text-[var(--status-success-text)]"
        aria-hidden
      />
      <p className="text-sm font-medium text-[var(--status-success-text)]">
        이번 주 주의가 필요한 페르소나가 없어요.
      </p>
    </div>
  );
}
