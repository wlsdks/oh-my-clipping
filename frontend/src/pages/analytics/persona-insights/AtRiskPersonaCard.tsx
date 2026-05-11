import { AlertTriangle } from "lucide-react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { useNavigate } from "react-router-dom";
import { Button } from "@/components/ui/button";
import { api } from "@/lib/kyInstance";
import { personaAnalyticsKeys } from "@/queries/personaAnalyticsKeys";
import { userFriendlyMessage } from "@/shared/lib/httpError";
import { RISK_LABELS, RISK_TOOLTIP } from "@/constants/personaSignalLabels";
import type { RiskSignalItem } from "@/types/personaAnalytics";
import { dismissKey } from "@/utils/personaRisk";
import { PersonaSignalCardBase } from "./PersonaSignalCardBase";

interface AtRiskPersonaCardProps {
  item: RiskSignalItem;
  currentWeekIso: string;
  /** Optional — used when the same persona also has a growth card. */
  crossReference?: string;
}

const DISMISS_STORAGE_KEY = "persona-insights-dismiss-v1";

/**
 * Read the dismiss-key list from localStorage. Returns `[]` on any failure.
 */
function readDismissedKeys(): string[] {
  try {
    const raw = localStorage.getItem(DISMISS_STORAGE_KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed.filter((k): k is string => typeof k === "string") : [];
  } catch {
    return [];
  }
}

function writeDismissedKeys(keys: string[]): void {
  try {
    localStorage.setItem(DISMISS_STORAGE_KEY, JSON.stringify(keys));
  } catch {
    // best-effort; quota errors swallowed.
  }
}

/**
 * 위험 페르소나 카드.
 * Spec §3 — CTA 는 위험 타입 × 지속 주차 에 따라 달라진다.
 */
export function AtRiskPersonaCard({
  item,
  currentWeekIso,
  crossReference,
}: AtRiskPersonaCardProps) {
  const navigate = useNavigate();
  const qc = useQueryClient();

  // 비활성화 mutation — PATCH /admin/personas/{id}/active (PR-A 에서 신설).
  const deactivateMutation = useMutation({
    mutationFn: () =>
      api
        .patch(`admin/personas/${encodeURIComponent(item.personaId)}/active`, {
          json: { isActive: false },
        })
        .json(),
    onSuccess: () => {
      toast.success(`"${item.personaName}" 페르소나를 비활성화했어요`);
      qc.invalidateQueries({ queryKey: personaAnalyticsKeys.all });
    },
    onError: (err) =>
      toast.error(
        userFriendlyMessage(err, "페르소나를 비활성화하지 못했어요"),
      ),
  });

  const handleEdit = () => navigate(`/admin/personas`);

  const handleViewSubscribers = () =>
    navigate(`/admin/user-accounts?personaId=${encodeURIComponent(item.personaId)}`);

  const handleViewRecentDeliveries = () =>
    navigate(`/admin/pipeline?personaId=${encodeURIComponent(item.personaId)}`);

  const handleDeactivate = () => {
    if (deactivateMutation.isPending) return;
    deactivateMutation.mutate();
  };

  const handleDismiss = () => {
    const key = dismissKey(item.personaId, item.riskType, currentWeekIso);
    const existing = readDismissedKeys();
    if (!existing.includes(key)) {
      writeDismissedKeys([...existing, key]);
    }
    toast.success("7일간 이 알림을 숨깁니다.");
  };

  const { primaryLine, secondaryLine } = buildLines(item);
  const ctas = buildCtas({
    item,
    onEdit: handleEdit,
    onViewSubscribers: handleViewSubscribers,
    onViewRecentDeliveries: handleViewRecentDeliveries,
    onDeactivate: handleDeactivate,
    onDismiss: handleDismiss,
    deactivating: deactivateMutation.isPending,
  });

  return (
    <PersonaSignalCardBase
      tone="risk"
      icon={<AlertTriangle className="h-5 w-5" aria-hidden />}
      personaName={item.personaName}
      isPreset={item.isPreset}
      typeLabel={RISK_LABELS[item.riskType]}
      tooltip={RISK_TOOLTIP[item.riskType]}
      persistentWeeks={item.persistentWeeks}
      primaryLine={primaryLine}
      secondaryLine={secondaryLine}
      ctas={ctas}
      crossReference={crossReference}
    />
  );
}

function buildLines(item: RiskSignalItem): {
  primaryLine: string;
  secondaryLine?: string;
} {
  const d = item.details;
  if (d.type === "CHURN_EXCESS") {
    return {
      primaryLine: `이탈 ${d.churnedSubs} · 신규 ${d.newSubs}`,
      secondaryLine: `활성 구독자 ${d.activeSubs}명`,
    };
  }
  if (d.type === "IDLE") {
    return {
      primaryLine: `${d.consecutiveWeeks}주 연속 발송 없음`,
      secondaryLine: `활성 구독자 ${d.activeSubs}명`,
    };
  }
  // ENGAGEMENT_DROP
  const prevPct = Math.round(d.prevEngagementRate * 100);
  const curPct = Math.round(d.engagementRate * 100);
  return {
    primaryLine: `${prevPct}% → ${curPct}% (${d.deltaPp}pp)`,
    secondaryLine: `발송 ${d.deliveredCount}건 · 클릭 ${d.totalClicks}건`,
  };
}

interface CtaBuilderArgs {
  item: RiskSignalItem;
  onEdit: () => void;
  onViewSubscribers: () => void;
  onViewRecentDeliveries: () => void;
  onDeactivate: () => void;
  onDismiss: () => void;
  deactivating: boolean;
}

function buildCtas(args: CtaBuilderArgs) {
  const {
    item,
    onEdit,
    onViewSubscribers,
    onViewRecentDeliveries,
    onDeactivate,
    onDismiss,
    deactivating,
  } = args;

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

  const dismissButton = (
    <Button key="dismiss" variant="ghost" size="sm" onClick={onDismiss}>
      알림 7일 끄기
    </Button>
  );

  if (item.riskType === "IDLE") {
    return (
      <>
        {editButton}
        <Button
          key="deactivate"
          variant="destructive"
          size="sm"
          onClick={onDeactivate}
          disabled={deactivating}
        >
          {deactivating ? "처리 중…" : "비활성화"}
        </Button>
        {dismissButton}
      </>
    );
  }

  const isPersistent = item.persistentWeeks >= 3;
  const secondaryAction =
    item.riskType === "CHURN_EXCESS" ? (
      <Button
        key="subscribers"
        variant="outline"
        size="sm"
        onClick={onViewSubscribers}
      >
        구독자 보기
      </Button>
    ) : (
      <Button
        key="deliveries"
        variant="outline"
        size="sm"
        onClick={onViewRecentDeliveries}
      >
        최근 발송 보기
      </Button>
    );

  if (isPersistent) {
    return (
      <>
        {editButton}
        {secondaryAction}
        <Button
          key="deactivate-suggest"
          variant="outline"
          size="sm"
          className="border-destructive text-destructive hover:bg-destructive/10"
          onClick={onDeactivate}
          disabled={deactivating}
        >
          {deactivating ? "처리 중…" : "비활성화 제안"}
        </Button>
      </>
    );
  }

  return (
    <>
      {editButton}
      {secondaryAction}
      {dismissButton}
    </>
  );
}
