import { useState } from "react";
import { useSearchParams } from "react-router-dom";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { ChevronLeft, ChevronRight } from "lucide-react";
import { userFriendlyMessage } from "@/shared/lib/httpError";
import { useDebounce } from "@/hooks/useDebounce";
import { sourceKeys } from "@/queries/sourceKeys";
import { categoryKeys } from "@/queries/categoryKeys";
import { sourceService } from "@/services/sourceService";
import type { SourceCreateRequest } from "@/types/adminDto";
import type { SourceComplianceStatus } from "@/types/source";
import { categoryService } from "@/services/categoryService";
import { pipelineService } from "@/services/pipelineService";
import { groupSources } from "./model/groupSources";
import { getHealthLevel } from "./sourceHelpers";
import type { HealthLevel } from "./sourceHelpers";
import { SourcesHeader } from "./SourcesHeader";
import { ProblemSourcesSection } from "./ProblemSourcesSection";
import { CollapsibleSourcesTable } from "./CollapsibleSourcesTable";
import { ErrorDetailDrawer } from "./ErrorDetailDrawer";
import { SourceDistributionChart } from "./SourceDistributionChart";
import { CoverageGapBanner } from "./CoverageGapBanner";
import { SourceQualityDashboard } from "./SourceQualityDashboard";
import { SourcesEmptyState } from "./SourcesEmptyState";
import { SourceCreateForm } from "./SourceCreateForm";
import { SourceEditModal } from "@/features/source-edit/SourceEditModal";
import { SourceComplianceModal } from "./SourceComplianceModal";
import { SourceAnalyticsDrawer } from "./SourceAnalyticsDrawer";
import { Button } from "@/components/ui/button";
import { ConfirmModal } from "@/components/shared/ConfirmModal";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import type { Source } from "@/types/source";

const PAGE_SIZE = 50;

