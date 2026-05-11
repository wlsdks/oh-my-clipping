import { useState } from "react";
import { useForm, Controller } from "react-hook-form";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { z } from "zod";
import { zodResolver } from "@hookform/resolvers/zod";
import { ChevronDown, ChevronUp } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Switch } from "@/components/ui/switch";
import { Sheet, SheetContent, SheetHeader, SheetTitle, SheetDescription } from "@/components/ui/sheet";
import { Collapsible, CollapsibleTrigger, CollapsibleContent } from "@/components/ui/collapsible";
import { KeywordTagInput } from "@/components/shared/KeywordTagInput";
import { ruleService } from "@/services/ruleService";
import { ruleKeys } from "@/queries/ruleKeys";
import { historyKeys } from "@/queries/historyKeys";
import { showSaveToastWithUndo } from "@/utils/saveToastUndo";
import { userFriendlyMessage, extractStaleEditInfo } from "@/shared/lib/httpError";
import { useStaleEditStore } from "@/lib/staleEditBus";
import { useEditingPresence } from "@/hooks/useEditingPresence";
import { EditingPresenceBadge } from "@/components/shared/EditingPresenceBadge";
import { ChangeDetectionStrip } from "@/components/shared/ChangeDetectionStrip";
import type { CategoryRule } from "@/types/category";

const drawerSchema = z.object({
  includeKeywords: z.array(z.string()),
  excludeKeywords: z.array(z.string()),
  includeThreshold: z.coerce.number().min(0).max(1),
  reviewThreshold: z.coerce.number().min(0).max(1),
  uncertainToReview: z.boolean(),
  autoExcludeEnabled: z.boolean(),
});

type DrawerFormValues = z.infer<typeof drawerSchema>;

interface KeywordRulesDrawerProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  categoryId: string;
  categoryName: string;
  initialRule: CategoryRule | null;
}

function defaultValues(rule: CategoryRule | null): DrawerFormValues {
  return {
    includeKeywords: rule?.includeKeywords ?? [],
    excludeKeywords: rule?.excludeKeywords ?? [],
    includeThreshold: rule?.includeThreshold ?? 0.55,
    reviewThreshold: rule?.reviewThreshold ?? 0.35,
    uncertainToReview: rule?.uncertainToReview ?? true,
    autoExcludeEnabled: rule?.autoExcludeEnabled ?? true,
  };
}

