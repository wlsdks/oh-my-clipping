import { ActionRequiredSection } from "./components/ActionRequiredSection";
import { OperatorFooter } from "./components/OperatorFooter";
import { OpsMetricsSection } from "./components/OpsMetricsSection";
import { PendingTasksSection } from "./components/PendingTasksSection";
import { SystemStatusBadge } from "./components/SystemStatusBadge";

/**
 * 관리자 대시보드 메인 페이지.
 * 4-Tier Section 구조: ActionRequired → PendingTasks → OpsMetrics → OperatorFooter
 */
export function AdminDashboardPage() {
  const today = new Date().toLocaleDateString("ko-KR", {
    year: "numeric",
    month: "long",
    day: "numeric",
    weekday: "short",
  });

  return (
    <div className="p-4 sm:p-6 space-y-6">
      <header className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">홈</h1>
          <p className="text-sm text-muted-foreground">{today}</p>
        </div>
        <SystemStatusBadge />
      </header>
      <ActionRequiredSection />
      <PendingTasksSection />
      <OpsMetricsSection />
      <OperatorFooter />
    </div>
  );
}
