import type { GrowthSignalItem, RiskSignalItem } from "@/types/personaAnalytics";
import { sortByPersistentWeeksAsc } from "@/utils/personaRisk";
import { GrowthPersonaCard } from "./GrowthPersonaCard";

interface GrowthPersonaSectionProps {
  items: GrowthSignalItem[];
  /** For cross-reference: personas that also appear in the risk list. */
  riskItems: RiskSignalItem[];
}

/**
 * 잘 되고 있는 페르소나 카드 리스트 렌더러.
 * Spec §1 — 비어 있으면 중립 톤 안내 카드를 렌더한다.
 */
export function GrowthPersonaSection({
  items,
  riskItems,
}: GrowthPersonaSectionProps) {
  if (items.length === 0) {
    return <EmptyGrowthCard />;
  }

  const sorted = sortByPersistentWeeksAsc(items, (item) => {
    const d = item.details;
    if (d.type === "SUBS_SURGE") return d.deltaPct;
    if (d.type === "ENGAGEMENT_RISE") return d.deltaPp;
    // FIRST_SUBSCRIPTION
    return d.activeSubs;
  });

  const riskPersonaIds = new Set(riskItems.map((r) => r.personaId));

  return (
    <div className="grid gap-3 md:grid-cols-2">
      {sorted.map((item) => (
        <GrowthPersonaCard
          key={`${item.personaId}:${item.signalType}`}
          item={item}
          crossReference={
            riskPersonaIds.has(item.personaId)
              ? "이 페르소나는 이번 주 위험 신호도 함께 가집니다."
              : undefined
          }
        />
      ))}
    </div>
  );
}

function EmptyGrowthCard() {
  return (
    <div className="rounded-2xl border bg-[var(--status-neutral-bg)] p-5" role="status">
      <p className="text-sm text-[var(--status-neutral-text)]">
        이번 주는 눈에 띄는 성장 신호가 없어요.
      </p>
    </div>
  );
}
