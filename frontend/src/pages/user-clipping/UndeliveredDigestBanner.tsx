import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { AlertTriangle, Clock, X } from "lucide-react";
import { userHistoryService } from "@/services/userHistoryService";
import { userHistoryKeys } from "@/queries/userHistoryKeys";
import type { UndeliveredDigest } from "@/types/insight";
import { UndeliveredDigestModal } from "./UndeliveredDigestModal";

/**
 * 미전달 다이제스트 배너 컴포넌트.
 * ABANDONED/STALE 또는 오래된 FAILED 상태 발송 건이 있을 때 사용자에게 알린다.
 */
export function UndeliveredDigestBanner() {
  const [dismissed, setDismissed] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);

  const { data: digests = [] } = useQuery({
    queryKey: userHistoryKeys.undelivered(),
    queryFn: () => userHistoryService.getUndeliveredDigests(),
    refetchInterval: 30_000,
    refetchIntervalInBackground: false,
    staleTime: 25_000,
  });

  if (dismissed || digests.length === 0) return null;

  const hasActionable = digests.some(
    (d: UndeliveredDigest) => d.status === "ABANDONED" || d.status === "STALE"
  );
  const isWarning = hasActionable;
  const count = digests.length;

  const message = isWarning
    ? count === 1
      ? `${digests[0].categoryName} 뉴스 요약이 아직 전달되지 않았어요`
      : `오늘 전달되지 않은 뉴스 요약이 ${count}건 있어요`
    : "발송 중이에요. 잠시 후 도착해요.";

  const bgClass = isWarning
    ? "bg-[var(--status-warning-bg)] text-[var(--status-warning-text)]"
    : "bg-[var(--status-neutral-bg)] text-[var(--status-neutral-text)]";

  const Icon = isWarning ? AlertTriangle : Clock;

  return (
    <>
      <div
        className={`flex items-center gap-3 rounded-lg px-4 py-3 text-sm ${bgClass}`}
        role="alert"
      >
        <Icon size={16} className="shrink-0" />
        <span className="flex-1">{message}</span>
        {isWarning && (
          <button
            type="button"
            className="shrink-0 underline underline-offset-2 font-medium hover:opacity-80 transition-opacity"
            onClick={() => setModalOpen(true)}
          >
            확인하기 →
          </button>
        )}
        <button
          type="button"
          aria-label="배너 닫기"
          className="shrink-0 hover:opacity-70 transition-opacity"
          onClick={() => setDismissed(true)}
        >
          <X size={14} />
        </button>
      </div>

      <UndeliveredDigestModal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        digests={digests}
      />
    </>
  );
}
