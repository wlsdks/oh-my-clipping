import { TrendingUp } from "lucide-react";
import { useNavigate } from "react-router-dom";
import { Button } from "@/components/ui/button";
import {
  GROWTH_LABELS,
  GROWTH_TOOLTIP,
} from "@/constants/personaSignalLabels";
import type { GrowthSignalItem } from "@/types/personaAnalytics";
import { PersonaSignalCardBase } from "./PersonaSignalCardBase";

interface GrowthPersonaCardProps {
  item: GrowthSignalItem;
  /** Optional — used when the same persona also has a risk card. */
  crossReference?: string;
}

/**
 * 성장 페르소나 카드.
 * Spec §3 — CTA 는 성장 타입 × (프리셋/커스텀) 에 따라 달라진다.
 * "이 스타일 프리셋화" 는 스펙 §2.5 에서 OoS — 제안만 남기고 실제 API 없음.
 */
export function GrowthPersonaCard({ item, crossReference }: GrowthPersonaCardProps) {
  const navigate = useNavigate();

  const handleEdit = () => navigate(`/admin/personas`);

  const handleViewSubscribers = () =>
    navigate(`/admin/user-accounts?personaId=${encodeURIComponent(item.personaId)}`);

  const handleViewRecentDeliveries = () =>
    navigate(`/admin/pipeline?personaId=${encodeURIComponent(item.personaId)}`);

  const { primaryLine, secondaryLine } = buildLines(item);
  const ctas = buildCtas({
    item,
    onEdit: handleEdit,
    onViewSubscribers: handleViewSubscribers,
    onViewRecentDeliveries: handleViewRecentDeliveries,
  });

  return (
    <PersonaSignalCardBase
      tone="growth"
      icon={<TrendingUp className="h-5 w-5" aria-hidden />}
      personaName={item.personaName}
      isPreset={item.isPreset}
      typeLabel={GROWTH_LABELS[item.signalType]}
      tooltip={GROWTH_TOOLTIP[item.signalType]}
      persistentWeeks={item.persistentWeeks}
      primaryLine={primaryLine}
      secondaryLine={secondaryLine}
      ctas={ctas}
      crossReference={crossReference}
    />
  );
}

function buildLines(item: GrowthSignalItem): {
  primaryLine: string;
  secondaryLine?: string;
} {
  const d = item.details;
  if (d.type === "SUBS_SURGE") {
    return {
      primaryLine: `${d.prevActiveSubs} → ${d.activeSubs} (+${d.deltaAbs})`,
      secondaryLine: `+${d.deltaPct}%`,
    };
  }
  if (d.type === "ENGAGEMENT_RISE") {
    const prevPct = Math.round(d.prevEngagementRate * 100);
    const curPct = Math.round(d.engagementRate * 100);
    return {
      primaryLine: `${prevPct}% → ${curPct}% (+${d.deltaPp}pp)`,
      secondaryLine: `발송 ${d.deliveredCount}건 · 클릭 ${d.totalClicks}건`,
    };
  }
  // FIRST_SUBSCRIPTION
  return {
    primaryLine: `첫 구독 ${d.activeSubs}명`,
    secondaryLine: `생성 ${d.daysSinceCreation}일차`,
  };
}

interface CtaBuilderArgs {
  item: GrowthSignalItem;
  onEdit: () => void;
  onViewSubscribers: () => void;
  onViewRecentDeliveries: () => void;
}

function buildCtas(args: CtaBuilderArgs) {
  const { item, onEdit, onViewSubscribers, onViewRecentDeliveries } = args;

  const editButton = (
    <Button
      key="edit"
      variant="outline"
      size="sm"
      onClick={onEdit}
      aria-label={`${item.personaName} 페르소나 편집`}
    >
      편집
    </Button>
  );

  const suggestPresetButton = !item.isPreset ? (
    <Button
      key="preset-suggest"
      variant="ghost"
      size="sm"
      disabled
      title="곧 지원될 기능이에요"
    >
      템플릿화 제안
    </Button>
  ) : null;

  if (item.signalType === "SUBS_SURGE") {
    return (
      <>
        {editButton}
        <Button
          key="recent-subs"
          variant="outline"
          size="sm"
          onClick={onViewSubscribers}
        >
          최근 구독자 보기
        </Button>
        {suggestPresetButton}
      </>
    );
  }

  if (item.signalType === "ENGAGEMENT_RISE") {
    return (
      <>
        {editButton}
        <Button
          key="recent-deliveries"
          variant="outline"
          size="sm"
          onClick={onViewRecentDeliveries}
        >
          최근 발송 보기
        </Button>
        {suggestPresetButton}
      </>
    );
  }

  // FIRST_SUBSCRIPTION
  return (
    <>
      {editButton}
      <Button
        key="recent-subs-first"
        variant="outline"
        size="sm"
        onClick={onViewSubscribers}
      >
        최근 구독자 보기
      </Button>
    </>
  );
}
