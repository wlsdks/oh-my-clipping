// frontend/src/pages/user-accounts/MembersTab.tsx
import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { userFriendlyMessage } from "@/shared/lib/httpError";
import { userKeys } from "@/queries/userKeys";
import { userService } from "@/services/userService";
import { formatRelativeDate } from "@/utils/date";
import { matchesKoreanSearch } from "@/utils/search";
import type { UserAccountApproval } from "@/types/user";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from "@/components/ui/dialog";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { EmptyState } from "@/components/shared/EmptyState";
import { ArrowUpDown, ChevronLeft, ChevronRight, Copy, Check } from "lucide-react";
import { MemberMetricCards } from "./components/MemberMetricCards";
import { MemberFilterBar } from "./components/MemberFilterBar";
import { WithdrawDialog } from "./components/WithdrawDialog";
import type { WithdrawDialogState } from "./components/WithdrawDialog";
import {
  DEFAULT_FILTERS,
  matchesFilters,
  getActivityStatus,
  getActivityDotClass,
  extractDepartments,
  isDefaultFilters,
  type MemberFilters,
} from "./components/memberFilters";

interface ResetPasswordDialogState {
  userId: string;
  username: string;
  tempPassword: string | null;
  slackDmSent: boolean;
}

type SortField = "createdAt" | "lastLoginAt" | "subscriptionCount";
type SortDir = "asc" | "desc";

const PAGE_SIZE = 20;


function matchesMember(item: UserAccountApproval, query: string): boolean {
  if (!query) return true;
  const targets = [item.username, item.displayName ?? "", item.department ?? ""];
  return targets.some((t) => matchesKoreanSearch(t, query));
}

interface MembersTabProps {
  /** URL로부터 전달된 페르소나 필터. 지정 시 해당 페르소나 구독자만 조회한다. */
  personaIdFilter?: string;
}

