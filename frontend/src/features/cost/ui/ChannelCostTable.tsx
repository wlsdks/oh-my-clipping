import type { LlmCostRow } from "@/types/cost";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";

interface ChannelCostTableProps {
  rows: LlmCostRow[];
  loading?: boolean;
}

export function ChannelCostTable({ rows, loading }: ChannelCostTableProps) {
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
        비용 데이터가 없어요
      </p>
    );
  }

  return (
    <div className="rounded-md border border-border overflow-x-auto">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>채널</TableHead>
            <TableHead>카테고리</TableHead>
            <TableHead className="text-right">요청 수</TableHead>
            <TableHead className="text-right">AI 사용량(입력)</TableHead>
            <TableHead className="text-right">AI 사용량(출력)</TableHead>
            <TableHead className="text-right">추정비용(USD)</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {rows.map((row) => (
            <TableRow key={`${row.channelId}-${row.categoryId}`}>
              <TableCell className="font-mono text-sm">
                {row.channelId}
              </TableCell>
              <TableCell>{row.categoryName}</TableCell>
              <TableCell className="text-right">
                {row.requestCount.toLocaleString()}
              </TableCell>
              <TableCell className="text-right">
                {row.tokensIn.toLocaleString()}
              </TableCell>
              <TableCell className="text-right">
                {row.tokensOut.toLocaleString()}
              </TableCell>
              <TableCell className="text-right font-medium">
                ${row.estimatedUsd.toFixed(4)}
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  );
}
