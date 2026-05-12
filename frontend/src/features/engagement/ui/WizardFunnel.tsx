import { useQuery } from "@tanstack/react-query";
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  Tooltip,
  ResponsiveContainer,
  Cell,
  LabelList,
} from "recharts";
import { analyticsKeys } from "@/queries/analyticsKeys";
import { analyticsService } from "@/services/analyticsService";
import { Badge } from "@/components/ui/badge";
import { EmptyState } from "@/components/shared/EmptyState";

const STEP_LABELS: Record<string, string> = {
  start: "시작",
  select_topic: "주제 선택",
  select_keywords: "키워드 선택",
  select_persona: "페르소나 선택",
  select_schedule: "발송 설정",
  confirm: "확인/제출",
  complete: "완료",
};

function stepLabel(step: string): string {
  return STEP_LABELS[step] ?? step;
}

const DROP_RATE_HIGH = 30;
const DROP_RATE_MID = 15;

function barColor(dropRate: number): string {
  if (dropRate >= DROP_RATE_HIGH) return "hsl(var(--destructive))";
  if (dropRate >= DROP_RATE_MID) return "hsl(var(--muted-foreground))";
  return "hsl(var(--primary))";
}

interface WizardFunnelProps {
  days: number;
}

export function WizardFunnel({ days }: WizardFunnelProps) {
  const { data, isLoading, error } = useQuery({
    queryKey: analyticsKeys.funnel(days),
    queryFn: () => analyticsService.getWizardFunnel(days),
  });

  const rows = data?.data ?? [];

  if (isLoading) {
    return (
      <p className="text-sm text-muted-foreground py-4">불러오는 중...</p>
    );
  }

  if (error) {
    return (
      <p className="text-sm text-destructive py-4">
        퍼널 데이터를 불러오지 못했어요
      </p>
    );
  }

  if (rows.length === 0) {
    return (
      <EmptyState
        title="퍼널 데이터가 아직 없어요"
        description="사용자가 위자드를 이용하면 단계별 전환율이 자동으로 분석돼요."
      />
    );
  }

  const chartData = rows.map((r) => ({
    name: stepLabel(r.step),
    enters: r.enters,
    dropRate: r.dropRate,
  }));

  return (
    <div className="space-y-6">
      <div className="h-80">
        <ResponsiveContainer width="100%" height="100%">
          <BarChart
            data={chartData}
            layout="vertical"
            margin={{ left: 20, right: 60 }}
          >
            <XAxis type="number" tick={{ fontSize: 12 }} />
            <YAxis
              type="category"
              dataKey="name"
              tick={{ fontSize: 12 }}
              width={100}
            />
            <Tooltip
              contentStyle={{ fontSize: 12, borderRadius: 8 }}
              formatter={(value, name) => {
                if (name === "enters") return [Number(value), "진입 수"];
                return [Number(value), String(name)];
              }}
            />
            <Bar dataKey="enters" radius={[0, 6, 6, 0]}>
              {chartData.map((entry, index) => (
                <Cell key={index} fill={barColor(entry.dropRate)} />
              ))}
              <LabelList
                dataKey="dropRate"
                position="right"
                formatter={(val: unknown) =>
                  `${Number(val).toFixed(1)}% 이탈`
                }
                style={{ fontSize: 11, fill: "hsl(var(--muted-foreground))" }}
              />
            </Bar>
          </BarChart>
        </ResponsiveContainer>
      </div>

      <div className="rounded-md border border-border overflow-x-auto">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b bg-muted/50">
              <th scope="col" className="text-left px-4 py-2 font-medium">단계</th>
              <th scope="col" className="text-right px-4 py-2 font-medium">진입</th>
              <th scope="col" className="text-right px-4 py-2 font-medium">완료</th>
              <th scope="col" className="text-right px-4 py-2 font-medium">이탈률</th>
            </tr>
          </thead>
          <tbody className="divide-y">
            {rows.map((r) => (
              <tr key={r.step}>
                <td className="px-4 py-2">{stepLabel(r.step)}</td>
                <td className="px-4 py-2 text-right">
                  {r.enters.toLocaleString()}
                </td>
                <td className="px-4 py-2 text-right">
                  {r.completes.toLocaleString()}
                </td>
                <td className="px-4 py-2 text-right">
                  <Badge
                    variant={
                      r.dropRate >= DROP_RATE_HIGH
                        ? "destructive"
                        : r.dropRate >= DROP_RATE_MID
                          ? "secondary"
                          : "default"
                    }
                  >
                    {r.dropRate.toFixed(1)}%
                  </Badge>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
