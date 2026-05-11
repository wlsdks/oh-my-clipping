import { BarChart, Bar, XAxis, YAxis, ResponsiveContainer, Tooltip, Cell } from "recharts";
import type { BarRectangleItem } from "recharts";

/* ── 이벤트 유형 -> 한글 레이블 매핑 ── */
const EVENT_LABELS: Record<string, string> = {
  PRODUCT_LAUNCH: "\u{1F680} \uC81C\uD488 \uCD9C\uC2DC",
  PARTNERSHIP: "\u{1F91D} \uC81C\uD734\u00B7\uD611\uB825",
  FUNDING: "\u{1F4B0} \uD22C\uC790\u00B7\uC778\uC218",
  POLICY: "\u{1F4CB} \uC815\uCC45\u00B7\uADDC\uC81C",
  PERSONNEL: "\u{1F464} \uC778\uC0AC",
  OTHER: "\u{1F4F0} \uAE30\uD0C0",
};

const PRIMARY_COLOR = "var(--accent-primary, #6366f1)";

interface AggregatedEventType {
  type: string;
  label: string;
  count: number;
}

/**
 * 기사 목록의 eventType을 집계하여 count 내림차순으로 정렬한다.
 * 알 수 없는 eventType은 "OTHER(기타)"로 분류하고,
 * null인 항목은 무시한다.
 */
export function aggregateEventTypes(articles: { eventType: string | null }[]): AggregatedEventType[] {
  const counts = new Map<string, number>();

  for (const article of articles) {
    if (article.eventType == null) continue;

    // 알 수 없는 유형은 OTHER로 통합
    const key = EVENT_LABELS[article.eventType] ? article.eventType : "OTHER";
    counts.set(key, (counts.get(key) ?? 0) + 1);
  }

  // count 내림차순 정렬
  return Array.from(counts.entries())
    .sort((a, b) => b[1] - a[1])
    .map(([type, count]) => ({
      type,
      label: EVENT_LABELS[type] ?? EVENT_LABELS.OTHER,
      count,
    }));
}

/* ── 커스텀 툴팁 ── */
interface TooltipPayloadItem {
  payload: AggregatedEventType;
  value: number;
}

function CustomTooltip({
  active,
  payload,
  total,
}: {
  active?: boolean;
  payload?: TooltipPayloadItem[];
  total: number;
}) {
  if (!active || !payload?.length) return null;
  const item = payload[0];
  const pct = total > 0 ? ((item.value / total) * 100).toFixed(1) : "0";
  return (
    <div className="bg-card border border-border rounded-xl px-3.5 py-2 text-[13px] text-foreground shadow-lg">
      {item.value}건 ({pct}%)
    </div>
  );
}

/* ── 메인 컴포넌트 ── */
interface EventTypeBarProps {
  articles: { eventType: string | null }[];
  onBarClick?: (eventType: string) => void;
}

export function EventTypeBar({ articles, onBarClick }: EventTypeBarProps) {
  const data = aggregateEventTypes(articles);
  const total = data.reduce((sum, d) => sum + d.count, 0);

  if (data.length === 0) {
    return (
      <div className="panel">
        <div className="panel-head">
          <h3>기사 유형 분포</h3>
        </div>
        <p className="text-muted-foreground text-center py-6">
          기사 유형 데이터가 없어요
        </p>
      </div>
    );
  }

  const chartHeight = data.length * 44 + 16;

  return (
    <div className="panel">
      <div className="panel-head">
        <h3>기사 유형 분포</h3>
      </div>
      <ResponsiveContainer width="100%" height={chartHeight}>
        <BarChart data={data} layout="vertical" margin={{ top: 4, right: 24, bottom: 4, left: 0 }}>
          <XAxis type="number" hide />
          <YAxis
            type="category"
            dataKey="label"
            width={110}
            axisLine={false}
            tickLine={false}
            tick={{ fontSize: 13, fill: "var(--text-secondary)" }}
          />
          <Tooltip content={<CustomTooltip total={total} />} cursor={{ fill: "rgba(0,0,0,0.04)" }} />
          <Bar
            dataKey="count"
            radius={[0, 6, 6, 0]}
            cursor="pointer"
            onClick={(entry: BarRectangleItem & { type?: string }) => {
              if (entry?.type) onBarClick?.(entry.type);
            }}
          >
            {data.map((_, i) => (
              <Cell key={i} fill={PRIMARY_COLOR} fillOpacity={1 - i * 0.12} />
            ))}
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}
