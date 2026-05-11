import { useEffect, useState } from "react";
import { Loader2 } from "lucide-react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Tabs, TabsList, TabsTrigger, TabsContent } from "@/components/ui/tabs";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Switch } from "@/components/ui/switch";
import { Textarea } from "@/components/ui/textarea";
import { userFriendlyMessage } from "@/shared/lib/httpError";
import { categoryRuleBundleService } from "@/services/categoryRuleBundleService";
import { categoryRuleService } from "@/services/categoryRuleService";
import { categoryKeys } from "@/queries/categoryKeys";
import { organizationKeys } from "@/queries/organizationKeys";
import { resolveDigestModeClient, modeLabel } from "@/shared/lib/digestMode";
import { OrganizationMultiSelect } from "@/components/shared/OrganizationMultiSelect";
import type { RuleDryRunResult } from "@/types/categoryRule";
import { RuleDryRunPreview } from "./RuleDryRunPreview";

interface CurrentBundle {
  excludeEventTypes: string[];
  includeKeywords: string[];
  organizationIds: string[];
  accountBasedDigestEnabled: boolean;
  shadowModeEnabled: boolean;
  shadowEnabledAt: string | null;
}

interface CategoryRuleEditModalProps {
  open: boolean;
  categoryId: string;
  /** optional — 제목에 노출하기 위한 카테고리명. 없으면 "룰 편집" 으로 대체 */
  categoryName?: string;
  currentBundle: CurrentBundle;
  onClose: () => void;
}

/**
 * 카테고리 룰 편집에서 선택 가능한 기본 event_type 옵션.
 *
 * 백엔드 `SummarizationPrompts` 의 표준 enum + 도메인 관례값(EARNINGS) 을 포함한다.
 */
const EVENT_TYPE_OPTIONS = [
  "FUNDING",
  "EARNINGS",
  "PARTNERSHIP",
  "PRODUCT_LAUNCH",
  "POLICY",
  "PERSONNEL",
  "OTHER",
];

/**
 * shadowEnabledAt 으로부터 "N일 전" 형태 상대 시간을 반환한다.
 * Intl.RelativeTimeFormat 을 사용해 한국어로 렌더한다.
 */
function formatShadowEnabledAt(shadowEnabledAt: string): string {
  const rtf = new Intl.RelativeTimeFormat("ko", { numeric: "auto" });
  const diffMs = Date.now() - new Date(shadowEnabledAt).getTime();
  const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24));
  if (diffDays === 0) return "오늘";
  return rtf.format(-diffDays, "day");
}

/**
 * 키워드 textarea 문자열을 파싱해 정제된 배열로 반환한다.
 * 줄 단위로 분리 → trim → 빈 줄 제거 → dedup.
 */
function parseKeywords(raw: string): string[] {
  const seen = new Set<string>();
  const result: string[] = [];
  for (const line of raw.split("\n")) {
    const trimmed = line.trim();
    if (trimmed && !seen.has(trimmed)) {
      seen.add(trimmed);
      result.push(trimmed);
    }
  }
  return result;
}

/**
 * 운영자가 카테고리 rule-bundle 전체를 한 번에 편집하는 모달.
 *
 * 탭 구조:
 *  - "필터 & 발송 설정": event_type 체크박스 + 포함 키워드 + 조직 선택 + Account-Based 스위치
 *  - "감사 로그": 비활성(disabled) — 추후 구현 예정
 *
 * 저장 시 `PUT /api/admin/categories/{id}/rule-bundle` 단일 요청으로 atomic 업데이트.
 * 성공 시 digest mode 가 바뀌면 toast 로 변경 내용 안내.
 */
