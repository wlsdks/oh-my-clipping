import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { ChevronDown, ChevronUp, ChevronRight } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Collapsible, CollapsibleTrigger, CollapsibleContent } from "@/components/ui/collapsible";
import { ruleService } from "@/services/ruleService";
import { ruleKeys } from "@/queries/ruleKeys";
import { groupByKeyword } from "./model/excludedGrouping";
import {
  EXCLUDED_ITEMS_LIMIT,
  TOAST_UNDO_DURATION_MS,
  DATE_RANGE_OPTIONS,
  DEFAULT_DATE_RANGE,
  type DateRangeValue,
} from "./model/excludedConstants";
import { userFriendlyMessage } from "@/shared/lib/httpError";
import type { CategoryRule } from "@/types/category";

interface ExcludedArticlesSectionProps {
  categoryId: string;
}

export function ExcludedArticlesSection({ categoryId }: ExcludedArticlesSectionProps) {
  const [isOpen, setIsOpen] = useState(false);

  return (
    <Collapsible open={isOpen} onOpenChange={setIsOpen}>
      <CollapsibleTrigger asChild>
        <button
          type="button"
          className="flex w-full items-center justify-between py-3 text-sm font-medium text-foreground hover:text-foreground/80 transition-colors"
        >
          <span>제외된 기사</span>
          {isOpen ? (
            <ChevronUp className="h-4 w-4 text-muted-foreground" />
          ) : (
            <ChevronDown className="h-4 w-4 text-muted-foreground" />
          )}
        </button>
      </CollapsibleTrigger>
      <CollapsibleContent>
        {/* 데이터는 펼쳐진 경우에만 로드 */}
        {isOpen && <ExcludedArticlesContent categoryId={categoryId} />}
      </CollapsibleContent>
    </Collapsible>
  );
}

function ExcludedArticlesContent({ categoryId }: { categoryId: string }) {
  const qc = useQueryClient();
  const [dateRange, setDateRange] = useState<DateRangeValue>(DEFAULT_DATE_RANGE);
  const [expandedKeywords, setExpandedKeywords] = useState<Set<string>>(new Set());

  const { data: excluded, isLoading } = useQuery({
    queryKey: ruleKeys.excludedItems(categoryId),
    queryFn: () => ruleService.getExcludedItems(categoryId, EXCLUDED_ITEMS_LIMIT),
    enabled: true,
    staleTime: 30_000,
  });

  // 키워드 제거를 위해 현재 규칙 조회
  const { data: rule } = useQuery({
    queryKey: ruleKeys.detail(categoryId),
    queryFn: () => ruleService.getCategoryRule(categoryId),
    enabled: true,
    staleTime: 30_000,
    retry: (failureCount, error) => {
      if ((error as { status?: number })?.status === 404) return false;
      return failureCount < 2;
    },
  });

  const removeKeywordMutation = useMutation({
    mutationFn: async ({ keyword }: { keyword: string }) => {
      if (!rule) throw new Error("rule not loaded");
      const newExcludeKeywords = rule.excludeKeywords.filter((k) => k !== keyword);
      return ruleService.updateCategoryRule(categoryId, { excludeKeywords: newExcludeKeywords });
    },
    onSuccess: (updatedRule: CategoryRule, variables) => {
      const previousKeywords = rule?.excludeKeywords ?? [];
      qc.setQueryData(ruleKeys.detail(categoryId), updatedRule);
      qc.invalidateQueries({ queryKey: ruleKeys.stats() });
      toast.success(`'${variables.keyword}' 키워드를 제거했어요`, {
        duration: TOAST_UNDO_DURATION_MS,
        action: {
          label: "실행 취소",
          onClick: async () => {
            try {
              await ruleService.updateCategoryRule(categoryId, { excludeKeywords: previousKeywords });
              qc.invalidateQueries({ queryKey: ruleKeys.all });
              toast.success("복원됐어요");
            } catch (err) {
              toast.error(userFriendlyMessage(err, "복원하지 못했어요"));
            }
          },
        },
      });
    },
    onError: (err) => toast.error(userFriendlyMessage(err, "제거하지 못했어요")),
  });

  function toggleExpand(keyword: string) {
    setExpandedKeywords((prev) => {
      const next = new Set(prev);
      if (next.has(keyword)) next.delete(keyword);
      else next.add(keyword);
      return next;
    });
  }

  if (isLoading) {
    return <p className="pb-3 text-xs text-muted-foreground">로딩 중...</p>;
  }

  const items = excluded?.items ?? [];
  const groups = groupByKeyword(items);

  return (
    <div className="flex flex-col gap-3 pb-2">
      {/* 날짜 범위 필터 */}
      <div className="flex gap-1">
        {DATE_RANGE_OPTIONS.map((opt) => (
          <button
            key={opt.value}
            type="button"
            className={`px-3 py-1 text-[10px] rounded-md border transition-colors ${
              dateRange === opt.value
                ? "bg-foreground text-background border-foreground"
                : "bg-card text-muted-foreground hover:text-foreground border-border"
            }`}
            onClick={() => setDateRange(opt.value)}
          >
            {opt.label}
          </button>
        ))}
      </div>

      {/* 그룹 목록 */}
      {groups.length === 0 ? (
        <p className="text-xs text-muted-foreground py-2 text-center">이 기간에 제외된 기사가 없어요</p>
      ) : (
        <div className="flex flex-col gap-2">
          <p className="text-[10px] text-muted-foreground">키워드별 그룹 · {groups.length}개</p>
          {groups.map((group) => {
            const isExpanded = expandedKeywords.has(group.keyword);
            return (
              <div key={group.keyword} className="rounded-lg bg-muted/50 p-3">
                <div className="flex items-center justify-between gap-2">
                  <div className="min-w-0">
                    <span className="text-sm font-semibold">'{group.keyword}'</span>
                    <span className="text-[10px] text-muted-foreground ml-2">
                      {group.count}건 제외
                    </span>
                  </div>
                  <div className="flex items-center gap-1 shrink-0">
                    <Button
                      size="sm"
                      variant="outline"
                      className="h-7 text-[10px] bg-[var(--status-danger-bg)] text-[var(--status-danger-text)] border-[var(--status-danger-text)]/30 hover:bg-[var(--status-danger-bg)]/80"
                      onClick={() => removeKeywordMutation.mutate({ keyword: group.keyword })}
                      disabled={removeKeywordMutation.isPending}
                    >
                      키워드 제거
                    </Button>
                    <Button
                      size="sm"
                      variant="ghost"
                      className="h-7 text-[10px] px-2"
                      onClick={() => toggleExpand(group.keyword)}
                    >
                      샘플 {group.samples.length}
                      {isExpanded ? (
                        <ChevronUp className="h-3 w-3 ml-1" />
                      ) : (
                        <ChevronRight className="h-3 w-3 ml-1" />
                      )}
                    </Button>
                  </div>
                </div>
                {isExpanded && (
                  <div className="mt-2 pt-2 border-t border-border/50 space-y-1">
                    {group.samples.map((sample, idx) => (
                      <p key={idx} className="text-[11px] text-muted-foreground line-clamp-1">
                        {sample.title}
                      </p>
                    ))}
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
