import type { DailyOperationalKpiRow } from "@/types/insight";

interface DailyKpiTableProps {
  rows: DailyOperationalKpiRow[];
  loading?: boolean;
}

export function DailyKpiTable({ rows, loading }: DailyKpiTableProps) {
  if (loading) {
    return (
      <div className="space-y-2">
        {[1, 2, 3].map((i) => (
          <div
            key={i}
            className="animate-pulse h-10 rounded bg-muted/30 border border-border"
          />
        ))}
      </div>
    );
  }

  if (rows.length === 0) {
    return (
      <p className="text-sm text-muted-foreground text-center py-8">
        일 단위 KPI 데이터가 없어요
      </p>
    );
  }

  return (
    <div className="overflow-x-auto rounded-md border border-border">
      <table className="w-full text-sm">
        <thead className="border-b bg-muted/50">
          <tr>
            <th scope="col" className="px-4 py-3 text-left font-medium">날짜</th>
            <th scope="col" className="px-4 py-3 text-right font-medium">수집</th>
            <th scope="col" className="px-4 py-3 text-right font-medium">노이즈율</th>
            <th scope="col" className="px-4 py-3 text-right font-medium">중복율</th>
            <th scope="col" className="px-4 py-3 text-right font-medium">리드타임</th>
            <th scope="col" className="px-4 py-3 text-right font-medium">발송 성공률</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr
              key={`${row.statDate}-${row.categoryId ?? "all"}`}
              className="border-b last:border-0"
            >
              <td className="px-4 py-3 text-muted-foreground">
                {row.statDate}
              </td>
              <td className="px-4 py-3 text-right tabular-nums">
                {row.itemsCollected}
              </td>
              <td className="px-4 py-3 text-right tabular-nums">
                {(row.noiseRate * 100).toFixed(1)}%
              </td>
              <td className="px-4 py-3 text-right tabular-nums">
                {(row.duplicateRate * 100).toFixed(1)}%
              </td>
              <td className="px-4 py-3 text-right tabular-nums">
                {row.reviewLeadTimeHours.toFixed(1)}h
              </td>
              <td className="px-4 py-3 text-right tabular-nums">
                {(row.sendSuccessRate * 100).toFixed(1)}%
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
