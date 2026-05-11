import { LineChart, Line, ResponsiveContainer, Tooltip } from "recharts";

interface Props {
  data: Array<{ date: string; value: number }>;
  height?: number;
}

export function CostSparkline({ data, height = 40 }: Props) {
  if (data.length === 0) {
    return <div className="text-xs text-muted-foreground">데이터 없음</div>;
  }
  return (
    <ResponsiveContainer width="100%" height={height}>
      <LineChart data={data}>
        <Line
          type="monotone"
          dataKey="value"
          stroke="var(--color-primary)"
          strokeWidth={1.5}
          dot={false}
        />
        <Tooltip
          formatter={(value) => [`$${Number(value).toFixed(2)}`, "비용"]}
          labelFormatter={(date) => String(date)}
          contentStyle={{ fontSize: "0.75rem", padding: "4px 8px" }}
        />
      </LineChart>
    </ResponsiveContainer>
  );
}
