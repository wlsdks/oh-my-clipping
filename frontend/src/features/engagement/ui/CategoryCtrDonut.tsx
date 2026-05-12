import {
  PieChart,
  Pie,
  Cell,
  Tooltip,
  ResponsiveContainer,
  Legend,
} from "recharts";
import type { CategoryStatItem } from "@/services/analyticsService";
import { chartColor, TossTooltip } from "@/utils/chartTheme";

interface CategoryCtrDonutProps {
  stats: CategoryStatItem[];
  loading?: boolean;
}

export function CategoryCtrDonut({ stats, loading }: CategoryCtrDonutProps) {
  if (loading) {
    return (
      <div className="animate-pulse h-64 rounded-xl bg-muted/30 border border-border" />
    );
  }

  const donutData = stats
    .filter((c) => c.clicks > 0)
    .map((c) => ({ name: c.categoryName, value: c.clicks }));

  if (donutData.length === 0) {
    return (
      <div className="rounded-xl border border-border bg-card p-4">
        <h3 className="text-sm font-semibold mb-4">카테고리별 클릭 분포</h3>
        <p className="text-sm text-muted-foreground text-center py-8">
          클릭 데이터가 아직 없어요
        </p>
      </div>
    );
  }

  const totalClicks = donutData.reduce((sum, d) => sum + d.value, 0);

  return (
    <div className="rounded-xl border border-border bg-card p-4">
      <h3 className="text-sm font-semibold mb-4">카테고리별 클릭 분포</h3>
      <div className="h-64">
        <ResponsiveContainer width="100%" height="100%">
          <PieChart>
            <Pie
              data={donutData}
              dataKey="value"
              nameKey="name"
              cx="50%"
              cy="50%"
              innerRadius={60}
              outerRadius={90}
              paddingAngle={2}
              label={({ name, percent }) =>
                `${name} ${((percent ?? 0) * 100).toFixed(0)}%`
              }
            >
              {donutData.map((_, i) => (
                <Cell key={i} fill={chartColor(i)} />
              ))}
            </Pie>
            <Tooltip
              content={
                <TossTooltip
                  formatter={(v) => `${v.toLocaleString()}건`}
                />
              }
            />
            <Legend
              verticalAlign="bottom"
              iconType="circle"
              wrapperStyle={{ fontSize: 12, paddingTop: 12 }}
            />
          </PieChart>
        </ResponsiveContainer>
      </div>
      <p className="text-center text-xs text-muted-foreground mt-2">
        총 {totalClicks.toLocaleString()}건 클릭
      </p>
    </div>
  );
}
