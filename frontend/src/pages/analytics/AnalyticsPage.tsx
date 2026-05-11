import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { useTabSync } from "@/hooks/useTabSync";
import {
  Tabs,
  TabsContent,
  TabsList,
  TabsTrigger,
} from "@/components/ui/tabs";
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

import { OverviewTab } from "./OverviewTab";
import { ContentQualityTab } from "./ContentQualityTab";
import { ContentInsightTab } from "./ContentInsightTab";
import { PersonaInsightsTab } from "./PersonaInsightsTab";

const PERIODS: { key: PeriodKey; label: string }[] = [
  { key: "this-week", label: "이번 주" },
  { key: "last-week", label: "지난 주" },
  { key: "this-month", label: "이번 달" },
  { key: "last-month", label: "지난 달" }
];

const ALL_CATEGORIES_VALUE = "__all__";
const VALID_TABS = ["overview", "quality", "insight", "personas"] as const;

export function AnalyticsPage() {
  const [activeTab, setTab] = useTabSync("overview", VALID_TABS);

  // 글로벌 필터
  const [categoryId, setCategoryId] = useState<string | undefined>();
  const [period, setPeriod] = useState<PeriodKey>("this-month");

  const { from, to } = getPeriodRange(period);
  const days = periodToDays(period);

  // 카테고리 목록
  const { data: categories } = useQuery({
    queryKey: categoryKeys.lists(),
    queryFn: () => categoryService.getAll(),
  });

  // 카테고리 선택 핸들러
  const handleCategoryChange = (value: string) => {
    setCategoryId(value === ALL_CATEGORIES_VALUE ? undefined : value);
  };

  const tabProps = { categoryId, from, to, days };

  return (
    <div className="p-4 sm:p-6 space-y-5">
      {/* 헤더 */}
      <div className="flex items-start justify-between gap-4 flex-wrap">
        <div>
          <h1 className="text-2xl font-bold">통합 분석</h1>
          <p className="text-sm text-muted-foreground mt-1">
            서비스 현황, 콘텐츠 품질, 콘텐츠 인사이트를 한눈에 확인합니다
          </p>
        </div>

        {/* 필터 영역 */}
        <div className="flex items-center gap-3 flex-wrap">
          {/* 카테고리 필터 */}
          <Select
            value={categoryId ?? ALL_CATEGORIES_VALUE}
            onValueChange={handleCategoryChange}
          >
            <SelectTrigger className="w-40">
              <SelectValue placeholder="전체 카테고리" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={ALL_CATEGORIES_VALUE}>
                전체 카테고리
              </SelectItem>
              {categories?.map((cat) => (
                <SelectItem key={cat.id} value={cat.id}>
                  {cat.name}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>

          {/* 기간 필터 */}
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

      {/* 탭 */}
      <Tabs value={activeTab} onValueChange={(v) => setTab(v as typeof activeTab)}>
        <div className="overflow-x-auto">
          <TabsList>
            <TabsTrigger value="overview">전체 현황</TabsTrigger>
            <TabsTrigger value="quality">콘텐츠 품질</TabsTrigger>
            <TabsTrigger value="insight">콘텐츠 인사이트</TabsTrigger>
            <TabsTrigger value="personas">페르소나 인사이트</TabsTrigger>
          </TabsList>
        </div>

        <TabsContent value="overview" className="mt-4">
          <OverviewTab {...tabProps} />
        </TabsContent>

        <TabsContent value="quality" className="mt-4">
          <ContentQualityTab {...tabProps} />
        </TabsContent>

        <TabsContent value="insight" className="mt-4">
          <ContentInsightTab {...tabProps} />
        </TabsContent>

        <TabsContent value="personas" className="mt-4">
          <PersonaInsightsTab />
        </TabsContent>
      </Tabs>
    </div>
  );
}
