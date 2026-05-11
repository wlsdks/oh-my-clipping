import { useState } from "react";
import { ChevronRight } from "lucide-react";
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  ResponsiveContainer,
  Tooltip,
} from "recharts";
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from "@/components/ui/collapsible";

// 한글 카테고리명 대부분이 10자 이하라 Y축 폭을 넉넉히 확보해 풀네임을 그대로 보이게 한다.
// 긴 이름(>16자)만 예외적으로 좌측 정렬로 초과분을 허용(오버플로우 허용) — 말줄임 금지.
const Y_AXIS_WIDTH = 180;
import type { Source } from "@/types/source";
import type { Category } from "@/types/category";

interface SourceDistributionChartProps {
  sources: Source[];
  categories: Category[];
}

interface ChartDatum {
  name: string;
  fullName: string;
  domestic: number;
  global: number;
  total: number;
}

/** 카테고리 x 지역별 소스 수를 집계한다. */
function aggregateDistribution(
  sources: Source[],
  categories: Category[],
): ChartDatum[] {
  const catMap = new Map(categories.map((c) => [c.id, c.name]));

  // 카테고리별 지역 카운트를 집계한다.
  const counts = new Map<string, { domestic: number; global: number }>();

  for (const cat of categories) {
    counts.set(cat.id, { domestic: 0, global: 0 });
  }

  for (const source of sources) {
    const entry = counts.get(source.categoryId);
    if (!entry) continue;
    if (source.sourceRegion === "DOMESTIC") {
      entry.domestic += 1;
    } else {
      // GLOBAL + UNKNOWN 모두 해외로 분류한다.
      entry.global += 1;
    }
  }

  return Array.from(counts.entries())
    .map(([catId, { domestic, global }]) => {
      const fullName = catMap.get(catId) ?? catId;
      return {
        name: fullName,
        fullName,
        domestic,
        global,
        total: domestic + global,
      };
    })
    .sort((a, b) => b.total - a.total);
}

/** 커스텀 툴팁 */
interface TooltipPayloadItem {
  name: string;
  value: number;
  color: string;
}

interface TooltipDatumPayload {
  payload?: ChartDatum;
}

function ChartTooltip({
  active,
  payload,
}: {
  active?: boolean;
  payload?: (TooltipPayloadItem & TooltipDatumPayload)[];
}) {
  if (!active || !payload?.length) return null;
  const fullName = payload[0]?.payload?.fullName ?? "";
  return (
    <div className="bg-card border border-border rounded-xl px-3.5 py-2 text-[13px] text-foreground shadow-lg space-y-0.5">
      <p className="font-medium">{fullName}</p>
      {payload.map((item) => (
        <p key={item.name} className="text-muted-foreground">
          <span
            className="inline-block w-2 h-2 rounded-full mr-1.5"
            style={{ backgroundColor: item.color }}
          />
          {item.name === "domestic" ? "국내" : "해외"} {item.value}개
        </p>
      ))}
    </div>
  );
}

export function SourceDistributionChart({
  sources,
  categories,
}: SourceDistributionChartProps) {
  const [open, setOpen] = useState(false);
  const data = aggregateDistribution(sources, categories);

  if (categories.length === 0) return null;

  const chartHeight = Math.max(data.length * 36 + 16, 80);

  return (
    <Collapsible open={open} onOpenChange={setOpen}>
      <CollapsibleTrigger asChild>
        <button
          type="button"
          className="w-full flex items-center gap-2 py-2 px-3 rounded-lg hover:bg-accent/30 transition-colors"
        >
          <ChevronRight
            size={16}
            className={`text-muted-foreground transition-transform ${open ? "rotate-90" : ""}`}
          />
          <span className="text-sm font-medium">소스 분포</span>
        </button>
      </CollapsibleTrigger>

      <CollapsibleContent className="mt-3">
        <div className="rounded-xl border bg-card p-4">
          <ResponsiveContainer width="100%" height={chartHeight}>
            <BarChart
              data={data}
              layout="vertical"
              margin={{ top: 4, right: 24, bottom: 4, left: 0 }}
            >
              <XAxis type="number" hide />
              <YAxis
                type="category"
                dataKey="name"
                width={Y_AXIS_WIDTH}
                axisLine={false}
                tickLine={false}
                tick={{ fontSize: 12, fill: "var(--muted-foreground)" }}
              />
              <Tooltip
                content={<ChartTooltip />}
                cursor={{ fill: "rgba(0,0,0,0.04)" }}
              />
              <Bar
                dataKey="domestic"
                stackId="region"
                fill="var(--chart-1)"
                radius={[0, 0, 0, 0]}
                name="domestic"
              />
              <Bar
                dataKey="global"
                stackId="region"
                fill="var(--chart-2)"
                radius={[0, 4, 4, 0]}
                name="global"
              />
            </BarChart>
          </ResponsiveContainer>

          {/* 범례 */}
          <div className="flex items-center gap-4 mt-2 ml-[180px] text-xs text-muted-foreground">
            <span className="flex items-center gap-1">
              <span
                className="inline-block w-2.5 h-2.5 rounded-sm"
                style={{ backgroundColor: "var(--chart-1)" }}
              />
              국내
            </span>
            <span className="flex items-center gap-1">
              <span
                className="inline-block w-2.5 h-2.5 rounded-sm"
                style={{ backgroundColor: "var(--chart-2)" }}
              />
              해외
            </span>
          </div>

          {/* 소스가 0인 카테고리 힌트 */}
          {data.some((d) => d.total === 0) && (
            <p className="text-xs text-muted-foreground mt-3 ml-[180px]">
              * 소스가 없는 주제가 있어요. 커버리지를 확인해 주세요.
            </p>
          )}
        </div>
      </CollapsibleContent>
    </Collapsible>
  );
}
