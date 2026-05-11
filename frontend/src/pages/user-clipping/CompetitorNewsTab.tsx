import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { newsReportKeys } from "@/queries/newsReportKeys";
import { userIntelligenceService } from "@/services/userIntelligenceService";
import type { CompetitorTimelineItem } from "@/types/newsReport";
import { getCompetitorPeriodOptions, getPeriodDays, type PeriodKey } from "@/utils/periodOptions";
import { formatKoreanDateTime } from "@/utils/date";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/shared/EmptyState";
import { ArticleDetailModal } from "@/components/shared/ArticleDetailModal";

export function CompetitorNewsTab() {
  const [periodKey, setPeriodKey] = useState<PeriodKey>("this-week");
  const [competitorFilter, setCompetitorFilter] = useState<string | null>(null);
  const [selectedArticleId, setSelectedArticleId] = useState<string | null>(null);
  const periodOptions = getCompetitorPeriodOptions();
  const days = getPeriodDays(periodKey);

  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: newsReportKeys.competitorTimeline({ days, periodKey }),
    queryFn: () => userIntelligenceService.getCompetitorTimeline({ days })
  });

  const items: CompetitorTimelineItem[] = data?.items ?? [];

  const seen = new Map<string, string>();
  for (const item of items) {
    if (!seen.has(item.competitorId)) seen.set(item.competitorId, item.competitorName);
  }
  const competitors = Array.from(seen.entries()).map(([id, name]) => ({ id, name }));
  const filteredItems = competitorFilter ? items.filter((item) => item.competitorId === competitorFilter) : items;

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap gap-2">
        {periodOptions.map((opt) => (
          <Button
            key={opt.key}
            variant={periodKey === opt.key ? "default" : "outline"}
            size="sm"
            onClick={() => {
              setPeriodKey(opt.key);
              setCompetitorFilter(null);
            }}
          >
            {opt.label}
          </Button>
        ))}
      </div>

      {competitors.length > 0 && (
        <div className="flex flex-wrap gap-2">
          <Button
            variant={competitorFilter === null ? "secondary" : "outline"}
            size="sm"
            onClick={() => setCompetitorFilter(null)}
          >
            전체
          </Button>
          {competitors.map((comp) => (
            <Button
              key={comp.id}
              variant={competitorFilter === comp.id ? "secondary" : "outline"}
              size="sm"
              onClick={() => setCompetitorFilter(competitorFilter === comp.id ? null : comp.id)}
            >
              {comp.name}
            </Button>
          ))}
        </div>
      )}

      {isLoading && <div className="py-8 text-center text-sm text-muted-foreground">불러오는 중...</div>}

      {isError && (
        <EmptyState
          title="경쟁사 뉴스를 불러올 수 없어요"
          description="네트워크 상태를 확인하고 다시 시도해주세요"
          action={
            <Button variant="outline" size="sm" onClick={() => refetch()}>
              다시 시도
            </Button>
          }
        />
      )}

      {!isLoading && !isError && filteredItems.length === 0 && (
        <EmptyState
          title="경쟁사 뉴스가 없어요"
          description={
            competitorFilter
              ? "선택한 경쟁사의 뉴스가 해당 기간에 없어요"
              : "등록된 경쟁사의 뉴스가 해당 기간에 없어요"
          }
        />
      )}

      {filteredItems.length > 0 && (
        <div className="rounded-md border divide-y">
          {filteredItems.map((item) => (
            <div key={item.summaryId} className="p-4 space-y-1">
              <button
                type="button"
                className="text-sm font-medium text-left hover:underline line-clamp-2"
                aria-label="기사 상세 보기"
                onClick={() => setSelectedArticleId(item.summaryId)}
              >
                {item.title}
              </button>
              {item.summary && (
                <p className="text-sm text-muted-foreground line-clamp-1">{item.summary}</p>
              )}
              <div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
                <span>{item.competitorName}</span>
                <span>·</span>
                <span>{formatKoreanDateTime(item.createdAt)}</span>
              </div>
            </div>
          ))}
        </div>
      )}

      <ArticleDetailModal
        variant="competitor"
        articleId={selectedArticleId}
        onClose={() => setSelectedArticleId(null)}
      />
    </div>
  );
}
