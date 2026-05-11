import { AlertTriangle, AlertCircle } from "lucide-react";
import { Link } from "react-router-dom";

import { useActionRequiredData } from "../hooks/useActionRequiredData";
import type { ActionRequiredType } from "../model/dashboardState";

const TARGET_MAP: Record<ActionRequiredType, string> = {
  delivery_failed: "/admin/delivery?status=FAILED",
  pipeline_failed: "/admin/pipeline?status=FAILED",
  budget_alert: "/admin/cost",
};

function budgetLabel(level: string): string {
  return level === "CRITICAL_100"
    ? "월 LLM 예산 초과 · 요약 중단됨"
    : "월 LLM 예산 90%+ 도달";
}

export function ActionRequiredSection() {
  const { items, isLoading } = useActionRequiredData();

  if (isLoading) return null;
  if (items.length === 0) return null;

  return (
    <section
      data-testid="action-required-section"
      aria-live="polite"
      className="rounded-lg border border-destructive/20 bg-destructive/5 p-4 space-y-2"
    >
      <h2 className="text-sm font-semibold text-destructive flex items-center gap-2">
        <AlertTriangle className="h-4 w-4" /> 조치 필요
      </h2>
      <ul className="space-y-1">
        {items.map((item, idx) => (
          <li key={idx}>
            <Link
              to={TARGET_MAP[item.type]}
              className="flex items-center gap-2 text-sm hover:underline"
              aria-label={`${item.type} 관리 페이지로 이동`}
            >
              <AlertCircle
                className={`h-4 w-4 ${
                  item.severity === "danger"
                    ? "text-destructive"
                    : "text-[var(--status-warning-text)]"
                }`}
              />
              {item.type === "budget_alert"
                ? budgetLabel(item.budgetLevel ?? "")
                : item.type === "delivery_failed"
                  ? `발송 실패 ${item.count ?? 0}건`
                  : `파이프라인 실패 ${item.count ?? 0}건`}
              <span className="ml-auto text-xs">→</span>
            </Link>
          </li>
        ))}
      </ul>
    </section>
  );
}