export function CategoryRuleEditModal({
  open,
  categoryId,
  categoryName,
  currentBundle,
  onClose,
}: CategoryRuleEditModalProps) {
  const queryClient = useQueryClient();

  // 선택된 event_type 집합
  const [selected, setSelected] = useState<string[]>(currentBundle.excludeEventTypes);
  // 포함 키워드 textarea 원문 — 파싱은 저장 시 수행
  const [keywordsRaw, setKeywordsRaw] = useState<string>(
    currentBundle.includeKeywords.join("\n"),
  );
  // 선택된 조직 id 목록
  const [orgIds, setOrgIds] = useState<string[]>(currentBundle.organizationIds);
  // Account-Based Digest 활성화 여부
  const [accountBased, setAccountBased] = useState<boolean>(
    currentBundle.accountBasedDigestEnabled,
  );
  // Shadow Mode 활성화 여부
  const [shadow, setShadow] = useState<boolean>(currentBundle.shadowModeEnabled);
  // dry-run 결과 캐시
  const [dryRunResult, setDryRunResult] = useState<RuleDryRunResult | null>(null);

  // currentBundle 은 의도적으로 snapshot-on-open 으로만 읽는다 — 부모의 background refetch 로 reference 가 바뀌어도 편집 중 값이 리셋되지 않도록 deps 에서 제외한다.
  useEffect(() => {
    if (open) {
      setSelected(currentBundle.excludeEventTypes);
      setKeywordsRaw(currentBundle.includeKeywords.join("\n"));
      setOrgIds(currentBundle.organizationIds);
      setAccountBased(currentBundle.accountBasedDigestEnabled);
      setShadow(currentBundle.shadowModeEnabled);
      setDryRunResult(null);
    }
  }, [open]);

  // dry-run mutation — event_type 체크 후 언제든 재호출 가능
  const dryRunMutation = useMutation({
    mutationFn: () =>
      categoryRuleService.dryRun(categoryId, {
        excludeEventTypes: selected,
      }),
    onSuccess: (result) => {
      setDryRunResult(result);
    },
    onError: (error) => {
      toast.error(userFriendlyMessage(error, "미리보기를 불러오지 못했어요"));
    },
  });

  // rule-bundle 저장 mutation
  const mutation = useMutation({
    mutationFn: () => {
      const parsedKeywords = parseKeywords(keywordsRaw);
      return categoryRuleBundleService.update(categoryId, {
        excludeEventTypes: selected,
        includeKeywords: parsedKeywords,
        organizationIds: orgIds,
        accountBasedDigestEnabled: accountBased,
        // Account-Based OFF 이면 Shadow 도 강제 OFF
        shadowModeEnabled: accountBased && shadow,
      });
    },
    onSuccess: () => {
      const parsedKeywords = parseKeywords(keywordsRaw);
      // digest mode 변경 여부 확인 → 변경됐으면 toast 안내
      const beforeMode = resolveDigestModeClient(
        currentBundle.includeKeywords.length,
        currentBundle.organizationIds.length,
      );
      const afterMode = resolveDigestModeClient(parsedKeywords.length, orgIds.length);
      if (beforeMode !== afterMode && afterMode !== null) {
        toast.info(
          `발송 구성이 '${modeLabel(afterMode)}'로 변경되었어요 (다음 발송부터 적용)`,
        );
      }
      queryClient.invalidateQueries({ queryKey: categoryKeys.detail(categoryId) });
      queryClient.invalidateQueries({ queryKey: organizationKeys.byCategory(categoryId) });
      onClose();
    },
    onError: (error) => {
      toast.error(userFriendlyMessage(error, "저장하지 못했어요"));
      // 모달은 유지 — 운영자가 수정/재시도 가능
    },
  });

  // 체크박스 토글
  function toggleEventType(eventType: string, checked: boolean) {
    setSelected((prev) => {
      if (checked) {
        if (prev.includes(eventType)) return prev;
        return [...prev, eventType];
      }
      return prev.filter((t) => t !== eventType);
    });
  }

  // Account-Based 스위치 — ON 시 Shadow 도 자동 ON
  function handleAccountBasedChange(next: boolean) {
    setAccountBased(next);
    if (next) setShadow(true);
  }

  const modalTitle = categoryName ? `룰 편집 — ${categoryName}` : "룰 편집";
  const isPending = mutation.isPending;

  return (
    <Dialog open={open} onOpenChange={(next) => !next && !isPending && onClose()}>
      <DialogContent
        className="sm:max-w-lg"
        data-testid="category-rule-edit-modal"
        data-category-id={categoryId}
      >
        <DialogHeader>
          <DialogTitle>{modalTitle}</DialogTitle>
          <DialogDescription>
            이 카테고리의 필터 규칙과 발송 구성을 한 번에 저장합니다.
          </DialogDescription>
        </DialogHeader>

        <Tabs defaultValue="rules">
          <TabsList>
            <TabsTrigger value="rules">필터 & 발송 설정</TabsTrigger>
            <TabsTrigger value="audit" disabled aria-disabled="true">
              감사 로그
            </TabsTrigger>
          </TabsList>

          <TabsContent value="rules" className="space-y-4 pt-2">
            {/* 자동 제외 이벤트 타입 */}
            <fieldset
              className="space-y-2"
              data-testid="category-rule-event-type-list"
            >
              <legend className="text-sm font-medium text-foreground">
                자동 제외할 이벤트 타입
              </legend>
              <ul className="grid grid-cols-2 gap-2">
                {EVENT_TYPE_OPTIONS.map((eventType) => {
                  const checked = selected.includes(eventType);
                  const inputId = `category-rule-event-type-${eventType}`;
                  return (
                    <li
                      key={eventType}
                      className="flex items-center gap-2 rounded-lg border bg-card px-3 py-2"
                    >
                      <Checkbox
                        id={inputId}
                        checked={checked}
                        data-testid={`category-rule-event-type-${eventType}`}
                        onCheckedChange={(next) =>
                          toggleEventType(eventType, next === true)
                        }
                        disabled={isPending}
                      />
                      <label
                        htmlFor={inputId}
                        className="text-sm font-medium text-foreground"
                      >
                        {eventType}
                      </label>
                    </li>
                  );
                })}
              </ul>
            </fieldset>

            {/* 포함 키워드 */}
            <div className="space-y-1.5">
              <label
                htmlFor="category-rule-keywords"
                className="text-sm font-medium text-foreground"
              >
                포함 키워드
              </label>
              <Textarea
                id="category-rule-keywords"
                data-testid="category-rule-keywords-textarea"
                placeholder={"키워드를 한 줄에 하나씩 입력하세요\n예: AI\n반도체"}
                value={keywordsRaw}
                onChange={(e) => setKeywordsRaw(e.target.value)}
                disabled={isPending}
                rows={4}
                className="resize-none"
              />
              <p className="text-xs text-muted-foreground">
                줄 단위로 구분 · 저장 시 중복 제거
              </p>
            </div>

            {/* 관심 조직 */}
            <div className="space-y-1.5">
              <p className="text-sm font-medium text-foreground">관심 조직</p>
              <OrganizationMultiSelect
                value={orgIds}
                onChange={setOrgIds}
                disabled={isPending}
              />
            </div>

            {/* Account-Based Digest 스위치 */}
            <div className="space-y-2">
              <div className="flex items-center justify-between gap-3">
                <div className="space-y-0.5">
                  <p className="text-sm font-medium text-foreground">
                    Account-Based Digest (베타)
                  </p>
                  <p className="text-xs text-muted-foreground">
                    주제와 관심 기업을 같이 입력한 구독에 새 발송 구조 적용
                  </p>
                </div>
                <Switch
                  aria-label="Account-Based Digest (베타)"
                  checked={accountBased}
                  onCheckedChange={handleAccountBasedChange}
                  disabled={isPending}
                />
              </div>

              {/* Shadow Mode — Account-Based ON 일 때만 표시 */}
              {accountBased && (
                <div className="ml-4 space-y-1 border-l-2 border-muted pl-4">
                  <div className="flex items-center justify-between gap-3">
                    <div className="space-y-0.5">
                      <p className="text-sm font-medium text-foreground">Shadow Mode</p>
                      <p className="text-xs text-muted-foreground">
                        Slack 실제 발송 없이 diff 만 기록 (canary 첫 1주 권장)
                      </p>
                    </div>
                    <Switch
                      aria-label="Shadow Mode"
                      checked={shadow}
                      onCheckedChange={setShadow}
                      disabled={isPending}
                    />
                  </div>
                  {currentBundle.shadowEnabledAt && (
                    <p className="text-xs text-muted-foreground">
                      Shadow 시작: {formatShadowEnabledAt(currentBundle.shadowEnabledAt)}
                    </p>
                  )}
                </div>
              )}
            </div>

            {/* 미리보기 */}
            <div className="flex items-center justify-between gap-2">
              <p className="text-xs text-muted-foreground tabular-nums">
                선택 {selected.length}개
              </p>
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={() => dryRunMutation.mutate()}
                disabled={dryRunMutation.isPending || isPending}
                data-testid="category-rule-preview-button"
              >
                {dryRunMutation.isPending ? (
                  <>
                    <Loader2
                      className="mr-1.5 h-3.5 w-3.5 animate-spin"
                      aria-hidden="true"
                    />
                    불러오는 중…
                  </>
                ) : (
                  "미리보기"
                )}
              </Button>
            </div>

            {dryRunResult && <RuleDryRunPreview result={dryRunResult} />}
          </TabsContent>
        </Tabs>

        <DialogFooter>
          <Button
            type="button"
            variant="outline"
            onClick={onClose}
            disabled={isPending}
            data-testid="category-rule-cancel-button"
          >
            취소
          </Button>
          <Button
            type="button"
            onClick={() => mutation.mutate()}
            disabled={isPending}
            data-testid="category-rule-save-button"
          >
            {isPending ? (
              <>
                <Loader2
                  className="mr-1.5 h-3.5 w-3.5 animate-spin"
                  aria-hidden="true"
                />
                저장 중…
              </>
            ) : (
              "저장"
            )}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
