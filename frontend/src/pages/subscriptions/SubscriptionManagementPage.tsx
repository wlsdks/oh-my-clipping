import { useState, useEffect } from "react";
import { useSearchParams } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { Search, X } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { ConfirmModal } from "@/components/shared/ConfirmModal";
import { useDebounce } from "@/hooks/useDebounce";
import { useMediaQuery } from "@/hooks/useMediaQuery";
import { matchesKoreanSearch } from "@/utils/search";
import { userService, type ApproveClippingRequestData } from "@/services/userService";
import { categoryService } from "@/services/categoryService";
import { ruleService } from "@/services/ruleService";
import { userKeys } from "@/queries/userKeys";
import { categoryKeys } from "@/queries/categoryKeys";
import { ruleKeys } from "@/queries/ruleKeys";
import { useSubscriptionMutations } from "./useSubscriptionMutations";
import { useKeyboardNavigation } from "./useKeyboardNavigation";

import { getCategoryStatus } from "./model/constants";
import { sortCategories, type SortState } from "./model/sorting";
import type { SubscriptionFilter, SubscriptionPanelItem } from "./model/types";
import type { OperationPanelTab } from "./OperationSidePanel";
import { SubscriptionFilterChips } from "./SubscriptionFilterChips";
import { PendingRequestsTable } from "./PendingRequestsTable";
import { ActiveSubscriptionsTable } from "./ActiveSubscriptionsTable";
import { SubscriptionSidePanel } from "./SubscriptionSidePanel";
import { BulkActionsBar } from "./BulkActionsBar";

/** 요청 기반 필터 (PendingRequestsTable 사용) */
const REQUEST_FILTERS: SubscriptionFilter[] = ["pending", "rejected", "withdrawn"];

/** 카테고리 기반 필터 (ActiveSubscriptionsTable 사용) */
const CATEGORY_FILTERS: SubscriptionFilter[] = [
  "active", "warning", "danger", "inactive", "public", "private",
];

/** 카테고리 상태를 SubscriptionFilter 값으로 매핑 */
function categoryToFilter(c: Parameters<typeof getCategoryStatus>[0]): SubscriptionFilter {
  const status = getCategoryStatus(c);
  switch (status.type) {
    case "success":
      return "active";
    case "warning":
      return "warning";
    case "danger":
      return "danger";
    case "neutral":
      return "inactive";
  }
}

