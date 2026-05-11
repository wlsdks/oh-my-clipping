import { TrendingUp, TrendingDown, Minus, ThumbsUp, ThumbsDown } from "lucide-react";
import { Link } from "react-router-dom";

import { Card, CardHeader, CardTitle, CardContent } from "@/components/ui/card";

import { computeClickRateTrend } from "../model/dashboardState";

interface Props {
  yesterdayClickRate: number;
  sevenDayAvg: number;
  stdDev: number;
  positive: number;
  negative: number;
  href?: string;
}

export function UserEngagementCard({
  yesterdayClickRate,
  sevenDayAvg,
  stdDev,
  positive,
  negative,
  href = "/admin/engagement",
}: Props) {
  const trend = computeClickRateTrend(yesterdayClickRate, sevenDayAvg, stdDev);
  const TrendIcon =
    trend.direction === "up"
      ? TrendingUp
      : trend.direction === "down"
        ? TrendingDown
        : Minus;
  const trendColor =
    trend.direction === "up"
      ? "text-[var(--status-success-text)]"
      : trend.direction === "down"
        ? "text-[var(--status-warning-text)]"
        : "text-muted-foreground";
  const sign = trend.deltaPct >= 0 ? "+" : "";

  return (
    <Link
      to={href}
      className="block"
      aria-label={`사용자 반응 카드. 어제 클릭률 ${yesterdayClickRate.toFixed(0)}%, 7일 평균 ${sevenDayAvg.toFixed(0)}%. 클릭하면 사용자 반응 페이지로 이동`}
    >
      <Card className="hover:shadow-md transition-shadow h-full">
        <CardHeader className="pb-2">
          <CardTitle className="text-sm text-muted-foreground">사용자 반응</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="text-3xl font-bold">{yesterdayClickRate.toFixed(0)}%</div>
          <div
            className={`text-sm flex items-center gap-1 ${trendColor}`}
            data-testid={`trend-${trend.direction}`}
          >
            <TrendIcon className="h-4 w-4" />
            <span>
              7일 평균 {sevenDayAvg.toFixed(0)}% ({sign}
              {trend.deltaPct.toFixed(1)}%p)
            </span>
          </div>
          <div className="flex gap-3 text-xs text-muted-foreground mt-2">
            <span className="inline-flex items-center gap-1">
              <ThumbsUp className="h-3 w-3" /> {positive}
            </span>
            <span className="inline-flex items-center gap-1">
              <ThumbsDown className="h-3 w-3" /> {negative}
            </span>
          </div>
        </CardContent>
      </Card>
    </Link>
  );
}
