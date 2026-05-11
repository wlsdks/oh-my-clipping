// frontend/src/pages/source-quality/SourceQualityPage.tsx
import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { SourceEditModal } from "@/pages/sources/SourceEditModal";
import { sourceQualityKeys } from "@/queries/sourceQualityKeys";
import { sourceQualityService } from "@/services/sourceQualityService";
import { sourceService } from "@/services/sourceService";
import { categoryService } from "@/services/categoryService";
import { sourceKeys } from "@/queries/sourceKeys";
import { categoryKeys } from "@/queries/categoryKeys";
import { userFriendlyMessage } from "@/shared/lib/httpError";
import type { SourceQualityPeriod } from "@/types/sourceQuality";
import { SourceQualityKpiCards } from "./components/SourceQualityKpiCards";
import { SourceQualityTable } from "./components/SourceQualityTable";
import { SourceDeactivateConfirmDialog } from "./components/SourceDeactivateConfirmDialog";

const PERIOD_LABELS: Record<SourceQualityPeriod, string> = {
  "7d": "7일",
  "14d": "14일",
  "28d": "28일",
  "90d": "90일",
};

interface MutationArgs {
  sourceId: string;
  sourceName: string;
  expectedUpdatedAt: string;
}

/**
 * RSS 소스 품질 페이지.
 *
 * - 상단 KPI 4장 (검토 필요, 신호 부족, 평균 클릭률, 총 발송)
 * - 하단 소스 테이블 (클릭률 오름차순 기본 + 상태 필터 chips + 편집/일시중지/활성화)
 * - 편집 → `SourceEditModal` 를 공용으로 재사용 (단건 조회 + 카테고리 목록 프리페치)
 * - 수집 일시중지 → destructive 확정 다이얼로그 경유
 * - 활성화 → 다이얼로그 없이 즉시 호출 (실패 카운트 초기화)
 */
