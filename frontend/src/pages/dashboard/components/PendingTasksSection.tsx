import { CheckCircle2, ChevronRight } from "lucide-react";
import { Link } from "react-router-dom";

import { usePendingTasksData } from "../hooks/usePendingTasksData";

const ITEMS = [
  {
    key: "userAccounts" as const,
    label: "가입 승인",
    href: "/admin/user-accounts?tab=approval",
  },
  {
    key: "clippingRequests" as const,
    label: "구독 요청",
    href: "/admin/subscriptions?tab=requests",
  },
  {
    key: "reviewItems" as const,
    label: "뉴스 검토",
    href: "/admin/review-queue?status=REVIEW",
  },
];

export function PendingTasksSection() {
  const data = usePendingTasksData();
  const totalCount =
    data.userAccounts.count + data.clippingRequests.count + data.reviewItems.count;

  return (
    <section
      data-testid="pending-tasks-section"
      className="rounded-lg border bg-card p-4 space-y-3"
    >
      <h2 className="text-sm font-semibold">📋 오늘의 대기 업무</h2>
      {totalCount === 0 ? (
        <p className="flex items-center gap-2 text-sm text-[var(--status-success-text)]">
          <CheckCircle2 className="h-4 w-4" /> 오늘 대기 없음 — 어제 처리 완료
        </p>
      ) : (
        <ul className="space-y-2">
          {ITEMS.map((item) => {
            const task = data[item.key];
            return (
              <li key={item.key}>
                <Link
                  to={item.href}
                  className="flex items-center gap-3 text-sm hover:underline"
                  aria-label={`${item.label} ${task.count}건 처리 페이지로 이동`}
                >
                  <span className="font-medium">{item.label}</span>
                  <span
                    className={
                      task.count === 0
                        ? "text-muted-foreground"
                        : "text-foreground font-semibold"
                    }
                  >
                    {task.count}건
                  </span>
                  {task.urgencyPreview && (
                    <span className="text-xs text-muted-foreground">
                      · {task.urgencyPreview}
                    </span>
                  )}
                  <ChevronRight className="ml-auto h-4 w-4 text-muted-foreground" />
                </Link>
              </li>
            );
          })}
        </ul>
      )}
    </section>
  );
}
