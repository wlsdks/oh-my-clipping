import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { userFriendlyMessage } from "@/shared/lib/httpError";
import { userKeys } from "@/queries/userKeys";
import { userService } from "@/services/userService";
import { matchesKoreanSearch } from "@/utils/search";
import type { UserAccountApproval } from "@/types/user";
import { ApprovalSummaryCards } from "./components/ApprovalSummaryCards";
import { ApprovalFilterChips } from "./components/ApprovalFilterChips";
import { ApprovalSearchBar } from "./components/ApprovalSearchBar";
import { BulkActionBar } from "./components/BulkActionBar";
import { useBulkChunkedMutation } from "./hooks/useBulkChunkedMutation";
import { ReviewDialog } from "./components/ReviewDialog";
import type { ReviewDialogState, ReviewMode } from "./components/ReviewDialog";
import { PendingTable } from "./components/PendingTable";
import { RejectedTable } from "./components/RejectedTable";
import { ApprovalEmptyState } from "./components/ApprovalEmptyState";
import { ApprovalPagination } from "./components/ApprovalPagination";

/**
 * 관리자 회원 가입 승인 탭의 최상위 컨테이너다.
 *
 * 책임:
 * - 승인 대기(PENDING) / 반려(REJECTED) 데이터 로딩과 필터 상태 관리
 * - 검색/페이지네이션/선택 상태의 중앙 오케스트레이션
 * - 단건/일괄 승인·반려 mutation 과 ReviewDialog 제어
 *
 * 렌더링은 모두 하위 컴포넌트(`PendingTable`, `RejectedTable`,
 * `ApprovalEmptyState`, `ApprovalPagination` 등)로 위임한다.
 */
type ApprovalFilter = "PENDING" | "REJECTED";

const PAGE_SIZE = 20;

