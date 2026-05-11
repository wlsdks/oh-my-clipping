import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  Cell,
} from "recharts";
import { chartColor, GRID_PROPS, AXIS_PROPS } from "@/utils/chartTheme";
import type { TableSizeEntry } from "@/types/dbMetrics";

interface TopTablesChartProps {
  tables: TableSizeEntry[];
}

/**
 * 테이블 크기를 MB 단위로 포맷한다. 1 MB 미만은 KB로 표시.
 */
function formatBytes(bytes: number): string {
  if (bytes >= 1_048_576) return `${(bytes / 1_048_576).toFixed(1)} MB`;
  return `${(bytes / 1024).toFixed(0)} KB`;
}

/**
 * recharts YAxis tickFormatter 용 — 축에는 짧게 표시.
 */
function bytesTickFormatter(bytes: number): string {
  if (bytes >= 1_048_576) return `${(bytes / 1_048_576).toFixed(0)}MB`;
  if (bytes >= 1024) return `${(bytes / 1024).toFixed(0)}KB`;
  return `${bytes}B`;
}

interface CustomBarLabelProps {
  x?: number;
  y?: number;
  width?: number;
  height?: number;
  value?: number;
  entry?: TableSizeEntry;
}

/**
 * 바 왼쪽에 테이블명 전체를 표시하는 커스텀 라벨 — 말줄임 없음 (§8.3 규칙 9).
 * recharts 는 label을 SVG로 렌더링하므로 foreignObject 대신 text 엘리먼트를 사용한다.
 */
function FullNameLabel({ x = 0, y = 0, height = 0, entry }: CustomBarLabelProps) {
  if (!entry) return null;
  return (
    <text
      x={x - 8}
      y={y + height / 2 + 4}
      textAnchor="end"
      fontSize={11}
      fill="var(--muted-foreground)"
    >
      {entry.table}
    </text>
  );
}

interface TooltipPayloadEntry {
  payload?: TableSizeEntry;
  value?: number;
}

interface TableTooltipProps {
  active?: boolean;
  payload?: TooltipPayloadEntry[];
}

function TableTooltip({ active, payload }: TableTooltipProps) {
  if (!active || !payload?.length) return null;
  const entry = payload[0].payload;
  const bytes = payload[0].value ?? 0;
  if (!entry) return null;
  return (
    <div className="rounded-xl border bg-card px-3 py-2 shadow-lg text-sm">
      <p className="font-medium text-foreground mb-1">{entry.table}</p>
      <p className="text-muted-foreground">크기: <span className="font-semibold text-foreground">{formatBytes(bytes)}</span></p>
      <p className="text-muted-foreground">행 수: <span className="font-semibold text-foreground">{entry.rows.toLocaleString()}</span></p>
      <p className="text-muted-foreground">DB 점유율: <span className="font-semibold text-foreground">{entry.pctOfDb.toFixed(1)}%</span></p>
    </div>
  );
}

export function TopTablesChart({ tables }: TopTablesChartProps) {
  if (tables.length === 0) {
    return (
      <p className="text-sm text-muted-foreground text-center py-8">데이터 수집 중이에요</p>
    );
  }

  // 작은 값이 위로 오도록 역순 정렬 (recharts 가로 바 차트는 아래서 위로 쌓임)
  const sorted = [...tables].sort((a, b) => a.bytes - b.bytes);

  // 테이블명 최대 길이 기반으로 왼쪽 여백 계산 — 말줄임 없음
  const maxLabelLen = Math.max(...sorted.map((t) => t.table.length));
  const leftMargin = Math.min(Math.max(maxLabelLen * 6.5, 120), 260);

  // 항목당 40px + 위아래 여백
  const chartHeight = sorted.length * 40 + 40;

  return (
    <ResponsiveContainer width="100%" height={chartHeight}>
      <BarChart
        data={sorted}
        layout="vertical"
        margin={{ top: 10, right: 60, left: leftMargin, bottom: 10 }}
      >
        <CartesianGrid {...GRID_PROPS} horizontal={false} />
        <XAxis
          type="number"
          {...AXIS_PROPS}
          tickFormatter={bytesTickFormatter}
          tick={{ fontSize: 11 }}
        />
        <YAxis
          type="category"
          dataKey="table"
          width={0}
          tick={false}
          axisLine={false}
          tickLine={false}
        />
        <Tooltip content={<TableTooltip />} />
        <Bar dataKey="bytes" radius={[0, 4, 4, 0]} isAnimationActive={false} label={<FullNameLabel />}>
          {sorted.map((entry, index) => (
            <Cell key={entry.table} fill={chartColor(index)} />
          ))}
        </Bar>
      </BarChart>
    </ResponsiveContainer>
  );
}
