import { PieChart, Pie, Cell, ResponsiveContainer, Tooltip } from "recharts";
import { SENTIMENT_COLORS } from "@/utils/chartTheme";

/* ── 타입 ── */

export interface SentimentSummary {
  positive: number;
  neutral: number;
  negative: number;
  total: number;
  positiveRate: number;
  neutralRate: number;
  negativeRate: number;
}

interface DonutItem {
  name: string;
  value: number;
  fill: string;
}

interface SentimentDonutProps {
  data: SentimentSummary;
  onSliceClick?: (sentiment: string) => void;
}

/* ── 상수 ── */

const SENTIMENT_MAP: { key: keyof SentimentSummary; label: string; apiValue: string; fill: string }[] = [
  { key: "positive", label: "\uAE0D\uC815", apiValue: "POSITIVE", fill: SENTIMENT_COLORS.positive },
  { key: "neutral", label: "\uC911\uB9BD", apiValue: "NEUTRAL", fill: SENTIMENT_COLORS.neutral },
  { key: "negative", label: "\uBD80\uC815", apiValue: "NEGATIVE", fill: SENTIMENT_COLORS.negative },
];

/* ── 헬퍼 ── */

/** 논조 데이터를 도넛 차트용 배열로 변환한다. total=0이면 빈 배열 반환. */
export function buildDonutData(sentiment: SentimentSummary): DonutItem[] {
  if (sentiment.total === 0) return [];
  return SENTIMENT_MAP.map(({ key, label, fill }) => ({
    name: label,
    value: sentiment[key] as number,
    fill,
  }));
}

/* ── 커스텀 툴팁 ── */

function DonutTooltip({ active, payload }: { active?: boolean; payload?: Array<{ name: string; value: number }> }) {
  if (!active || !payload?.length) return null;
  const { name, value } = payload[0];
  return (
    <div className="bg-card rounded-xl shadow-lg border-none px-3.5 py-2.5 text-[13px]">
      {name} {value}건
    </div>
  );
}

/* ── 컴포넌트 ── */

export function SentimentDonut({ data, onSliceClick }: SentimentDonutProps) {
  const items = buildDonutData(data);

  if (items.length === 0) {
    return (
      <div className="panel">
        <div className="panel-head">
          <h3>논조 분포</h3>
        </div>
        <p className="text-sm text-muted-foreground text-center py-8">
          논조 데이터가 없어요
        </p>
      </div>
    );
  }

  return (
    <div className="panel">
      <div className="panel-head">
        <h3>논조 분포</h3>
      </div>
      <div className="flex items-center gap-6">
        {/* 도넛 차트 */}
        <div className="w-40 h-40 shrink-0">
          <ResponsiveContainer width="100%" height="100%" aria-label="감성 분포 도넛 차트">
            <PieChart>
              <Pie
                data={items}
                dataKey="value"
                nameKey="name"
                cx="50%"
                cy="50%"
                innerRadius={45}
                outerRadius={70}
                paddingAngle={2}
                cursor="pointer"
                onClick={(_entry, index) => {
                  if (onSliceClick) {
                    onSliceClick(SENTIMENT_MAP[index].apiValue);
                  }
                }}
              >
                {items.map((item) => (
                  <Cell key={item.name} fill={item.fill} stroke="none" />
                ))}
              </Pie>
              <Tooltip content={<DonutTooltip />} />
            </PieChart>
          </ResponsiveContainer>
        </div>

        {/* 범례 */}
        <div className="flex flex-col gap-2.5">
          {SENTIMENT_MAP.map(({ key, label, fill }) => {
            const value = data[key] as number;
            const rateKey = `${key}Rate` as keyof SentimentSummary;
            const rate = data[rateKey] as number;
            return (
              <div key={label} className="flex items-center gap-2 text-[13px]">
                <span
                  className="w-2.5 h-2.5 rounded-full shrink-0"
                  style={{ background: fill }}
                />
                <span className="text-foreground">
                  {label} <strong>{value}건</strong>{" "}
                  <span className="text-muted-foreground">({Math.round(rate)}%)</span>
                </span>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}
