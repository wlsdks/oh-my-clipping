import { Link } from "react-router-dom";
import { ArrowRight, CheckCircle2 } from "lucide-react";

import { Card, CardContent, CardHeader } from "@/components/ui/card";
import { cn } from "@/utils/cn";

import type { ActionItem } from "../model/dashboardState";

interface ActionItemsCardProps {
  items: ActionItem[];
}

const SEVERITY_DOT: Record<ActionItem["severity"], string> = {
  danger: "bg-[var(--status-danger-text)]",
  warning: "bg-[var(--status-warning-text)]",
  info: "bg-[var(--status-neutral-text)]",
};

const SEVERITY_LABEL: Record<ActionItem["severity"], string> = {
  danger: "긴급",
  warning: "주의",
  info: "정보",
};

/**
 * 대시보드 최상단 "지금 확인이 필요해요" 카드.
 * 빈 리스트일 때는 잔잔한 성공 상태를 보여준다.
 */
export function ActionItemsCard({ items }: ActionItemsCardProps) {
  const isEmpty = items.length === 0;

  return (
    <Card>
      <CardHeader>
        <h2 className="text-base font-semibold tracking-tight leading-none">
          지금 확인이 필요해요
        </h2>
      </CardHeader>
      <CardContent>
        {isEmpty ? (
          <div className="flex items-center gap-2 text-sm text-muted-foreground">
            <CheckCircle2 className="h-4 w-4 text-[var(--status-success-text)]" aria-hidden="true" />
            <span>모든 항목이 정상이에요</span>
          </div>
        ) : (
          <ul className="divide-y divide-border rounded-[var(--radius-card)] border border-border">
            {items.map((item) => (
              <li key={item.id}>
                <Link
                  to={item.href}
                  className={cn(
                    "flex items-center justify-between gap-3 px-4 py-3",
                    "transition-colors hover:bg-accent",
                    "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-inset",
                  )}
                >
                  <div className="flex items-center gap-3 min-w-0">
                    <span
                      className={cn("inline-block h-2 w-2 rounded-full shrink-0", SEVERITY_DOT[item.severity])}
                      role="img"
                      aria-label={SEVERITY_LABEL[item.severity]}
                    />
                    <span className="text-sm text-foreground truncate">{item.label}</span>
                  </div>
                  <div className="flex items-center gap-2 shrink-0">
                    <span className="text-sm font-semibold tabular-nums text-foreground">
                      {item.count.toLocaleString()}건
                    </span>
                    <ArrowRight size={14} className="text-muted-foreground" aria-hidden="true" />
                  </div>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </CardContent>
    </Card>
  );
}
