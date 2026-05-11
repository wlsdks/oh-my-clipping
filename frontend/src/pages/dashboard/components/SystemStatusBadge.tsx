import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { ArrowRight } from "lucide-react";

import { cn } from "@/utils/cn";
import { systemStatusKeys } from "@/queries/systemStatusKeys";
import { systemStatusService } from "@/services/systemStatusService";
import { computeSystemHealth } from "@/utils/systemHealth";

/**
 * 페이지 헤더 우측에 표시되는 한 줄 시스템 상태 배지.
 * 정상일 때는 초록 점 + 짧은 텍스트, 이상일 때는 빨간 점 + 상세 배너와 링크를 렌더링한다.
 * 자체적으로 시스템 상태를 조회한다(60초 갱신).
 */
export function SystemStatusBadge() {
  const { data: status } = useQuery({
    queryKey: systemStatusKeys.status(),
    queryFn: () => systemStatusService.getStatus(),
    staleTime: 60_000,
    refetchInterval: 60_000,
  });

  const health = computeSystemHealth(status);
  const isOk = health.ok;

  const dotClass = cn(
    "inline-block h-2 w-2 rounded-full",
    isOk ? "bg-[var(--status-success-text)]" : "bg-[var(--status-danger-text)] animate-pulse",
  );

  const content = (
    <>
      <span className={dotClass} role="img" aria-label={isOk ? "정상" : "이상"} />
      <span
        className={cn(
          "text-sm font-semibold",
          isOk ? "text-foreground" : "text-[var(--status-danger-text)]",
        )}
      >
        {health.message}
      </span>
      {!isOk && <ArrowRight size={14} className="text-[var(--status-danger-text)]" aria-hidden="true" />}
    </>
  );

  if (isOk) {
    return (
      <div
        className="flex items-center gap-2"
        role="status"
        aria-live="polite"
      >
        {content}
      </div>
    );
  }

  return (
    <Link
      to="/admin/system-status"
      role="status"
      aria-live="polite"
      className={cn(
        "flex items-center gap-2 rounded-full border border-[var(--status-danger-text)] bg-[var(--status-danger-bg)] px-3 py-1.5",
        "transition-colors hover:brightness-95",
        "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring",
      )}
    >
      {content}
    </Link>
  );
}
