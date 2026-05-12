import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Button } from "@/components/ui/button";
import { categoryKeys } from "@/queries/categoryKeys";
import { categoryService } from "@/services/categoryService";
import type { PeriodKey } from "@/utils/periodUtils";
import { getPeriodRange, periodToDays } from "@/utils/periodUtils";
import { CostTab } from "@/features/cost/CostTab";

const PERIODS: { key: PeriodKey; label: string }[] = [
  { key: "this-week", label: "이번 주" },
  { key: "last-week", label: "지난 주" },
  { key: "this-month", label: "이번 달" },
  { key: "last-month", label: "지난 달" },
];

const ALL_CATEGORIES_VALUE = "__all__";

/**
 * 비용 관리 독립 페이지.
 * LLM 비용 현황과 예산 사용률을 한눈에 확인한다.
 */
export function CostPage() {
  const [categoryId, setCategoryId] = useState<string | undefined>();
  const [period, setPeriod] = useState<PeriodKey>("this-month");

  const { from, to } = getPeriodRange(period);
  const days = periodToDays(period);

  const { data: categories } = useQuery({
    queryKey: categoryKeys.lists(),
    queryFn: () => categoryService.getAll(),
  });

  const handleCategoryChange = (value: string) => {
    setCategoryId(value === ALL_CATEGORIES_VALUE ? undefined : value);
  };

  return (
    <div className="p-4 sm:p-6 space-y-5">
      {/* 헤더 */}
      <div className="flex items-start justify-between gap-4 flex-wrap">
        <div>
          <h1 className="text-2xl font-bold">비용 관리</h1>
          <p className="text-sm text-muted-foreground mt-1">
            LLM 비용 현황과 예산 사용률을 확인합니다
          </p>
        </div>

        {/* 필터 영역 */}
        <div className="flex items-center gap-3 flex-wrap">
          <Select
            value={categoryId ?? ALL_CATEGORIES_VALUE}
            onValueChange={handleCategoryChange}
          >
            <SelectTrigger className="w-40">
              <SelectValue placeholder="전체 카테고리" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={ALL_CATEGORIES_VALUE}>전체 카테고리</SelectItem>
              {categories?.map((cat) => (
                <SelectItem key={cat.id} value={cat.id}>
                  {cat.name}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>

          <div className="flex items-center gap-1">
            {PERIODS.map((p) => (
              <Button
                key={p.key}
                variant={period === p.key ? "default" : "outline"}
                size="sm"
                onClick={() => setPeriod(p.key)}
              >
                {p.label}
              </Button>
            ))}
          </div>
        </div>
      </div>

      {/* 콘텐츠 */}
      <CostTab categoryId={categoryId} from={from} to={to} days={days} />
    </div>
  );
}