export function MembersTab({ personaIdFilter }: MembersTabProps = {}) {
  const qc = useQueryClient();
  const [search, setSearch] = useState("");
  const [filters, setFilters] = useState<MemberFilters>(DEFAULT_FILTERS);
  const [activeCardKey, setActiveCardKey] = useState<string | null>(null);
  const [sortField, setSortField] = useState<SortField>("createdAt");
  const [sortDir, setSortDir] = useState<SortDir>("desc");
  const [page, setPage] = useState(0);
  const [withdrawDialog, setWithdrawDialog] = useState<WithdrawDialogState | null>(null);
  const [resetDialog, setResetDialog] = useState<ResetPasswordDialogState | null>(null);
  const [copied, setCopied] = useState(false);

  const { data: accounts = [], isLoading, isError, refetch } = useQuery({
    queryKey: userKeys.accounts({ status: "APPROVED", personaId: personaIdFilter }),
    queryFn: () =>
      userService.listAdminUserAccounts(
        "APPROVED",
        personaIdFilter ? { personaId: personaIdFilter } : undefined
      ),
  });

  const { mutate: withdrawAccount, isPending: isWithdrawing } = useMutation({
    mutationFn: ({ id, note }: { id: string; note: string | null }) =>
      userService.withdrawAdminUserAccount(id, { reviewNote: note }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: userKeys.all });
      setWithdrawDialog(null);
      toast.success("탈퇴가 완료됐어요");
    },
    onError: (err) => toast.error(userFriendlyMessage(err, "탈퇴에 실패했어요")),
  });

  const { mutate: resetPassword, isPending: isResetting } = useMutation({
    mutationFn: (id: string) => userService.resetAdminUserPassword(id),
    onSuccess: (data, id) => {
      const item = accounts.find((a) => a.id === id);
      setResetDialog({ userId: id, username: item?.username ?? id, tempPassword: data.tempPassword, slackDmSent: data.slackDmSent });
      setCopied(false);
    },
    onError: (err) => toast.error(userFriendlyMessage(err, "비밀번호 초기화에 실패했어요")),
  });

  // 활성 회원만 표시
  const activeMembers = accounts.filter((a) => a.isActive);

  // 부서 목록 추출 (지표 카드는 전체 데이터 기준)
  const departments = extractDepartments(activeMembers);
  const hasNoDept = activeMembers.some((m) => !m.department);

  // 필터 + 검색 적용
  const filtered = activeMembers
    .filter((a) => matchesFilters(a, filters))
    .filter((a) => matchesMember(a, search));

  // 정렬
  const sorted = [...filtered].sort((a, b) => {
    let cmp = 0;
    if (sortField === "createdAt") {
      cmp = new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime();
    } else if (sortField === "lastLoginAt") {
      const aTime = a.lastLoginAt ? new Date(a.lastLoginAt).getTime() : 0;
      const bTime = b.lastLoginAt ? new Date(b.lastLoginAt).getTime() : 0;
      cmp = aTime - bTime;
    } else if (sortField === "subscriptionCount") {
      cmp = (a.subscriptionCount ?? 0) - (b.subscriptionCount ?? 0);
    }
    return sortDir === "asc" ? cmp : -cmp;
  });

  // 페이지네이션
  const totalPages = Math.max(1, Math.ceil(sorted.length / PAGE_SIZE));
  const safePage = Math.min(page, totalPages - 1);
  const pageItems = sorted.slice(safePage * PAGE_SIZE, (safePage + 1) * PAGE_SIZE);

  // 필터 또는 검색이 활성 상태인지
  const hasActiveFilters = !isDefaultFilters(filters) || search !== "";

  function toggleSort(field: SortField) {
    if (sortField === field) {
      setSortDir((prev) => (prev === "asc" ? "desc" : "asc"));
    } else {
      setSortField(field);
      setSortDir("desc");
    }
  }

  function handleSearchChange(value: string) {
    setSearch(value);
    setPage(0);
  }

  function handleFiltersChange(newFilters: MemberFilters) {
    setFilters(newFilters);
    setPage(0);
    // 필터가 수동 변경되면 카드 선택 해제
    setActiveCardKey(null);
  }

  // 지표 카드 클릭: 칩 필터를 프리셋으로 세팅 (검색어 유지)
  function handleCardClick(preset: Partial<MemberFilters> | null, key: string | null) {
    if (preset === null || key === null) {
      // 총 회원 카드 또는 이미 선택된 카드 재클릭 → 필터 초기화
      setFilters(DEFAULT_FILTERS);
      setActiveCardKey(null);
    } else {
      setFilters({ ...DEFAULT_FILTERS, ...preset });
      setActiveCardKey(key);
    }
    setPage(0);
  }

  function openWithdrawDialog(item: UserAccountApproval) {
    setWithdrawDialog({ item, note: "", slackCleanupConfirmed: false });
  }

  function submitWithdraw() {
    if (!withdrawDialog) return;
    withdrawAccount({
      id: withdrawDialog.item.id,
      note: withdrawDialog.note.trim() || null,
    });
  }

  const sortIndicator = (field: SortField) =>
    sortField === field ? (sortDir === "asc" ? " ↑" : " ↓") : "";

  return (
    <div className="space-y-4">
      {/* 지표 카드 */}
      <MemberMetricCards
        members={activeMembers}
        activeCardKey={activeCardKey}
        onCardClick={handleCardClick}
        loading={isLoading}
      />

      {/* 필터 바 */}
      <MemberFilterBar
        search={search}
        onSearchChange={handleSearchChange}
        filters={filters}
        onFiltersChange={handleFiltersChange}
        departments={departments}
        hasNoDept={hasNoDept}
      />

      {isLoading ? (
        <div className="space-y-2">
          {Array.from({ length: 3 }).map((_, i) => (
            <div key={i} className="h-12 bg-muted animate-pulse rounded-lg" />
          ))}
        </div>
      ) : isError ? (
        <div className="py-8 text-center space-y-3">
          <p className="text-sm text-destructive">데이터를 불러오지 못했어요</p>
          <button type="button" onClick={() => refetch()} className="text-sm text-primary hover:underline">
            다시 시도
          </button>
        </div>
      ) : activeMembers.length === 0 ? (
        <EmptyState
          title="활성 회원이 없어요"
          description="승인된 회원이 없어요"
        />
      ) : filtered.length === 0 ? (
        <EmptyState
          title="조건에 맞는 회원이 없어요"
          description={search ? `"${search}"에 해당하는 회원이 없어요` : "선택한 필터에 맞는 회원이 없어요"}
          action={
            <Button
              variant="outline"
              size="sm"
              onClick={() => {
                setSearch("");
                setFilters(DEFAULT_FILTERS);
                setActiveCardKey(null);
              }}
            >
              필터 초기화
            </Button>
          }
        />
      ) : (
        <>
          <div className="rounded-md border bg-card overflow-x-auto">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="whitespace-nowrap">아이디</TableHead>
                  <TableHead className="whitespace-nowrap">표시 이름</TableHead>
                  <TableHead className="whitespace-nowrap">부서</TableHead>
                  <TableHead className="whitespace-nowrap">역할</TableHead>
                  <TableHead className="whitespace-nowrap">
                    <button type="button" onClick={() => toggleSort("createdAt")} className="inline-flex items-center gap-1 hover:text-foreground">
                      가입일{sortIndicator("createdAt")}
                      <ArrowUpDown className="h-3 w-3" />
                    </button>
                  </TableHead>
                  <TableHead className="whitespace-nowrap">
                    <button type="button" onClick={() => toggleSort("lastLoginAt")} className="inline-flex items-center gap-1 hover:text-foreground">
                      마지막 로그인{sortIndicator("lastLoginAt")}
                      <ArrowUpDown className="h-3 w-3" />
                    </button>
                  </TableHead>
                  <TableHead className="whitespace-nowrap">
                    <button type="button" onClick={() => toggleSort("subscriptionCount")} className="inline-flex items-center gap-1 hover:text-foreground">
                      구독{sortIndicator("subscriptionCount")}
                      <ArrowUpDown className="h-3 w-3" />
                    </button>
                  </TableHead>
                  <TableHead className="whitespace-nowrap">관리</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {pageItems.map((item) => {
                  const activityStatus = getActivityStatus(item.lastLoginAt);
                  const dotClass = getActivityDotClass(activityStatus);
                  const subCount = item.subscriptionCount ?? 0;

                  return (
                    <TableRow key={item.id} className="hover:bg-muted/50 transition-colors">
                      <TableCell className="font-medium">{item.username}</TableCell>
                      <TableCell>{item.displayName ?? "-"}</TableCell>
                      <TableCell>
                        {item.department ? (
                          <Badge variant="outline" className="text-xs">
                            {item.department}
                          </Badge>
                        ) : (
                          <span className="text-muted-foreground">-</span>
                        )}
                      </TableCell>
                      <TableCell>
                        <Badge variant={item.role === "ADMIN" ? "default" : "secondary"}>
                          {item.role === "ADMIN" ? "관리자" : "사용자"}
                        </Badge>
                      </TableCell>
                      <TableCell className="text-sm text-muted-foreground">
                        {formatRelativeDate(item.createdAt)}
                      </TableCell>
                      <TableCell className="text-sm text-muted-foreground">
                        <span className="inline-flex items-center gap-1.5">
                          <span className={`inline-block h-2 w-2 rounded-full ${dotClass}`} />
                          {formatRelativeDate(item.lastLoginAt)}
                        </span>
                      </TableCell>
                      <TableCell className={`text-sm ${subCount === 0 ? "text-muted-foreground/50" : "text-muted-foreground"}`}>
                        {subCount}건
                      </TableCell>
                      <TableCell>
                        <div className="flex items-center gap-2">
                          <Button
                            size="sm"
                            variant="outline"
                            onClick={() => resetPassword(item.id)}
                            disabled={isResetting || isWithdrawing}
                          >
                            비밀번호 초기화
                          </Button>
                          <Button
                            size="sm"
                            variant="outline"
                            onClick={() => openWithdrawDialog(item)}
                            disabled={isWithdrawing || isResetting}
                            className="text-destructive border-destructive/30 hover:bg-destructive/10"
                          >
                            탈퇴 처리
                          </Button>
                        </div>
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          </div>

          {/* 하단: 결과 요약 + 페이징 */}
          <div className="flex items-center justify-between">
            <span className="text-sm text-muted-foreground">
              {hasActiveFilters
                ? `필터 결과 ${filtered.length}명 / 전체 ${activeMembers.length}명`
                : `총 ${activeMembers.length}명`}
            </span>

            {totalPages > 1 && (
              <div className="flex items-center gap-1">
                <Button variant="outline" size="sm" onClick={() => setPage((p) => Math.max(0, p - 1))} disabled={safePage === 0}>
                  <ChevronLeft className="h-4 w-4" />
                  이전
                </Button>
                <span className="text-sm text-muted-foreground px-1">
                  {safePage + 1} / {totalPages}
                </span>
                <Button variant="outline" size="sm" onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))} disabled={safePage >= totalPages - 1}>
                  다음
                  <ChevronRight className="h-4 w-4" />
                </Button>
              </div>
            )}
          </div>
        </>
      )}

      <WithdrawDialog
        state={withdrawDialog}
        onClose={() => setWithdrawDialog(null)}
        onNoteChange={(note) => setWithdrawDialog((prev) => prev ? { ...prev, note } : prev)}
        onSlackCheckChange={(checked) => setWithdrawDialog((prev) => prev ? { ...prev, slackCleanupConfirmed: checked } : prev)}
        onSubmit={submitWithdraw}
        isWorking={isWithdrawing}
      />

      <Dialog open={resetDialog !== null} onOpenChange={(open) => { if (!open) setResetDialog(null); }}>
        <DialogContent className="max-w-sm">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              임시 비밀번호 발급됨
              {resetDialog?.slackDmSent && (
                <Badge variant="secondary" className="text-xs">Slack DM 전송 완료</Badge>
              )}
            </DialogTitle>
            <DialogDescription>
              <strong>{resetDialog?.username}</strong> 계정의 비밀번호가 초기화됐어요.
              아래 임시 비밀번호를 사용자에게 전달해 주세요.
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-3">
            <div className="flex items-center gap-2 rounded-lg border bg-muted p-3">
              <code className="flex-1 font-mono text-sm select-all">{resetDialog?.tempPassword}</code>
              <button
                type="button"
                onClick={() => {
                  if (resetDialog?.tempPassword) {
                    navigator.clipboard.writeText(resetDialog.tempPassword ?? "");
                    setCopied(true);
                    setTimeout(() => setCopied(false), 2000);
                  }
                }}
                className="shrink-0 text-muted-foreground hover:text-foreground transition-colors"
                aria-label="복사"
              >
                {copied ? <Check className="h-4 w-4 text-[var(--status-success-text)]" /> : <Copy className="h-4 w-4" />}
              </button>
            </div>
            <p className="text-xs text-muted-foreground">
              {resetDialog?.slackDmSent
                ? "임시 비밀번호가 사용자의 Slack DM으로 전송되었습니다."
                : "이 사용자는 Slack DM 채널이 설정되어 있지 않습니다. 임시 비밀번호를 직접 전달해 주세요."}
            </p>
          </div>
          <DialogFooter>
            <Button onClick={() => setResetDialog(null)}>확인</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