export function SourcesPage() {
  const qc = useQueryClient();
  const [searchParams, setSearchParams] = useSearchParams();

  // URL 파라미터에서 categoryId 읽기 (구독 페이지에서 딥링크)
  const urlCategoryId = searchParams.get("categoryId") ?? "";

  const [createOpen, setCreateOpen] = useState(false);
  const [selectedSource, setSelectedSource] = useState<Source | null>(null);
  const [editOpen, setEditOpen] = useState(false);
  const [complianceSource, setComplianceSource] = useState<Source | null>(null);
  const [errorDetailSource, setErrorDetailSource] = useState<Source | null>(null);
  const [pendingIds, setPendingIds] = useState<Set<string>>(new Set());
  const [deleteConfirmId, setDeleteConfirmId] = useState<string | null>(null);
  const [healthFilter, setHealthFilter] = useState<HealthLevel | null>(null);
  const [analyticsSourceId, setAnalyticsSourceId] = useState<string | null>(null);

  // 서버 사이드 필터/검색 상태
  const [query, setQuery] = useState("");
  const [categoryId, setCategoryId] = useState(urlCategoryId);
  const [complianceStatus, setComplianceStatus] = useState<"" | SourceComplianceStatus>("");
  const [page, setPage] = useState(0);

  // 벌크 액션 선택 상태
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());

  const debouncedQuery = useDebounce(query, 300);

  // categoryId 변경 시 URL 파라미터 동기화
  function handleCategoryChange(newCategoryId: string) {
    setCategoryId(newCategoryId);
    setPage(0);
    setSelectedIds(new Set());
    if (newCategoryId) {
      setSearchParams({ categoryId: newCategoryId }, { replace: true });
    } else {
      setSearchParams({}, { replace: true });
    }
  }

  function handleQueryChange(newQuery: string) {
    setQuery(newQuery);
    setPage(0);
    setSelectedIds(new Set());
  }

  /** 저작권 필터 변경 — 페이지/선택 초기화 + 서버 요청 트리거 */
  function handleComplianceChange(value: "" | SourceComplianceStatus) {
    setComplianceStatus(value);
    setPage(0);
    setSelectedIds(new Set());
  }

  const { data: categories = [] } = useQuery({
    queryKey: categoryKeys.lists(),
    queryFn: () => categoryService.getAll(),
  });

  // 서버 사이드 페이지네이션 + 검색 + 카테고리 + 저작권 필터
  const { data: pageData, isLoading, isError, refetch } = useQuery({
    queryKey: sourceKeys.list({
      page,
      size: PAGE_SIZE,
      q: debouncedQuery || undefined,
      categoryId: categoryId || undefined,
      complianceStatus: complianceStatus || undefined,
    }),
    queryFn: () => {
      const params = new URLSearchParams();
      params.set("page", String(page));
      params.set("size", String(PAGE_SIZE));
      if (debouncedQuery) params.set("q", debouncedQuery);
      if (categoryId) params.set("categoryId", categoryId);
      if (complianceStatus) params.set("complianceStatus", complianceStatus);
      return sourceService.getPage(params);
    },
  });

  // 소스별 7일간 기사 수집 건수
  const { data: articleCountsData } = useQuery({
    queryKey: sourceKeys.articleCounts(7),
    queryFn: () => sourceService.getArticleCounts(7),
  });
  const articleCounts = articleCountsData?.counts ?? {};

  // 마지막 파이프라인 실행 시각 (신선도 표시용)
  const { data: pipelineRuns } = useQuery({
    queryKey: ["pipeline", "runs", { size: 1 }],
    queryFn: () => {
      const params = new URLSearchParams();
      params.set("page", "0");
      params.set("size", "1");
      return pipelineService.listRuns(params);
    },
  });

  const sources = pageData?.content ?? [];
  const totalCount = pageData?.totalCount ?? 0;
  const totalPages = Math.ceil(totalCount / PAGE_SIZE);
  const grouped = groupSources(sources);
  const lastPipelineTime = pipelineRuns?.content?.[0]?.startedAt ?? null;

  const addPendingId = (id: string) =>
    setPendingIds((prev) => new Set(prev).add(id));
  const removePendingId = (id: string) =>
    setPendingIds((prev) => {
      const next = new Set(prev);
      next.delete(id);
      return next;
    });

  const { mutate: createSource, isPending: isCreating } = useMutation({
    mutationFn: sourceService.create,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: sourceKeys.all });
      toast.success("소스가 등록됐어요");
      setCreateOpen(false);
    },
    onError: (err) => toast.error(userFriendlyMessage(err, "등록하지 못했어요")),
  });

  /** 관리자 직접 추가 시 기본 법적 근거를 적용하고 즉시 활성화한다. */
  const handleCreateSource = (data: SourceCreateRequest) =>
    createSource({
      ...data,
      legalBasis: data.legalBasis ?? "QUOTATION_ONLY",
      summaryAllowed: data.summaryAllowed ?? true,
      fulltextAllowed: data.fulltextAllowed ?? false,
      crawlApproved: true,
      approvedBy: "admin-console",
    });

  const { mutate: verifySource } = useMutation({
    mutationFn: (id: string) => sourceService.verify(id),
    onMutate: (id: string) => addPendingId(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: sourceKeys.all });
      toast.success("재시도를 시작했어요");
    },
    onError: (err) => toast.error(userFriendlyMessage(err, "재시도에 실패했어요")),
    onSettled: (_, __, id) => removePendingId(id),
  });

  const { mutate: deleteSource } = useMutation({
    mutationFn: sourceService.delete,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: sourceKeys.all });
      toast.success("소스가 삭제됐어요");
    },
    onError: (err) => toast.error(userFriendlyMessage(err, "삭제하지 못했어요")),
  });

  const { mutate: approveSource } = useMutation({
    mutationFn: ({ id, approved }: { id: string; approved: boolean }) =>
      sourceService.approve(id, { approved, approvedBy: "admin-console" }),
    onMutate: ({ id }) => addPendingId(id),
    onSuccess: (_data, { approved }) => {
      qc.invalidateQueries({ queryKey: sourceKeys.all });
      toast.success(approved ? "승인됐어요" : "반려됐어요");
    },
    onError: (err) => toast.error(userFriendlyMessage(err, "처리에 실패했어요")),
    onSettled: (_, __, { id }) => removePendingId(id),
  });

  const handleRetry = (id: string) => verifySource(id);
  const handleArchive = (id: string) => approveSource({ id, approved: false });
  const handleRestore = (id: string) => approveSource({ id, approved: true });
  const handleDelete = (id: string) => setDeleteConfirmId(id);
  const handleOpenDetail = (source: Source) => setErrorDetailSource(source);

  const handleEdit = (source: Source) => {
    setSelectedSource(source);
    setEditOpen(true);
  };

  // 벌크 액션
  const handleToggleSelect = (id: string) => {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const handleToggleSelectAll = (sourceList: Source[]) => {
    const allSelected = sourceList.every((s) => selectedIds.has(s.id));
    if (allSelected) {
      setSelectedIds(new Set());
    } else {
      setSelectedIds(new Set(sourceList.map((s) => s.id)));
    }
  };

  const { mutate: bulkVerify, isPending: isBulkVerifying } = useMutation({
    mutationFn: (ids: string[]) => sourceService.bulkVerify(ids),
    onSuccess: (data) => {
      qc.invalidateQueries({ queryKey: sourceKeys.all });
      const parts = [`성공 ${data.successCount}`];
      if (data.failCount > 0) parts.push(`실패 ${data.failCount}`);
      toast.success(`${data.successCount + data.failCount}개 소스 연결 확인 완료 (${parts.join(" / ")})`);
      setSelectedIds(new Set());
    },
    onError: (err) => toast.error(userFriendlyMessage(err, "벌크 연결 확인에 실패했어요")),
  });

  const { mutate: bulkArchive, isPending: isBulkArchiving } = useMutation({
    mutationFn: (ids: string[]) => sourceService.bulkArchive(ids),
    onSuccess: (data) => {
      qc.invalidateQueries({ queryKey: sourceKeys.all });
      const parts = [`성공 ${data.successCount}`];
      if (data.failCount > 0) parts.push(`실패 ${data.failCount}`);
      toast.success(`${data.successCount + data.failCount}개 소스 보관 완료 (${parts.join(" / ")})`);
      setSelectedIds(new Set());
    },
    onError: (err) => toast.error(userFriendlyMessage(err, "벌크 보관에 실패했어요")),
  });

  const handleBulkVerify = () => {
    if (isBulkVerifying) return;
    bulkVerify([...selectedIds]);
  };

  const handleBulkArchive = () => {
    if (isBulkArchiving) return;
    bulkArchive([...selectedIds]);
  };

  const hasProblems =
    grouped.connectionError.length > 0 || grouped.pendingApproval.length > 0;

  // 헬스 요약 카드를 위한 수치 계산 (getHealthLevel 기반)
  const healthCounts = sources.reduce((acc, s) => {
    const level = getHealthLevel(s);
    acc[level] = (acc[level] || 0) + 1;
    return acc;
  }, {} as Record<HealthLevel, number>);

  // 헬스 필터 적용 — 카드 클릭 시 해당 레벨 소스만 표시
  function filterByHealth(list: Source[], ...levels: HealthLevel[]): Source[] {
    if (!healthFilter) return list;
    if (!levels.includes(healthFilter)) return [];
    return list.filter((s) => getHealthLevel(s) === healthFilter);
  }

  const filteredActive = filterByHealth(grouped.active, "healthy", "warning", "pending");
  const filteredError = filterByHealth(grouped.connectionError, "error");
  const filteredArchived = filterByHealth(grouped.archived, "archived");

  if (isLoading) {
    return (
      <div className="p-4 sm:p-6 space-y-2" role="status" aria-live="polite">
        <span className="sr-only">로딩 중...</span>
        {[1, 2, 3].map((i) => (
          <div key={i} className="h-16 animate-pulse rounded-xl bg-muted" />
        ))}
      </div>
    );
  }

  if (isError) {
    return (
      <div className="flex flex-col items-center gap-3 p-12 text-center">
        <p className="text-sm text-muted-foreground">소스 목록을 불러오지 못했어요</p>
        <Button variant="outline" size="sm" onClick={() => refetch()}>
          다시 시도
        </Button>
      </div>
    );
  }

  if (totalCount === 0 && !debouncedQuery && !categoryId) {
    return (
      <>
        <div className="p-4 sm:p-6">
          <SourcesEmptyState onAddClick={() => setCreateOpen(true)} />
        </div>
        <Dialog open={createOpen} onOpenChange={setCreateOpen}>
          <DialogContent className="max-w-lg">
            <DialogHeader>
              <DialogTitle>소스 추가</DialogTitle>
              <DialogDescription className="sr-only">
                새로운 뉴스 소스를 등록합니다
              </DialogDescription>
            </DialogHeader>
            <SourceCreateForm
              categories={categories}
              onSubmit={handleCreateSource}
              isPending={isCreating}
            />
          </DialogContent>
        </Dialog>
      </>
    );
  }

  return (
    <div className="p-4 sm:p-6 space-y-5">
      <SourcesHeader
        healthyCount={healthCounts.healthy ?? 0}
        warningCount={healthCounts.warning ?? 0}
        errorCount={healthCounts.error ?? 0}
        archivedCount={healthCounts.archived ?? 0}
        totalCount={totalCount}
        lastPipelineTime={lastPipelineTime}
        activeFilter={healthFilter}
        onAddClick={() => setCreateOpen(true)}
        onFilterClick={(filter) => {
          // 토글: 같은 카드 재클릭 시 필터 해제
          setHealthFilter((prev) => (prev === filter ? null : filter));
        }}
      />

      <SourceDistributionChart sources={sources} categories={categories} />

      <CoverageGapBanner />

      <SourceQualityDashboard sources={sources} articleCounts={articleCounts} />

      <ProblemSourcesSection
        connectionErrors={filteredError}
        categories={categories}
        pendingIds={pendingIds}
        onRetry={handleRetry}
        onOpenDetail={handleOpenDetail}
      />

      <div className="space-y-2">
        <CollapsibleSourcesTable
          mode="active"
          sources={filteredActive}
          categories={categories}
          articleCounts={articleCounts}
          defaultOpen={!hasProblems}
          query={query}
          onQueryChange={handleQueryChange}
          categoryId={categoryId}
          onCategoryChange={handleCategoryChange}
          complianceStatus={complianceStatus}
          onComplianceChange={handleComplianceChange}
          selectedIds={selectedIds}
          onToggleSelect={handleToggleSelect}
          onToggleSelectAll={handleToggleSelectAll}
          onBulkVerify={handleBulkVerify}
          onBulkArchive={handleBulkArchive}
          onEdit={handleEdit}
          onVerify={(id) => verifySource(id)}
          onCompliance={setComplianceSource}
          onArchive={handleArchive}
          onRestore={handleRestore}
          onDelete={handleDelete}
          onSourceClick={setAnalyticsSourceId}
        />

        <CollapsibleSourcesTable
          mode="archived"
          sources={filteredArchived}
          categories={categories}
          articleCounts={articleCounts}
          defaultOpen={false}
          query={query}
          onQueryChange={handleQueryChange}
          categoryId={categoryId}
          onCategoryChange={handleCategoryChange}
          selectedIds={selectedIds}
          onToggleSelect={handleToggleSelect}
          onToggleSelectAll={handleToggleSelectAll}
          onBulkVerify={handleBulkVerify}
          onBulkArchive={handleBulkArchive}
          onEdit={handleEdit}
          onVerify={(id) => verifySource(id)}
          onCompliance={setComplianceSource}
          onArchive={handleArchive}
          onRestore={handleRestore}
          onDelete={handleDelete}
          onSourceClick={setAnalyticsSourceId}
        />
      </div>

      {/* 페이지네이션 */}
      {totalPages > 1 && (
        <div className="flex items-center justify-between text-sm text-muted-foreground">
          <span>
            전체 {totalCount}건 중 {page * PAGE_SIZE + 1}~
            {Math.min((page + 1) * PAGE_SIZE, totalCount)}건
          </span>
          <div className="flex items-center gap-1">
            <Button
              variant="outline"
              size="sm"
              disabled={page === 0}
              onClick={() => { setPage((p) => p - 1); setSelectedIds(new Set()); }}
            >
              <ChevronLeft className="h-4 w-4" />
              이전
            </Button>
            <Button
              variant="outline"
              size="sm"
              disabled={page >= totalPages - 1}
              onClick={() => { setPage((p) => p + 1); setSelectedIds(new Set()); }}
            >
              다음
              <ChevronRight className="h-4 w-4" />
            </Button>
          </div>
        </div>
      )}

      {/* Create Dialog */}
      <Dialog open={createOpen} onOpenChange={setCreateOpen}>
        <DialogContent className="max-w-lg">
          <DialogHeader>
            <DialogTitle>소스 추가</DialogTitle>
            <DialogDescription className="sr-only">
              새로운 뉴스 소스를 등록합니다
            </DialogDescription>
          </DialogHeader>
          <SourceCreateForm
            categories={categories}
            onSubmit={handleCreateSource}
            isPending={isCreating}
          />
        </DialogContent>
      </Dialog>

      {/* Edit Modal */}
      <SourceEditModal
        source={selectedSource}
        categories={categories}
        open={editOpen}
        onClose={() => {
          setEditOpen(false);
          setSelectedSource(null);
        }}
      />

      {/* Compliance Modal */}
      <SourceComplianceModal
        open={complianceSource !== null}
        source={complianceSource}
        onClose={() => setComplianceSource(null)}
      />

      {/* Error Detail Drawer */}
      <ErrorDetailDrawer
        source={errorDetailSource}
        category={
          errorDetailSource
            ? categories.find((c) => c.id === errorDetailSource.categoryId)
            : undefined
        }
        isPending={errorDetailSource ? pendingIds.has(errorDetailSource.id) : false}
        onClose={() => setErrorDetailSource(null)}
        onRetry={handleRetry}
        onEdit={handleEdit}
        onCompliance={setComplianceSource}
        onArchive={handleArchive}
        onDelete={handleDelete}
      />

      {/* Source Analytics Drawer */}
      <SourceAnalyticsDrawer
        sourceId={analyticsSourceId}
        onClose={() => setAnalyticsSourceId(null)}
      />

      {/* Delete Confirmation */}
      <ConfirmModal
        open={deleteConfirmId !== null}
        onOpenChange={(open) => {
          if (!open) setDeleteConfirmId(null);
        }}
        title="소스를 삭제할까요?"
        description="이 소스를 삭제하면 뉴스가 더 이상 수집되지 않아요"
        confirmLabel="삭제"
        variant="destructive"
        onConfirm={() => {
          if (deleteConfirmId) {
            deleteSource(deleteConfirmId);
            setDeleteConfirmId(null);
          }
        }}
      />
    </div>
  );
}