export function KeywordRulesDrawer({
  open,
  onOpenChange,
  categoryId,
  categoryName,
  initialRule,
}: KeywordRulesDrawerProps) {
  const qc = useQueryClient();
  const [advancedOpen, setAdvancedOpen] = useState(false);
  const [isSaving, setIsSaving] = useState(false);

  const { control, handleSubmit, reset } = useForm<DrawerFormValues>({
    resolver: zodResolver(drawerSchema),
    defaultValues: defaultValues(initialRule),
  });

  // 드로어가 열릴 때마다 최신 initialRule로 폼 초기화
  function handleOpenChange(next: boolean) {
    if (next) {
      reset(defaultValues(initialRule));
      setAdvancedOpen(false);
    }
    onOpenChange(next);
  }

  // 편집 presence + 변경 감지 폴링. 카테고리 룰은 카테고리 식별자로 추적.
  const { otherEditors } = useEditingPresence({
    resourceType: "categoryRule",
    resourceId: categoryId,
    enabled: open && Boolean(categoryId)
  });

  const { data: liveRule } = useQuery({
    queryKey: [...ruleKeys.detail(categoryId), "live"],
    queryFn: () => ruleService.getCategoryRule(categoryId),
    enabled: open && Boolean(categoryId),
    refetchInterval: open && categoryId ? 30_000 : false,
    refetchIntervalInBackground: false,
    retry: false
  });

  async function onSubmit(values: DrawerFormValues) {
    setIsSaving(true);
    try {
      const saved = await ruleService.updateCategoryRule(categoryId, {
        includeKeywords: values.includeKeywords,
        excludeKeywords: values.excludeKeywords,
        includeThreshold: values.includeThreshold,
        reviewThreshold: values.reviewThreshold,
        uncertainToReview: values.uncertainToReview,
        autoExcludeEnabled: values.autoExcludeEnabled,
        updatedBy: "admin-console",
        // 낙관적 잠금: 드로어를 열 때 받은 rule의 updated_at을 재전송해 경합을 감지한다.
        expectedUpdatedAt: initialRule?.updatedAt ?? null,
      });
      qc.invalidateQueries({ queryKey: ruleKeys.all });
      qc.invalidateQueries({ queryKey: historyKeys.byResource("category_rule", categoryId) });
      showSaveToastWithUndo({
        resource: "category_rule",
        savedId: categoryId,
        savedUpdatedAt: saved.updatedAt,
        successMessage: "키워드 규칙이 저장됐어요",
        onRestored: () => {
          qc.invalidateQueries({ queryKey: ruleKeys.all });
          qc.invalidateQueries({ queryKey: historyKeys.byResource("category_rule", categoryId) });
        }
      });
      onOpenChange(false);
    } catch (err) {
      // 낙관적 잠금 충돌이면 전역 모달을 통해 최신 불러오기 UX를 제공한다.
      const staleInfo = extractStaleEditInfo(err);
      if (staleInfo) {
        useStaleEditStore.getState().show(
          staleInfo,
          async () => {
            await qc.invalidateQueries({ queryKey: ruleKeys.all });
          },
          { draftKey: `draft:rule:${categoryId}` }
        );
        return;
      }
      toast.error(userFriendlyMessage(err, "저장하지 못했어요"));
    } finally {
      setIsSaving(false);
    }
  }

  return (
    <Sheet open={open} onOpenChange={handleOpenChange}>
      <SheetContent side="right" className="w-full sm:max-w-lg flex flex-col p-0">
        <SheetHeader className="px-6 pt-6 pb-4 border-b shrink-0">
          <SheetTitle className="text-base font-semibold">
            {categoryName} — 키워드 관리
          </SheetTitle>
          <SheetDescription className="text-xs text-muted-foreground">
            포함·제외 키워드로 기사를 자동으로 분류해요
          </SheetDescription>
          {otherEditors.length > 0 && (
            <div className="pt-2">
              <EditingPresenceBadge editors={otherEditors} />
            </div>
          )}
        </SheetHeader>
        <div className="px-6 pt-2">
          <ChangeDetectionStrip
            initialUpdatedAt={initialRule?.updatedAt}
            currentUpdatedAt={liveRule?.updatedAt ?? initialRule?.updatedAt}
            onReload={async () => {
              await qc.invalidateQueries({ queryKey: ruleKeys.detail(categoryId) });
            }}
          />
        </div>

        <form
          onSubmit={handleSubmit(onSubmit)}
          className="flex flex-col flex-1 overflow-y-auto"
        >
          <div className="flex flex-col gap-5 px-6 py-5">
            {/* 포함 키워드 */}
            <div className="flex flex-col gap-2">
              <label className="text-sm font-medium">포함 키워드</label>
              <p className="text-xs text-muted-foreground">이 키워드가 포함된 기사를 우선 포함해요</p>
              <Controller
                control={control}
                name="includeKeywords"
                render={({ field }) => (
                  <KeywordTagInput
                    tags={field.value}
                    onChange={field.onChange}
                    placeholder="예: AI, 머신러닝 — Enter로 추가"
                    color="include"
                    disabled={isSaving}
                  />
                )}
              />
            </div>

            {/* 제외 키워드 */}
            <div className="flex flex-col gap-2">
              <label className="text-sm font-medium">제외 키워드</label>
              <p className="text-xs text-muted-foreground">이 키워드가 포함된 기사를 자동으로 제외해요</p>
              <Controller
                control={control}
                name="excludeKeywords"
                render={({ field }) => (
                  <KeywordTagInput
                    tags={field.value}
                    onChange={field.onChange}
                    placeholder="예: 광고, 이벤트 — Enter로 추가"
                    color="exclude"
                    disabled={isSaving}
                  />
                )}
              />
            </div>

            {/* 고급 설정 Collapsible */}
            <Collapsible open={advancedOpen} onOpenChange={setAdvancedOpen}>
              <CollapsibleTrigger asChild>
                <button
                  type="button"
                  className="flex items-center gap-1 text-xs font-semibold text-muted-foreground hover:text-foreground transition-colors py-1"
                >
                  고급 설정 (임계값, 토글)
                  {advancedOpen ? (
                    <ChevronUp className="h-3.5 w-3.5" />
                  ) : (
                    <ChevronDown className="h-3.5 w-3.5" />
                  )}
                </button>
              </CollapsibleTrigger>
              <CollapsibleContent>
                <div className="flex flex-col gap-4 mt-3 pt-3 border-t border-border/50">
                  {/* 임계값 입력 */}
                  <div className="grid grid-cols-2 gap-4">
                    <div className="flex flex-col gap-1.5">
                      <label className="text-xs text-muted-foreground">포함 점수 기준 (0~1)</label>
                      <Controller
                        control={control}
                        name="includeThreshold"
                        render={({ field }) => (
                          <Input
                            type="number"
                            step="0.05"
                            min={0}
                            max={1}
                            {...field}
                            disabled={isSaving}
                            className="text-sm"
                          />
                        )}
                      />
                    </div>
                    <div className="flex flex-col gap-1.5">
                      <label className="text-xs text-muted-foreground">검토 점수 기준 (0~1)</label>
                      <Controller
                        control={control}
                        name="reviewThreshold"
                        render={({ field }) => (
                          <Input
                            type="number"
                            step="0.05"
                            min={0}
                            max={1}
                            {...field}
                            disabled={isSaving}
                            className="text-sm"
                          />
                        )}
                      />
                    </div>
                  </div>

                  {/* 토글 옵션 */}
                  <div className="flex flex-col gap-3">
                    <Controller
                      control={control}
                      name="uncertainToReview"
                      render={({ field }) => (
                        <div className="flex items-center justify-between">
                          <div>
                            <p className="text-sm">불확실 → 검토 필요</p>
                            <p className="text-xs text-muted-foreground">점수가 애매한 기사를 검토 대기로 분류</p>
                          </div>
                          <Switch
                            checked={field.value}
                            onCheckedChange={field.onChange}
                            disabled={isSaving}
                          />
                        </div>
                      )}
                    />
                    <Controller
                      control={control}
                      name="autoExcludeEnabled"
                      render={({ field }) => (
                        <div className="flex items-center justify-between">
                          <div>
                            <p className="text-sm">자동 제외 활성화</p>
                            <p className="text-xs text-muted-foreground">제외 키워드 매칭 시 자동으로 제외</p>
                          </div>
                          <Switch
                            checked={field.value}
                            onCheckedChange={field.onChange}
                            disabled={isSaving}
                          />
                        </div>
                      )}
                    />
                  </div>
                </div>
              </CollapsibleContent>
            </Collapsible>
          </div>

          {/* 저장 버튼 (하단 고정) */}
          <div className="px-6 py-4 border-t shrink-0">
            <Button type="submit" disabled={isSaving} className="w-full">
              {isSaving ? "저장 중..." : "저장"}
            </Button>
          </div>
        </form>
      </SheetContent>
    </Sheet>
  );
}
