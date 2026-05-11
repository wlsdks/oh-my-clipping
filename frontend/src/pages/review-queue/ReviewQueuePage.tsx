import { useState, useEffect, useRef } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { userFriendlyMessage } from "@/shared/lib/httpError";
import { reviewKeys } from "@/queries/reviewKeys";
import { categoryKeys } from "@/queries/categoryKeys";
import { runtimeKeys } from "@/queries/runtimeKeys";
import { reviewService } from "@/services/reviewService";
import { categoryService } from "@/services/categoryService";
import { runtimeService } from "@/services/runtimeService";
import { useSlackChannelMap } from "@/hooks/useSlackChannelMap";
import { useDebounce } from "@/hooks/useDebounce";
import { useMediaQuery } from "@/hooks/useMediaQuery";
import { matchesKoreanSearch } from "@/utils/search";
import { useReviewSelection } from "./model/useReviewSelection";
import { useBulkSelection } from "./hooks/useBulkSelection";
import {
  STATUS_FILTER_OPTIONS,
  MAX_BULK_SELECT,
  bulkFailureMessage,
  type ReviewStatusFilter,
} from "./model/constants";
import { ReviewListPanel } from "./ReviewListPanel";
import { ReviewDetailPanel } from "./ReviewDetailPanel";
import { ReviewSlideOver } from "./ReviewSlideOver";
import { ReviewProgressHeader } from "./ReviewProgressHeader";
import { CategorySummaryCards } from "./CategorySummaryCards";
import { FloatingActionBar } from "./FloatingActionBar";
import { BulkApproveDialog } from "./BulkApproveDialog";
import { ReviewPolicyDashboard } from "./components/ReviewPolicyDashboard";
import { EmptyState } from "@/components/shared/EmptyState";
import { InfoTooltip } from "@/components/shared/InfoTooltip";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import type {
  BulkActionResponse,
  BulkRevertItem,
  ReviewQueueItem,
} from "@/types/review";

const ACTION_SERVICE: Record<string, (id: string, data: { reviewedBy: string }) => Promise<unknown>> = {
  approve: reviewService.approve,
  exclude: reviewService.exclude,
  review: reviewService.markForReview,
};

const ACTION_TOAST: Record<string, string> = {
  approve: "보내기로 처리했어요",
  exclude: "건너뛰기로 처리했어요",
  review: "확인 필요 상태로 되돌렸어요",
};

type PreviousStatus = "INCLUDE" | "REVIEW" | "EXCLUDE";
type BulkAction = "approve" | "exclude";

interface BulkDialogState {
  action: BulkAction;
  items: ReviewQueueItem[];
}

