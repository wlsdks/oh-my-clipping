import { useState, useEffect } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Settings2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { categoryKeys } from "@/queries/categoryKeys";
import { pipelineKeys } from "@/queries/pipelineKeys";
import { categoryService } from "@/services/categoryService";
import { pipelineService } from "@/services/pipelineService";
import { PipelineHero } from "./PipelineHero";
import { PipelineControls } from "./PipelineControls";
import { PipelineHistory } from "./PipelineHistory";
import { ReviewAccuracySection } from "./ReviewAccuracySection";

export function PipelinePage() {
  const queryClient = useQueryClient();
  const [searchParams, setSearchParams] = useSearchParams();
  // Persona Insights "최근 발송 기사 보기" CTA에서 전달되는 페르소나 필터를 읽는다.
  const personaIdFilter = searchParams.get("personaId") ?? undefined;

  const [categoryId, setCategoryId] = useState("");
  // activeRunId: 현재 실행 중인 run의 폴링 상태 (URL과 별개)
  const [activeRunId, setActiveRunId] = useState<string | null>(null);

  // URL ?runId= 파라미터 — Slack 드릴다운 링크에서 이력 행을 자동 펼침
  const urlRunId = searchParams.get("runId");

  const openRunDetail = (runId: string) => {
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev);
      next.set("runId", runId);
      return next;
    });
  };

  const closeRunDetail = () => {
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev);
      next.delete("runId");
      return next;
    });
  };

  // 카테고리 목록 조회
  const { data: categories = [] } = useQuery({
    queryKey: categoryKeys.lists(),
    queryFn: () => categoryService.getAll(),
  });

  // 선택된 카테고리의 가장 최근 파이프라인 실행 조회
  const { data: latestRun = null, isLoading: isLatestLoading } = useQuery({
    queryKey: pipelineKeys.latest(categoryId),
    queryFn: () => pipelineService.getLatest(categoryId),
    enabled: !!categoryId,
  });

  // 활성 실행 폴링 (RUNNING 상태일 때 3초 간격)
  const { data: activeRun } = useQuery({
    queryKey: pipelineKeys.runDetail(activeRunId!),
    queryFn: () => pipelineService.getRunDetail(activeRunId!),
    enabled: !!activeRunId,
    refetchInterval: (query) => {
      const run = query.state.data;
      return run?.status === "RUNNING" ? 3000 : false;
    },
  });

  // 실행 완료 시 관련 쿼리 갱신 및 폴링 중단
  useEffect(() => {
    if (activeRunId && activeRun && activeRun.status !== "RUNNING") {
      queryClient.invalidateQueries({ queryKey: pipelineKeys.latest(categoryId) });
      queryClient.invalidateQueries({ queryKey: pipelineKeys.runs() });
      setActiveRunId(null);
    }
  }, [activeRun?.status, activeRunId, categoryId, queryClient]);

  // 히어로에 표시할 run: 활성 실행 우선, 없으면 최근 실행
  const heroRun = activeRun ?? latestRun;
  const isHeroLoading = !!categoryId && isLatestLoading;

  return (
    <div className="p-4 sm:p-6 space-y-5">
      {/* 헤더: 제목 + 설정 링크 */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">파이프라인</h1>
          <p className="text-sm text-muted-foreground mt-1">
            수집 → 요약 → 다이제스트 전체 파이프라인의 실행 기록입니다. 스케줄에 의한 자동 실행과 관리자가 수동으로 트리거한 실행이 모두 한 테이블에 기록됩니다.
          </p>
        </div>
        <Button variant="outline" size="sm" asChild>
          <Link to="/admin/runtime">
            <Settings2 className="mr-2 h-4 w-4" />
            파이프라인 설정
          </Link>
        </Button>
      </div>

      <PipelineHero run={heroRun} isLoading={isHeroLoading} />

      <PipelineControls
        categories={categories}
        selectedCategoryId={categoryId}
        onCategoryChange={setCategoryId}
        onRunStarted={setActiveRunId}
        isRunning={!!activeRunId}
      />

      <ReviewAccuracySection />

      <div>
        <h2 className="text-lg font-semibold mb-3">실행 이력</h2>
        <PipelineHistory
          categories={categories}
          personaIdFilter={personaIdFilter}
          expandedRunId={urlRunId}
          onExpandChange={(runId) => {
            if (runId) openRunDetail(runId);
            else closeRunDetail();
          }}
        />
      </div>
    </div>
  );
}