export function SubscriptionManagementPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const isXl = useMediaQuery("(min-width: 1280px)");

  // ── URL 상태 ──
  const initialFilter = (searchParams.get("filter") as SubscriptionFilter) || "pending";
  const initialSelected = searchParams.get("selected") || null;
  const initialTab = (searchParams.get("tab") as OperationPanelTab) || "config";

  // ── UI 상태 ──
  const [filter, setFilter] = useState<SubscriptionFilter>(
    [...REQUEST_FILTERS, ...CATEGORY_FILTERS].includes(initialFilter) ? initialFilter : "pending"
  );
  const [search, setSearch] = useState("");
  const debouncedSearch = useDebounce(search, 300);
  const [panel, setPanel] = useState<SubscriptionPanelItem | null>(null);
  const [operationTab, setOperationTab] = useState<OperationPanelTab>(initialTab);

  // 정렬 상태
  const [sort, setSort] = useState<SortState>({ field: "name", direction: "asc" });

  // 체크박스 선택 상태
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());

  // 벌크 승인 확인 모달 상태
  const [bulkApproveConfirmOpen, setBulkApproveConfirmOpen] = useState(false);

  // 키보드 포커스 인덱스
  const [focusedIndex, setFocusedIndex] = useState(-1);

  // ── 데이터 페칭 ──

  const pendingQuery = useQuery({
    queryKey: [...userKeys.clippingRequests(), { status: "PENDING" }],
    queryFn: () => userService.listAdminClippingRequests("PENDING"),
  });

  const rejectedQuery = useQuery({
    queryKey: [...userKeys.clippingRequests(), { status: "REJECTED" }],
    queryFn: () => userService.listAdminClippingRequests("REJECTED"),
    staleTime: 60_000,
  });

  const withdrawnQuery = useQuery({
    queryKey: [...userKeys.clippingRequests(), { status: "WITHDRAWN" }],
    queryFn: () => userService.listAdminClippingRequests("WITHDRAWN"),
    staleTime: 60_000,
  });

  const categoriesQuery = useQuery({
    queryKey: categoryKeys.list({ size: 500 }),
    queryFn: () => categoryService.getPage(new URLSearchParams({ size: "500" })),
    select: (page) => page.content,
  });

  const allCategories = categoriesQuery.data ?? [];

  // 키워드 규칙 통계 (키워드 없음 뱃지 계산용)
  const ruleStatsQuery = useQuery({
    queryKey: ruleKeys.stats(),
    queryFn: () => ruleService.getRuleStats(),
    staleTime: 60_000,
  });

  // 키워드가 없는 카테고리 ID Set
  const noRuleCategoryIds = new Set<string>(
    (ruleStatsQuery.data?.perCategory ?? [])
      .filter((s) => !s.hasRule || (s.included === 0 && s.review === 0 && s.excluded === 0))
      .map((s) => s.categoryId)
  );

  // ── 칩 카운트 계산 ──
  const counts: Record<SubscriptionFilter, number> = {
    pending: pendingQuery.data?.length ?? 0,
    active: 0,
    warning: 0,
    danger: 0,
    inactive: 0,
    public: 0,
    private: 0,
    rejected: rejectedQuery.data?.length ?? 0,
    withdrawn: withdrawnQuery.data?.length ?? 0,
  };

  for (const cat of allCategories) {
    const mapped = categoryToFilter(cat);
    counts[mapped]++;
    // 공개/비공개 카운트는 상태와 독립적으로 집계한다.
    if (cat.isPublic) {
      counts.public++;
    } else {
      counts.private++;
    }
  }

  // ── 검색 필터링 ──
  function searchFilter(name: string): boolean {
    if (!debouncedSearch.trim()) return true;
    return matchesKoreanSearch(name, debouncedSearch);
  }

  function currentRequests() {
    const map: Record<string, typeof pendingQuery> = {
      pending: pendingQuery,
      rejected: rejectedQuery,
      withdrawn: withdrawnQuery,
    };
    const query = map[filter];
    if (!query) return [];
    return (query.data ?? []).filter((r) => searchFilter(r.requestName));
  }

  function currentCategories() {
    if (!CATEGORY_FILTERS.includes(filter)) return [];
    let filtered: typeof allCategories;
    // 공개/비공개 필터는 isPublic 속성으로 직접 필터링한다.
    if (filter === "public") {
      filtered = allCategories.filter((cat) => cat.isPublic);
    } else if (filter === "private") {
      filtered = allCategories.filter((cat) => !cat.isPublic);
    } else {
      filtered = allCategories.filter((cat) => categoryToFilter(cat) === filter);
    }
    filtered = filtered.filter((cat) => searchFilter(cat.name));
    return sortCategories(filtered, sort);
  }

  const isRequestFilter = REQUEST_FILTERS.includes(filter);
  const displayedCategories = isRequestFilter ? [] : currentCategories();

  // ── URL 동기화 ──
  function updateUrl(params: Record<string, string | null>) {
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev);
      for (const [key, value] of Object.entries(params)) {
        if (value === null) {
          next.delete(key);
        } else {
          next.set(key, value);
        }
      }
      return next;
    }, { replace: true });
  }

  // 초기 URL에서 선택된 아이템 복원
  useEffect(() => {
    if (initialSelected && allCategories.length > 0 && !panel) {
      const found = allCategories.find((c) => c.id === initialSelected);
      if (found) {
        setPanel({ kind: "category", data: found });
      }
    }
    // 최초 한 번만 실행
  }, [allCategories.length]);

  function handleSelectPanel(item: SubscriptionPanelItem) {
    setPanel(item);
    const id = item.kind === "category" ? item.data.id : item.data.id;
    updateUrl({ selected: id, tab: "config" });
    setOperationTab("config");
  }

  function handleClosePanel() {
    setPanel(null);
    updateUrl({ selected: null, tab: null });
  }

  function handleOperationTabChange(tab: OperationPanelTab) {
    setOperationTab(tab);
    updateUrl({ tab });
  }

  function handleFilterChange(newFilter: SubscriptionFilter) {
    setFilter(newFilter);
    setSelectedIds(new Set());
    setFocusedIndex(-1);
    updateUrl({ filter: newFilter, selected: null, tab: null });
    setPanel(null);
  }

  // ── 체크박스 핸들러 ──
  function handleToggleSelect(id: string) {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
  }

  function handleToggleSelectAll() {
    if (isRequestFilter) {
      // 요청 필터에서는 현재 표시된 요청 기준으로 전체 선택/해제
      const displayed = currentRequests();
      if (displayed.every((r) => selectedIds.has(r.id))) {
        setSelectedIds(new Set());
      } else {
        setSelectedIds(new Set(displayed.map((r) => r.id)));
      }
    } else {
      if (displayedCategories.every((c) => selectedIds.has(c.id))) {
        setSelectedIds(new Set());
      } else {
        setSelectedIds(new Set(displayedCategories.map((c) => c.id)));
      }
    }
  }

  // ── 키보드 네비게이션 ──
  const displayedItems = isRequestFilter ? currentRequests() : displayedCategories;

  useKeyboardNavigation({
    isRequestFilter,
    displayedItems,
    focusedIndex,
    isPanelOpen: panel !== null,
    onFocusChange: setFocusedIndex,
    onSelectItem: (index) => {
      if (isRequestFilter) {
        const requests = currentRequests();
        handleSelectPanel({ kind: "request", data: requests[index] });
      } else {
        handleSelectPanel({ kind: "category", data: displayedCategories[index] });
      }
    },
    onClosePanel: handleClosePanel,
    onToggleSelect: !isRequestFilter ? handleToggleSelect : undefined,
  });

  // ── 뮤테이션 ──
  const {
    approveMutation,
    rejectMutation,
    editCategoryMutation,
    pauseMutation,
    resumeMutation,
    togglePublicMutation,
    deleteCategoryMutation,
    bulkToggleMutation,
    bulkApproveMutation,
    bulkApproveProgress,
    isCategoryWorking,
  } = useSubscriptionMutations({
    onClose: handleClosePanel,
    setSelectedIds,
  });

  // ── 이벤트 핸들러 ──

  function handleApprove(id: string, data: ApproveClippingRequestData) {
    approveMutation.mutate({ id, data });
  }

  function handleReject(id: string, note: string = "") {
    rejectMutation.mutate({ id, note });
  }

  function handleBulkActivate() {
    const ids = Array.from(selectedIds);
    if (ids.length > 0) {
      bulkToggleMutation.mutate({ ids, isActive: true });
    }
  }

  function handleBulkDeactivate() {
    const ids = Array.from(selectedIds);
    if (ids.length > 0) {
      bulkToggleMutation.mutate({ ids, isActive: false });
    }
  }

  function handleBulkApprove() {
    const ids = Array.from(selectedIds);
    if (ids.length === 0) return;
    setBulkApproveConfirmOpen(true);
  }

  function handleBulkApproveConfirm() {
    const ids = Array.from(selectedIds);
    bulkApproveMutation.mutate(ids);
  }

  /**
   * 현재 오픈된 카테고리 패널의 updated_at을 반환한다.
   * 낙관적 잠금(409 STALE_EDIT) 감지를 위해 편집 요청에 함께 보낸다.
   * request 패널이거나 패널이 없으면 null.
   */
  function currentCategoryUpdatedAt(): string | null {
    if (!panel || panel.kind !== "category") return null;
    return panel.data.updatedAt ?? null;
  }

  // ── 요약 라인 ──
  const totalSubscriptions = allCategories.length;
  const activeSubscriptions = counts.active;

  return (
    <div className="p-4 sm:p-6 space-y-6">
      {/* 헤더 */}
      <div>
        <h1 className="text-2xl font-bold tracking-tight">구독 관리</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          총 {totalSubscriptions}개 구독 &middot; {activeSubscriptions}개 활성
          {counts.warning > 0 && (
            <>
              {" "}&middot;{" "}
              <button
                type="button"
                className="text-[var(--status-warning-text)] hover:underline font-medium"
                onClick={() => handleFilterChange("warning")}
              >
                {counts.warning}개 주의
              </button>
            </>
          )}
          {counts.danger > 0 && (
            <>
              {" "}&middot;{" "}
              <button
                type="button"
                className="text-[var(--status-danger-text)] hover:underline font-medium"
                onClick={() => handleFilterChange("danger")}
              >
                {counts.danger}개 오류
              </button>
            </>
          )}
        </p>
      </div>

      {/* 칩 필터 + 검색 */}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <SubscriptionFilterChips
          selected={filter}
          counts={counts}
          onSelect={handleFilterChange}
        />

        <div className="relative w-full sm:w-64">
          <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            placeholder="이름으로 검색"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="pl-9"
          />
        </div>
      </div>

      {/* 테이블 + 인라인 패널 레이아웃 */}
      <div className="flex gap-0">
        <div className="flex-1 min-w-0">
          {isRequestFilter ? (
            <PendingRequestsTable
              requests={currentRequests()}
              filter={filter}
              onSelect={handleSelectPanel}
              selectedIds={selectedIds}
              onToggleSelect={handleToggleSelect}
              onToggleSelectAll={handleToggleSelectAll}
            />
          ) : (
            <ActiveSubscriptionsTable
              categories={displayedCategories}
              onSelect={handleSelectPanel}
              sort={sort}
              onSortChange={setSort}
              selectedIds={selectedIds}
              onToggleSelect={handleToggleSelect}
              onToggleSelectAll={handleToggleSelectAll}
              focusedIndex={focusedIndex}
              noRuleCategoryIds={noRuleCategoryIds}
            />
          )}
        </div>

        {/* xl: 인라인 패널 */}
        {isXl && (
          <SubscriptionSidePanel
            item={panel}
            onClose={handleClosePanel}
            onApprove={handleApprove}
            onReject={handleReject}
            isApproving={approveMutation.isPending || rejectMutation.isPending}
            onEdit={(id, data) =>
              editCategoryMutation.mutate({ id, data, expectedUpdatedAt: currentCategoryUpdatedAt() })
            }
            onPause={(id) => pauseMutation.mutate(id)}
            onResume={(id) => resumeMutation.mutate(id)}
            onTogglePublic={(id, isPublic) =>
              togglePublicMutation.mutate({ id, isPublic, expectedUpdatedAt: currentCategoryUpdatedAt() })
            }
            onDelete={(id) => deleteCategoryMutation.mutate(id)}
            isCategoryWorking={isCategoryWorking}
            isInline={true}
            operationTab={operationTab}
            onOperationTabChange={handleOperationTabChange}
          />
        )}
      </div>

      {/* < xl: Sheet 패널 */}
      {!isXl && (
        <SubscriptionSidePanel
          item={panel}
          onClose={handleClosePanel}
          onApprove={handleApprove}
          onReject={handleReject}
          isApproving={approveMutation.isPending || rejectMutation.isPending}
          onEdit={(id, data) => editCategoryMutation.mutate({ id, data })}
          onPause={(id) => pauseMutation.mutate(id)}
          onResume={(id) => resumeMutation.mutate(id)}
          onTogglePublic={(id, isPublic) => togglePublicMutation.mutate({ id, isPublic })}
          onDelete={(id) => deleteCategoryMutation.mutate(id)}
          isCategoryWorking={isCategoryWorking}
          isInline={false}
          operationTab={operationTab}
          onOperationTabChange={handleOperationTabChange}
        />
      )}

      {/* 벌크 액션 바 — 카테고리 탭 */}
      {!isRequestFilter && (
        <BulkActionsBar
          selectedCount={selectedIds.size}
          onActivate={handleBulkActivate}
          onDeactivate={handleBulkDeactivate}
          onClear={() => setSelectedIds(new Set())}
          isWorking={bulkToggleMutation.isPending}
        />
      )}

      {/* 벌크 승인 바 — pending 요청 탭 */}
      {filter === "pending" && selectedIds.size > 0 && (
        <div className="sticky bottom-4 z-20 mx-auto max-w-xl">
          <div className="flex items-center gap-3 rounded-xl border bg-card px-4 py-3 shadow-lg">
            <span className="text-sm font-medium text-foreground whitespace-nowrap">
              {bulkApproveProgress
                ? `${bulkApproveProgress.done}/${bulkApproveProgress.total} 승인 완료...`
                : `${selectedIds.size}개 선택`}
            </span>
            <div className="flex items-center gap-2 ml-auto">
              <Button
                size="sm"
                disabled={bulkApproveMutation.isPending}
                onClick={handleBulkApprove}
              >
                일괄 승인
              </Button>
              <Button
                size="sm"
                variant="ghost"
                onClick={() => setSelectedIds(new Set())}
                className="h-8 w-8 p-0"
              >
                <X className="h-4 w-4" />
                <span className="sr-only">선택 해제</span>
              </Button>
            </div>
          </div>
        </div>
      )}

      {/* 벌크 승인 확인 모달 */}
      <ConfirmModal
        open={bulkApproveConfirmOpen}
        onOpenChange={setBulkApproveConfirmOpen}
        title={`${selectedIds.size}건의 요청을 승인할까요?`}
        description="기본 저작권 정책(인용만 허용)이 적용돼요"
        confirmLabel="승인"
        onConfirm={handleBulkApproveConfirm}
      />
    </div>
  );
}