export function ReviewQueuePage() {
  const qc = useQueryClient();
  const { formatChannel } = useSlackChannelMap();
  const [statusFilter, setStatusFilter] = useState<ReviewStatusFilter>("REVIEW");
  const [categoryId, setCategoryId] = useState("");
  const [query, setQuery] = useState("");
  const [pendingIds, setPendingIds] = useState<Set<string>>(new Set());
  const [bulkDialog, setBulkDialog] = useState<BulkDialogState | null>(null);
  const debouncedQuery = useDebounce(query, 200);
  const isMobile = useMediaQuery("(max-width: 767px)");

  // ref to break circular dependency: handleAction → performAction (mutation defined below)
  const performActionRef = useRef<((params: { summaryId: string; action: string }) => void) | undefined>(undefined);

  const { data: categories = [] } = useQuery({
    queryKey: categoryKeys.lists(),
    queryFn: () => categoryService.getAll(),
  });

  // RuntimeSettings: batch UX flag + top-N 샘플링 수치
  const { data: runtimeSettings } = useQuery({
    queryKey: runtimeKeys.configs(),
    queryFn: () => runtimeService.getSettings(),
  });
  const perCategory = runtimeSettings?.defaultReviewPerCategory ?? 20;
  const usePerCategorySampling = !categoryId && perCategory > 0;
  // Feature flag + 모바일 비노출: 둘 다 만족해야 배치 UI 활성
  const batchEnabled = Boolean(runtimeSettings?.reviewBatchUxEnabled) && !isMobile;

  // 검토함 요약 (전체 필터 상태에서만 카테고리 카드로 노출)
  const { data: reviewSummary } = useQuery({
    queryKey: reviewKeys.summary(),
    queryFn: () => reviewService.getSummary(),
  });

  const {
    data: allItems = [],
    isLoading,
    isError,
    refetch,
  } = useQuery({
    queryKey: reviewKeys.queue({
      categoryId: categoryId || undefined,
      perCategory: usePerCategorySampling ? perCategory : undefined,
    }),
    queryFn: () =>
      reviewService.listItems({
        categoryId: categoryId || undefined,
        limit: 120,
        perCategory: usePerCategorySampling ? perCategory : undefined,
      }),
  });

  // 필터링
  const statusFiltered =
    statusFilter === "ALL" ? allItems : allItems.filter((item) => item.currentStatus === statusFilter);

  const searchFiltered = statusFiltered.filter((item) => {
    if (!debouncedQuery) return true;
    return (
      matchesKoreanSearch(item.title, debouncedQuery) ||
      item.keywords.some((kw) => matchesKoreanSearch(kw, debouncedQuery))
    );
  });

  const filteredIds = searchFiltered.map((item) => item.summaryId);

  // 일괄 선택 상태 (모든 조건에서 훅은 호출하되, 결과는 batchEnabled일 때만 쓴다)
  const { checkedIds, toggle, toggleRange, selectAll, clearAll } = useBulkSelection(filteredIds);

  function handleAction(summaryId: string, action: "approve" | "exclude" | "review") {
    performActionRef.current?.({ summaryId, action });
  }

  // 단축키: 벌크 선택 중이면 S/X는 "다이얼로그 오픈"으로 우회 (rubber-stamping 방지)
  const { selectedId, setSelectedId, selectNext } = useReviewSelection({
    itemIds: filteredIds,
    onAction: (id, action) => {
      // 체크된 항목이 있으면 단건 단축키는 무시 — 사용자 혼동 방지
      if (batchEnabled && checkedIds.size > 0) return;
      handleAction(id, action);
    },
    autoSelect: false,
    getSourceLink: (id) => searchFiltered.find((item) => item.summaryId === id)?.sourceLink,
  });

  // 필터 변경 시 단건 선택 리셋. 일괄 선택 해제는 useBulkSelection이 내용 기반으로 처리.
  useEffect(() => {
    setSelectedId(null);
  }, [statusFilter, categoryId]);

  const selectedItem = searchFiltered.find((item) => item.summaryId === selectedId) ?? null;

  // 단건 mutation (기존 유지)
  const { mutate: performAction } = useMutation({
    mutationFn: ({ summaryId, action }: { summaryId: string; action: string }) =>
      ACTION_SERVICE[action](summaryId, { reviewedBy: "admin-console" }),
    onMutate: ({ summaryId }) => {
      setPendingIds((prev) => new Set(prev).add(summaryId));
    },
    onSuccess: (_data, { summaryId, action }) => {
      toast.success(ACTION_TOAST[action], {
        action:
          action !== "review"
            ? {
                label: "되돌리기",
                onClick: () => {
                  reviewService.markForReview(summaryId, { reviewedBy: "admin-console" }).then(() => {
                    qc.invalidateQueries({ queryKey: reviewKeys.all });
                    toast.success("되돌렸어요");
                  });
                },
              }
            : undefined,
        duration: 5000,
      });
      selectNext();
      qc.invalidateQueries({ queryKey: reviewKeys.all });
    },
    onError: (err) => {
      toast.error(userFriendlyMessage(err, "처리하지 못했어요"));
    },
    onSettled: (_data, _err, { summaryId }) => {
      setPendingIds((prev) => {
        const next = new Set(prev);
        next.delete(summaryId);
        return next;
      });
    },
  });

  performActionRef.current = performAction;

  // 일괄 revert mutation — undo 버튼이 호출
  const { mutate: performBulkRevert, isPending: isRevertPending } = useMutation({
    mutationFn: (reverts: BulkRevertItem[]) => reviewService.bulkRevert(reverts),
    onSuccess: (result) => {
      const revertedCount = result.succeeded.length;
      if (revertedCount > 0) toast.success(`${revertedCount}건 되돌렸어요`);
      if (result.failed.length > 0) {
        const first = result.failed[0];
        toast.error(`일부 되돌리지 못했어요 (${bulkFailureMessage(first.code)})`);
      }
      qc.invalidateQueries({ queryKey: reviewKeys.all });
    },
    onError: (err) => {
      toast.error(userFriendlyMessage(err, "되돌리지 못했어요"));
    },
  });

  // 일괄 액션 mutation (승인/제외)
  const { mutate: performBulk, isPending: isBulkPending } = useMutation<
    BulkActionResponse,
    unknown,
    { action: BulkAction; ids: string[] },
    { snapshot: Map<string, PreviousStatus> }
  >({
    mutationFn: ({ action, ids }) => {
      const note = action === "approve" ? "관리자 일괄 승인" : "관리자 일괄 제외";
      return action === "approve"
        ? reviewService.bulkApprove(ids, note)
        : reviewService.bulkExclude(ids, note);
    },
    onMutate: ({ ids }) => {
      // invalidate 이전 원본 상태를 캡처. undo에서만 이 snapshot을 사용한다.
      const snapshot = new Map<string, PreviousStatus>();
      for (const id of ids) {
        const item = allItems.find((i) => i.summaryId === id);
        if (item) snapshot.set(id, item.currentStatus);
      }
      setPendingIds((prev) => {
        const next = new Set(prev);
        for (const id of ids) next.add(id);
        return next;
      });
      return { snapshot };
    },
    onSuccess: (result, _vars, ctx) => {
      showBulkResultToast(result, ctx?.snapshot ?? new Map());
      if (result.succeeded.length > 0) clearAll();
      setBulkDialog(null);
      qc.invalidateQueries({ queryKey: reviewKeys.all });
    },
    onError: (err) => {
      // 선택은 유지해 재시도 가능하게 (엣지 3-4)
      toast.error(userFriendlyMessage(err, "처리하지 못했어요"));
    },
    onSettled: (_data, _err, { ids }) => {
      setPendingIds((prev) => {
        const next = new Set(prev);
        for (const id of ids) next.delete(id);
        return next;
      });
    },
  });

  /** 벌크 결과를 토스트로 안내한다. undo는 snapshot의 succeeded만을 대상으로 한다. */
  function showBulkResultToast(result: BulkActionResponse, snapshot: Map<string, PreviousStatus>) {
    const { succeeded, failed } = result;

    if (succeeded.length > 0 && failed.length === 0) {
      toast.success(`${succeeded.length}건 처리했어요`, {
        duration: 5000,
        action: {
          label: "되돌리기",
          onClick: () => {
            const reverts: BulkRevertItem[] = succeeded
              .map((id) => {
                const previousStatus = snapshot.get(id);
                if (!previousStatus) return null;
                return { id, previousStatus };
              })
              .filter((r): r is BulkRevertItem => r !== null);
            if (reverts.length > 0) performBulkRevert(reverts);
          },
        },
      });
      return;
    }

    if (succeeded.length > 0 && failed.length > 0) {
      // 실패 코드별 카운트 집계
      const codeCounts = failed.reduce<Record<string, number>>((acc, f) => {
        acc[f.code] = (acc[f.code] ?? 0) + 1;
        return acc;
      }, {});
      const detail = Object.entries(codeCounts)
        .map(([code, count]) => `${bulkFailureMessage(code)} ${count}건`)
        .join(", ");
      toast.warning(`${succeeded.length}건 성공, ${failed.length}건 실패`, {
        description: detail,
        duration: 7000,
        action: {
          label: "되돌리기",
          onClick: () => {
            const reverts: BulkRevertItem[] = succeeded
              .map((id) => {
                const previousStatus = snapshot.get(id);
                if (!previousStatus) return null;
                return { id, previousStatus };
              })
              .filter((r): r is BulkRevertItem => r !== null);
            if (reverts.length > 0) performBulkRevert(reverts);
          },
        },
      });
      return;
    }

    if (succeeded.length === 0 && failed.length > 0) {
      const firstCode = failed[0].code;
      toast.error(`모두 실패했어요 (${bulkFailureMessage(firstCode)})`);
    }
  }

  function openBulkDialog(action: BulkAction) {
    const items = searchFiltered.filter((item) => checkedIds.has(item.summaryId));
    if (items.length === 0) return;
    if (items.length > MAX_BULK_SELECT) {
      toast.warning(`최대 ${MAX_BULK_SELECT}건까지 일괄 처리할 수 있어요`);
      return;
    }
    setBulkDialog({ action, items });
  }

  function confirmBulk() {
    if (!bulkDialog) return;
    const ids = bulkDialog.items.map((i) => i.summaryId);
    performBulk({ action: bulkDialog.action, ids });
  }

  const counts = {
    REVIEW: allItems.filter((i) => i.currentStatus === "REVIEW").length,
    INCLUDE: allItems.filter((i) => i.currentStatus === "INCLUDE").length,
    EXCLUDE: allItems.filter((i) => i.currentStatus === "EXCLUDE").length,
  };

  const showCategory = !categoryId;

  // categoryId → 발송 채널 레이블 조회 (기사 보내기 시 어느 채널로 갈지 표시용)
  const categoryChannelLabel = selectedItem
    ? (() => {
        const cat = categories.find((c) => c.id === selectedItem.categoryId);
        if (!cat?.slackChannelId) return undefined;
        return formatChannel(cat.slackChannelId);
      })()
    : undefined;

  if (isLoading) {
    return (
      <div className="p-4 sm:p-6 space-y-5" role="status" aria-live="polite">
        <span className="sr-only">로딩 중...</span>
        <div className="space-y-2">
          <div className="h-7 w-28 rounded bg-muted animate-pulse" />
          <div className="h-4 w-56 rounded bg-muted animate-pulse" />
        </div>
        <div className="flex items-center gap-2">
          {[1, 2, 3, 4].map((i) => (
            <div key={i} className="h-7 w-16 rounded-full bg-muted animate-pulse" />
          ))}
        </div>
        <div className="hidden md:grid md:grid-cols-[2fr_3fr] gap-4">
          <div className="space-y-2">
            {[1, 2, 3, 4, 5].map((i) => (
              <div key={i} className="h-16 rounded-xl bg-muted animate-pulse" />
            ))}
          </div>
          <div className="h-80 rounded-xl bg-muted animate-pulse" />
        </div>
      </div>
    );
  }

  if (isError) {
    return (
      <div className="p-8 text-center space-y-3">
        <p className="text-sm text-muted-foreground">데이터를 불러오지 못했어요</p>
        <Button variant="outline" size="sm" onClick={() => refetch()}>
          다시 불러오기
        </Button>
      </div>
    );
  }

  return (
    <div className="p-4 sm:p-6 space-y-5 pb-24">
      {/* 헤더 */}
      <div>
        <h1 className="text-2xl font-bold">뉴스 검토</h1>
        <p className="text-sm text-muted-foreground mt-1">
          확인 필요 {counts.REVIEW}건 · 보내기 {counts.INCLUDE}건 · 건너뛰기 {counts.EXCLUDE}건
        </p>
        <p className="text-xs text-muted-foreground mt-1">
          AI가 발송 여부를 제안한 기사 목록입니다. '확인 필요'는 관리자 판단 대기, '보내기'는 승인된 기사, '건너뛰기'는 제외된 기사입니다.
        </p>
      </div>

      {/* 리뷰 정책 대시보드 — 카테고리별 임계값/대기 건수/점수 분포 요약 */}
      <ReviewPolicyDashboard onCategoryClick={setCategoryId} />

      <ReviewProgressHeader
        totalCount={counts.REVIEW + counts.INCLUDE + counts.EXCLUDE}
        reviewCount={counts.REVIEW}
        includeCount={counts.INCLUDE}
        excludeCount={counts.EXCLUDE}
      />

      {showCategory && reviewSummary && (
        <CategorySummaryCards
          categories={reviewSummary.categories}
          onSelectCategory={(id) => setCategoryId(id)}
        />
      )}

      {/* 필터 바 */}
      <div className="flex items-center justify-between gap-3">
        <div className="flex items-center gap-1.5">
          {STATUS_FILTER_OPTIONS.map((opt) => {
            const count = opt.value === "ALL" ? allItems.length : counts[opt.value as keyof typeof counts];
            return (
              <Button
                key={opt.value}
                size="sm"
                variant={statusFilter === opt.value ? "default" : "outline"}
                className="h-7 text-xs rounded-full px-3"
                onClick={() => setStatusFilter(opt.value as ReviewStatusFilter)}
              >
                {opt.label}
                <span className="ml-1 text-[10px] opacity-60">{count}</span>
              </Button>
            );
          })}
          <InfoTooltip
            ariaLabel="확인 필요 설명"
            content="관리자 판단 대기 중. AI가 명확한 결정을 내리지 못한 기사"
          />
          <InfoTooltip
            ariaLabel="보내기 설명"
            content="슬랙 발송 대상으로 승인된 기사"
          />
          <InfoTooltip
            ariaLabel="건너뛰기 설명"
            content="중복·저품질로 제외된 기사"
          />
        </div>
        <div className="flex items-center gap-2">
          <Select value={categoryId || "all"} onValueChange={(v) => setCategoryId(v === "all" ? "" : v)}>
            <SelectTrigger className="w-[160px] h-8 text-xs" data-testid="topic-combobox-trigger">
              <SelectValue placeholder="주제 전체" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">주제 전체</SelectItem>
              {categories.map((cat) => (
                <SelectItem key={cat.id} value={cat.id}>
                  {cat.name}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Input
            placeholder="검색 (초성 가능)"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            className="max-w-[180px] h-8 text-sm"
            aria-label="뉴스 검색"
          />
        </div>
      </div>

      {allItems.length >= 120 && <p className="text-[11px] text-muted-foreground">최근 120건을 표시해요</p>}

      {/* 메인 콘텐츠 */}
      {searchFiltered.length === 0 ? (
        <EmptyState
          title={debouncedQuery ? "검색 결과가 없어요" : "검토할 항목이 없어요"}
          description={debouncedQuery ? "다른 키워드로 검색해 보세요" : "AI가 분류한 뉴스가 쌓이면 여기 나타나요"}
        />
      ) : (
        <>
          {/* 데스크톱: 분할 패널 */}
          <div className="hidden md:grid md:grid-cols-[2fr_3fr] gap-4">
            <ReviewListPanel
              items={searchFiltered}
              selectedId={selectedId}
              showCategory={showCategory}
              onSelect={setSelectedId}
              batchMode={batchEnabled}
              checkedIds={checkedIds}
              onToggle={toggle}
              onToggleRange={toggleRange}
              onSelectAll={selectAll}
              onClearAll={clearAll}
            />
            <ReviewDetailPanel
              item={selectedItem}
              isPending={selectedId ? pendingIds.has(selectedId) : false}
              onAction={handleAction}
              allDone={searchFiltered.length === 0}
              channelLabel={categoryChannelLabel}
            />
          </div>

          {/* 모바일: 리스트만, 배치 UI 완전 비노출 */}
          <div className="md:hidden">
            <ReviewListPanel
              items={searchFiltered}
              selectedId={null}
              showCategory={showCategory}
              onSelect={setSelectedId}
              batchMode={false}
            />
            {isMobile && (
              <ReviewSlideOver
                item={selectedItem}
                isPending={selectedId ? pendingIds.has(selectedId) : false}
                onAction={handleAction}
                onClose={() => setSelectedId(null)}
                channelLabel={categoryChannelLabel}
              />
            )}
          </div>

          {/* 키보드 단축키 힌트 */}
          <p className="hidden md:block text-[11px] text-muted-foreground">
            ↑↓ 이동 · S 보내기 · X 건너뛰기 · O 원문 · Esc 선택해제
            {batchEnabled && " · 체크박스 선택 중에는 단축키가 비활성화돼요"}
          </p>
        </>
      )}

      {/* 일괄 액션 바 — batch 활성 + 선택 있을 때만 */}
      {batchEnabled && (
        <FloatingActionBar
          selectedCount={checkedIds.size}
          isPending={isBulkPending}
          isRevertPending={isRevertPending}
          onApprove={() => openBulkDialog("approve")}
          onExclude={() => openBulkDialog("exclude")}
          onClear={clearAll}
        />
      )}

      {/* 일괄 확인 다이얼로그 */}
      {bulkDialog && (
        <BulkApproveDialog
          open={bulkDialog !== null}
          onOpenChange={(open) => {
            if (!open && !isBulkPending) setBulkDialog(null);
          }}
          items={bulkDialog.items}
          action={bulkDialog.action}
          isPending={isBulkPending}
          onConfirm={confirmBulk}
        />
      )}
    </div>
  );
}
