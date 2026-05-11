import { Link } from "react-router-dom";

import { InfoTooltip } from "@/components/shared/InfoTooltip";

import { useOperatorFooterData } from "../hooks/useOperatorFooterData";

export function OperatorFooter() {
  const { activeSubscriptions, showGettingStarted, isLoading } = useOperatorFooterData();

  if (isLoading) return <div className="h-12 rounded bg-muted animate-pulse" />;

  return (
    <section
      data-testid="operator-footer"
      className="space-y-2 text-sm text-muted-foreground"
    >
      {activeSubscriptions && (
        <div className="flex items-center gap-2">
          <Link to="/admin/subscriptions" className="hover:underline">
            <span className="font-medium">
              활성 구독 {activeSubscriptions.activeCount}개
            </span>
          </Link>
          <span>
            · 이번 주 ↑{activeSubscriptions.newThisWeek} ↓
            {activeSubscriptions.deactivatedThisWeek} · 순{" "}
            {activeSubscriptions.netChange >= 0 ? "+" : ""}
            {activeSubscriptions.netChange}
          </span>
          <InfoTooltip content="이탈은 명시적 해지 + 회원 탈퇴만 포함. 관리자 임시 비활성화나 활동 없음 기반 이탈은 포함되지 않습니다." />
        </div>
      )}
      {showGettingStarted && (
        <Link
          to="/admin/getting-started"
          className="inline-block px-3 py-1 rounded bg-primary/10 text-primary hover:bg-primary/20"
        >
          🚀 시작하기 — 기본 설정 완료하기
        </Link>
      )}
    </section>
  );
}