export function ApprovalTab() {
  const qc = useQueryClient();
  const [statusFilter, setStatusFilter] = useState<ApprovalFilter>("PENDING");
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const [search, setSearch] = useState("");
  const [reviewDialog, setReviewDialog] = useState<ReviewDialogState | null>(
    null,
  );
  const [bulkTarget, setBulkTarget] = useState<string[] | null>(null);
  const [singleTarget, setSingleTarget] = useState<string | null>(null);
  const [page, setPage] = useState(0);
  const [selectAllFiltered, setSelectAllFiltered] = useState(false);

  const isPending = statusFilter === "PENDING";

  // PENDING 목록
  const {
    data: pendingAccounts = [],
    isLoading: isPendingLoading,
    isError: isPendingError,
    refetch: refetchPending,
  } = useQuery({
    queryKey: userKeys.accounts({ status: "PENDING" }),
    queryFn: () => userService.listAdminUserAccounts("PENDING"),
  });

  // REJECTED 목록
  const {
    data: rejectedAccounts = [],
    isLoading: isRejectedLoading,
    isError: isRejectedError,
    refetch: refetchRejected,
  } = useQuery({
    queryKey: userKeys.accounts({ status: "REJECTED" }),
    queryFn: () => userService.listAdminUserAccounts("REJECTED"),
  });

  // 요약 데이터 (카드 표시용)
  const { data: summary, isLoading: isSummaryLoading } = useQuery({
    queryKey: userKeys.accountSummary(),
    queryFn: () => userService.getUserAccountSummary(),
  });

  // 현재 필터에 따른 데이터 선택
  const currentAccounts = isPending ? pendingAccounts : rejectedAccounts;
  const isLoading = isPending ? isPendingLoading : isRejectedLoading;
  const isError = isPending ? isPendingError : isRejectedError;
  const refetch = isPending ? refetchPending : refetchRejected;

  // 검색 필터 적용
  const filteredAccounts = search
    ? currentAccounts.filter(
        (item) =>
          matchesKoreanSearch(item.username, search) ||
          matchesKoreanSearch(item.displayName ?? "", search) ||
          matchesKoreanSearch(item.department ?? "", search),
      )
    : currentAccounts;

  // 페이지네이션 계산
  const totalPages = Math.max(1, Math.ceil(filteredAccounts.length / PAGE_SIZE));
  const safePage = Math.min(page, totalPages - 1);
  const pageItems = filteredAccounts.slice(
    safePage * PAGE_SIZE,
    (safePage + 1) * PAGE_SIZE,
  );

  // 요약 카드 로딩 상태
  const summaryLoading = isPendingLoading || isRejectedLoading || isSummaryLoading;

  // ── 단건 승인 ──
  const { mutate: approveAccount, isPending: isApproving } = useMutation({
    mutationFn: ({ id, note }: { id: string; note: string | null }) =>
      userService.approveAdminUserAccount(id, { reviewNote: note }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: userKeys.all });
      setReviewDialog(null);
      toast.success("회원가입을 승인했어요");
    },
    onError: (err) =>
      toast.error(userFriendlyMessage(err, "승인하지 못했어요")),
  });

  // ── 단건 반려 ──
  const { mutate: rejectAccount, isPending: isRejecting } = useMutation({
    mutationFn: ({ id, note }: { id: string; note: string }) =>
      userService.rejectAdminUserAccount(id, { reviewNote: note }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: userKeys.all });
      setReviewDialog(null);
      toast.success("회원가입을 반려했어요");
    },
    onError: (err) =>
      toast.error(userFriendlyMessage(err, "반려하지 못했어요")),
  });

  // ── 일괄 승인 (자동 청크 분할) ──
  const bulkApproveChunked = useBulkChunkedMutation({
    mutationFn: (ids, note) =>
      userService.bulkApproveAdminUserAccounts({ ids, reviewNote: note }),
    onComplete: (result) => {
      setReviewDialog(null);
      setBulkTarget(null);
      setSelectedIds((prev) => {
        const next = new Set(prev);
        for (const id of result.succeeded) next.delete(id);
        return next;
      });
      setSelectAllFiltered(false);
      const alreadyProcessed = result.failed.filter((f) => f.code === "ALREADY_PROCESSED").length;
      const realErrors = result.failed.filter((f) => f.code !== "ALREADY_PROCESSED").length;
      if (realErrors > 0) {
        toast.success(`${result.succeeded.length}건 승인${alreadyProcessed > 0 ? `, ${alreadyProcessed}건 이미 처리됨` : ""}, ${realErrors}건 오류`);
      } else if (alreadyProcessed > 0) {
        toast.success(`${result.succeeded.length}건 승인, ${alreadyProcessed}건은 이미 다른 관리자가 처리함`);
      } else {
        toast.success(`${result.succeeded.length}건을 승인했어요.`);
      }
    },
    onPartialError: (partial) => {
      setReviewDialog(null);
      setBulkTarget(null);
      setSelectedIds((prev) => {
        const next = new Set(prev);
        for (const id of partial.succeeded) next.delete(id);
        return next;
      });
      toast.error(`${partial.succeeded.length}건 승인 완료, 네트워크 오류로 나머지 미처리`);
    },
  });

  // ── 일괄 반려 (자동 청크 분할) ──
  const bulkRejectChunked = useBulkChunkedMutation({
    mutationFn: (ids, note) =>
      userService.bulkRejectAdminUserAccounts({ ids, reviewNote: note }),
    onComplete: (result) => {
      setReviewDialog(null);
      setBulkTarget(null);
      setSelectedIds((prev) => {
        const next = new Set(prev);
        for (const id of result.succeeded) next.delete(id);
        return next;
      });
      setSelectAllFiltered(false);
      const alreadyProcessed = result.failed.filter((f) => f.code === "ALREADY_PROCESSED").length;
      const realErrors = result.failed.filter((f) => f.code !== "ALREADY_PROCESSED").length;
      if (realErrors > 0) {
        toast.success(`${result.succeeded.length}건 반려${alreadyProcessed > 0 ? `, ${alreadyProcessed}건 이미 처리됨` : ""}, ${realErrors}건 오류`);
      } else if (alreadyProcessed > 0) {
        toast.success(`${result.succeeded.length}건 반려, ${alreadyProcessed}건은 이미 다른 관리자가 처리함`);
      } else {
        toast.success(`${result.succeeded.length}건을 반려했어요.`);
      }
    },
    onPartialError: (partial) => {
      setReviewDialog(null);
      setBulkTarget(null);
      setSelectedIds((prev) => {
        const next = new Set(prev);
        for (const id of partial.succeeded) next.delete(id);
        return next;
      });
      toast.error(`${partial.succeeded.length}건 반려 완료, 네트워크 오류로 나머지 미처리`);
    },
  });

  const isWorking =
    isApproving || isRejecting ||
    bulkApproveChunked.isProcessing || bulkRejectChunked.isProcessing;

  // ── 체크박스 토글 ──
  function toggleSelect(id: string) {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }

  function toggleSelectAll() {
    const pageIds = pageItems.map((a) => a.id);
    const allPageSelected = pageIds.length > 0 && pageIds.every((id) => selectedIds.has(id));
    if (allPageSelected) {
      setSelectedIds((prev) => {
        const next = new Set(prev);
        for (const id of pageIds) next.delete(id);
        return next;
      });
      setSelectAllFiltered(false);
    } else {
      setSelectedIds((prev) => {
        const next = new Set(prev);
        for (const id of pageIds) next.add(id);
        return next;
      });
    }
  }

  function handleSelectAllFiltered() {
    setSelectedIds(new Set(filteredAccounts.map((a) => a.id)));
    setSelectAllFiltered(true);
  }

  // ── 단건 다이얼로그 ──
  function openSingleReview(mode: ReviewMode, item: UserAccountApproval) {
    setBulkTarget(null);
    setSingleTarget(item.id);
    setReviewDialog({ mode, note: "", error: null });
  }

  // ── 일괄 다이얼로그 ──
  function openBulkReview(mode: ReviewMode) {
    const ids = Array.from(selectedIds);
    if (ids.length === 0) return;
    setBulkTarget(ids);
    setSingleTarget(null);
    setReviewDialog({ mode, note: "", error: null });
  }

  function submitReview() {
    if (!reviewDialog) return;
    const note = reviewDialog.note.trim();

    // 반려 시 사유 필수
    if (reviewDialog.mode === "reject" && note.length === 0) {
      setReviewDialog((prev) =>
        prev ? { ...prev, error: "반려 사유를 입력해주세요." } : prev,
      );
      return;
    }

    // 일괄 처리 (자동 청크 분할)
    if (bulkTarget && bulkTarget.length > 0) {
      if (reviewDialog.mode === "approve") {
        bulkApproveChunked.execute(bulkTarget, note || null);
      } else {
        bulkRejectChunked.execute(bulkTarget, note);
      }
      return;
    }

    // 단건 처리
    if (singleTarget) {
      if (reviewDialog.mode === "approve") {
        approveAccount({ id: singleTarget, note: note || null });
      } else {
        rejectAccount({ id: singleTarget, note });
      }
    }
  }

  function handleNoteChange(note: string) {
    setReviewDialog((prev) =>
      prev ? { ...prev, note, error: null } : prev,
    );
  }

  function handleCloseDialog() {
    setReviewDialog(null);
    setBulkTarget(null);
    setSingleTarget(null);
  }

  // 필터 변경 시 선택/검색 초기화
  function handleFilterChange(value: ApprovalFilter) {
    setStatusFilter(value);
    setSelectedIds(new Set());
    setSelectAllFiltered(false);
    setSearch("");
    setPage(0);
  }

  function handleSearchChange(value: string) {
    setSearch(value);
    setPage(0);
    setSelectAllFiltered(false);
  }

  // 요약 카드 클릭과 칩 필터 동기화
  function handleCardClick(card: "PENDING" | "REJECTED" | null) {
    if (card) {
      handleFilterChange(card);
    }
  }

  // 페이지 이동 시 선택/전체선택 상태 초기화
  function handlePrevPage() {
    setPage((p) => Math.max(0, p - 1));
    setSelectedIds(new Set());
    setSelectAllFiltered(false);
  }

  function handleNextPage() {
    setPage((p) => Math.min(totalPages - 1, p + 1));
    setSelectedIds(new Set());
    setSelectAllFiltered(false);
  }

  // 현재 페이지가 모두 선택됐는지 판정 (페이지 초과 선택 힌트 노출 조건)
  const pageFullySelected =
    selectedIds.size > 0 &&
    pageItems.length > 0 &&
    pageItems.every((a) => selectedIds.has(a.id));

  // 필터 전체 선택 힌트를 보여줄지 결정 (페이지 외 잔여가 있을 때만)
  const showSelectAllHint =
    isPending &&
    pageFullySelected &&
    !selectAllFiltered &&
    filteredAccounts.length > pageItems.length;

  return (
    <div className="space-y-4">
      {/* 요약 카드 */}
      <ApprovalSummaryCards
        pendingCount={summary?.pendingCount ?? pendingAccounts.length}
        rejectedCount={summary?.rejectedCount ?? rejectedAccounts.length}
        weeklyProcessedCount={summary?.weeklyProcessedCount ?? 0}
        activeCard={statusFilter}
        onCardClick={handleCardClick}
        isLoading={summaryLoading}
      />

      {/* 칩 필터 + 검색바 */}
      <div className="flex items-center justify-between">
        <ApprovalFilterChips
          value={statusFilter}
          onChange={handleFilterChange}
        />
        <ApprovalSearchBar value={search} onChange={handleSearchChange} />
      </div>

      {/* 일괄 처리 바 (PENDING만) */}
      {isPending && (
        <BulkActionBar
          selectedCount={selectedIds.size}
          onBulkApprove={() => openBulkReview("approve")}
          onBulkReject={() => openBulkReview("reject")}
          isWorking={isWorking}
          progress={bulkApproveChunked.progress ?? bulkRejectChunked.progress}
        />
      )}

      {/* 테이블 영역 */}
      {isLoading ? (
        <div
          className="space-y-2"
          role="status"
          aria-live="polite"
          aria-label="회원 목록 불러오는 중"
        >
          {Array.from({ length: 3 }).map((_, i) => (
            <div key={i} className="h-12 bg-muted animate-pulse rounded-lg" />
          ))}
        </div>
      ) : isError ? (
        <div
          className="py-8 text-center space-y-3"
          role="alert"
        >
          <p className="text-sm text-destructive">
            데이터를 불러오지 못했어요
          </p>
          <button
            type="button"
            onClick={() => refetch()}
            className="text-sm text-primary hover:underline"
          >
            다시 시도
          </button>
        </div>
      ) : filteredAccounts.length === 0 ? (
        <ApprovalEmptyState
          search={search}
          isPending={isPending}
          onClearSearch={() => setSearch("")}
        />
      ) : isPending ? (
        <>
          {showSelectAllHint && (
            <div
              className="rounded-lg border bg-primary/5 px-4 py-2 text-sm text-center"
              role="status"
              aria-live="polite"
            >
              이 페이지 {pageItems.length}건 선택됨 ·{" "}
              <button
                type="button"
                onClick={handleSelectAllFiltered}
                className="text-primary font-medium hover:underline"
                aria-label={`필터된 전체 ${filteredAccounts.length}건 모두 선택`}
              >
                필터된 전체 {filteredAccounts.length}건 모두 선택
              </button>
            </div>
          )}
          <PendingTable
            accounts={pageItems}
            selectedIds={selectedIds}
            onToggleSelect={toggleSelect}
            onToggleSelectAll={toggleSelectAll}
            onReview={openSingleReview}
            isWorking={isWorking}
          />
        </>
      ) : (
        <RejectedTable
          accounts={pageItems}
          onReapprove={(item) => openSingleReview("approve", item)}
          isWorking={isWorking}
        />
      )}

      {/* 페이지네이션 */}
      {!isLoading && !isError && filteredAccounts.length > 0 && (
        <ApprovalPagination
          search={search}
          filteredCount={filteredAccounts.length}
          totalCount={currentAccounts.length}
          currentPage={safePage}
          totalPages={totalPages}
          onPrev={handlePrevPage}
          onNext={handleNextPage}
        />
      )}

      {/* 리뷰 다이얼로그 */}
      <ReviewDialog
        state={reviewDialog}
        onClose={handleCloseDialog}
        onNoteChange={handleNoteChange}
        onSubmit={submitReview}
        isWorking={isWorking}
        bulkCount={bulkTarget ? bulkTarget.length : undefined}
      />
    </div>
  );
}