export function SourceQualityPage() {
  const [period, setPeriod] = useState<SourceQualityPeriod>("28d");
  const [editingSourceId, setEditingSourceId] = useState<string | null>(null);
  const [deactivateTarget, setDeactivateTarget] = useState<MutationArgs | null>(
    null,
  );
  const qc = useQueryClient();

  // 상단 summary — KPI + table 의 원본 데이터
  const summaryQuery = useQuery({
    queryKey: sourceQualityKeys.summary(period),
    queryFn: () => sourceQualityService.getSummary(period),
    staleTime: 60_000,
  });

  // 편집 모달 오픈 시에만 단건 조회 — ChangeDetectionStrip 의 updatedAt 감지용
  const sourceDetailQuery = useQuery({
    queryKey: sourceKeys.detail(editingSourceId ?? "none"),
    queryFn: () => sourceService.getById(editingSourceId as string),
    enabled: !!editingSourceId,
    staleTime: 10_000,
  });

  // 카테고리 드롭다운 — 편집 모달 의존. 캐시 5분.
  const categoriesQuery = useQuery({
    queryKey: categoryKeys.all,
    queryFn: () => categoryService.getAll(),
    staleTime: 5 * 60_000,
    enabled: !!editingSourceId,
  });

  // 수집 일시중지 — isActive=false + expectedUpdatedAt 로 stale edit 방어
  const deactivateMutation = useMutation<unknown, Error, MutationArgs>({
    mutationFn: (args) =>
      sourceService.update(args.sourceId, {
        isActive: false,
        expectedUpdatedAt: args.expectedUpdatedAt,
      }),
    onSuccess: async () => {
      toast.success("수집을 일시중지했습니다");
      await qc.invalidateQueries({ queryKey: sourceQualityKeys.summary(period) });
    },
    onError: (err) => {
      toast.error(userFriendlyMessage(err, "수집 일시중지 실패"));
      // 서버 상태와 다를 수 있으므로 재조회
      qc.invalidateQueries({ queryKey: sourceQualityKeys.summary(period) });
    },
  });

  // 활성화 — 확인 다이얼로그 없이 즉시, 서버가 실패 카운트 초기화
  const activateMutation = useMutation<unknown, Error, MutationArgs>({
    mutationFn: (args) =>
      sourceService.update(args.sourceId, {
        isActive: true,
        expectedUpdatedAt: args.expectedUpdatedAt,
      }),
    onSuccess: async () => {
      toast.success("수집을 재개했습니다 (실패 카운트 초기화)");
      await qc.invalidateQueries({ queryKey: sourceQualityKeys.summary(period) });
    },
    onError: (err) => {
      toast.error(userFriendlyMessage(err, "수집 재개 실패"));
      qc.invalidateQueries({ queryKey: sourceQualityKeys.summary(period) });
    },
  });

  return (
    <div className="p-4 sm:p-6 space-y-6 max-w-7xl mx-auto">
      <header className="flex flex-col sm:flex-row sm:items-end sm:justify-between gap-3">
        <div>
          <h1 className="text-xl font-semibold">RSS 소스 품질</h1>
          <p className="text-sm text-muted-foreground mt-1">
            클릭률·발송 성과로 소스 상태를 한눈에 보고 조치
          </p>
        </div>
        <div className="flex gap-1" role="radiogroup" aria-label="기간 필터">
          {(Object.keys(PERIOD_LABELS) as SourceQualityPeriod[]).map((p) => {
            const checked = p === period;
            return (
              <button
                key={p}
                type="button"
                role="radio"
                aria-checked={checked}
                onClick={() => setPeriod(p)}
                className={
                  checked
                    ? "px-3 py-1 text-xs rounded-full bg-primary text-primary-foreground"
                    : "px-3 py-1 text-xs rounded-full bg-muted text-muted-foreground hover:bg-muted/70"
                }
              >
                {PERIOD_LABELS[p]}
              </button>
            );
          })}
        </div>
      </header>

      {summaryQuery.isLoading && (
        <div className="text-muted-foreground text-sm" role="status">
          로딩 중…
        </div>
      )}

      {summaryQuery.error && (
        <div
          className="rounded-xl bg-[var(--status-danger-bg)] p-4 space-y-2"
          role="alert"
          data-testid="source-quality-error"
        >
          <div className="text-sm text-[var(--status-danger-text)]">
            소스 품질 데이터를 불러오지 못했어요
          </div>
          <button
            type="button"
            className="px-3 py-1.5 rounded-lg bg-primary text-primary-foreground text-xs disabled:opacity-60"
            disabled={summaryQuery.isFetching}
            onClick={() => summaryQuery.refetch()}
          >
            {summaryQuery.isFetching ? "다시 시도 중…" : "다시 시도"}
          </button>
        </div>
      )}

      {summaryQuery.data && (
        <>
          <SourceQualityKpiCards
            rows={summaryQuery.data.sourceQuality}
            period={period}
          />
          <SourceQualityTable
            rows={summaryQuery.data.sourceQuality}
            onEdit={(id) => setEditingSourceId(id)}
            onDeactivate={(args) => setDeactivateTarget(args)}
            onActivate={(args) => activateMutation.mutate(args)}
          />
        </>
      )}

      <SourceEditModal
        source={sourceDetailQuery.data ?? null}
        categories={categoriesQuery.data ?? []}
        open={!!editingSourceId && !!sourceDetailQuery.data}
        onClose={() => {
          setEditingSourceId(null);
          // 편집 결과가 summary 에 반영될 수 있으므로 재조회
          qc.invalidateQueries({ queryKey: sourceQualityKeys.summary(period) });
        }}
      />

      {deactivateTarget && (
        <SourceDeactivateConfirmDialog
          open={true}
          source={{
            sourceId: deactivateTarget.sourceId,
            sourceName: deactivateTarget.sourceName,
          }}
          onCancel={() => setDeactivateTarget(null)}
          onConfirm={() => {
            deactivateMutation.mutate(deactivateTarget);
            setDeactivateTarget(null);
          }}
        />
      )}
    </div>
  );
}
