import { useQuery } from "@tanstack/react-query";
import { RefreshCw } from "lucide-react";
import { toast } from "sonner";
import { costKeys } from "@/queries/costKeys";
import { costService } from "@/services/costService";
import type { CostDetail, LlmCostSummary } from "@/types/cost";
import { KpiCard, type KpiCardStatus } from "@/components/shared/KpiCard";
import { EmptyState } from "@/components/shared/EmptyState";
import { Button } from "@/components/ui/button";
import { ChannelCostTable } from "./ui/ChannelCostTable";
import { CostBudgetBar } from "./ui/CostBudgetBar";

interface CostTabProps {
  categoryId?: string;
  from: string;
  to: string;
  days: number;
}

export function CostTab({ categoryId, from, to, days }: CostTabProps) {
  const { data, isLoading, isError, isFetching, refetch } = useQuery({
    queryKey: costKeys.detail({ from, to, categoryId }),
    queryFn: () => costService.getDetail(from, to, categoryId),
    select: toCostSummary,
  });

  // 예산 설정 조회 — 게이지 표시용
  const { data: budget } = useQuery({
    queryKey: costKeys.budget(),
    queryFn: () => costService.getBudget(),
  });

  // 에러 재시도
  const handleRetry = async () => {
    try {
      await refetch();
    } catch {
      toast.error("데이터를 불러오지 못했어요. 잠시 후 다시 시도해 주세요");
    }
  };

  // 로딩 스켈레톤
  if (isLoading) {
    return (
      <div className="space-y-6">
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
          {[1, 2, 3, 4].map((i) => (
            <KpiCard key={i} label="" value="" loading />
          ))}
        </div>
        <ChannelCostTable rows={[]} loading />
      </div>
    );
  }

  // 에러 화면
  if (isError) {
    return (
      <div className="flex flex-col items-center justify-center py-16 text-center">
        <p className="text-lg font-medium text-foreground">
          데이터를 불러오지 못했어요
        </p>
        <p className="mt-1 text-sm text-muted-foreground">
          네트워크 상태를 확인하고 다시 시도해 주세요.
        </p>
        <Button
          variant="outline"
          size="sm"
          className="mt-4 gap-1.5"
          onClick={handleRetry}
          disabled={isFetching}
        >
          <RefreshCw size={14} className={isFetching ? "animate-spin" : ""} />
          {isFetching ? "시도 중..." : "다시 시도"}
        </Button>
      </div>
    );
  }

  // 빈 상태
  if (!data || data.rows.length === 0) {
    return (
      <EmptyState
        title="선택한 기간에 비용 데이터가 없어요"
        description="AI 요약 기능을 사용하면 비용 데이터가 자동으로 기록돼요"
      />
    );
  }

  // 예산 사용률 계산
  const hasBudget = budget != null && budget.monthlyBudgetUsd > 0;
  const budgetUsedPercent =
    hasBudget ? (data.totalEstimatedUsd / budget.monthlyBudgetUsd) * 100 : 0;

  // 비용 상태: 예산 대비 사용률에 따라 색상 표시
  const costStatus: KpiCardStatus | undefined = hasBudget
    ? budgetUsedPercent > 80
      ? "danger"
      : budgetUsedPercent > 60
        ? "warning"
        : "success"
    : undefined;

  return (
    <div className="space-y-6">
      {/* 예산 사용률 게이지 */}
      {hasBudget && (
        <CostBudgetBar
          usedPercent={budgetUsedPercent}
          spentUsd={data.totalEstimatedUsd}
          budgetUsd={budget.monthlyBudgetUsd}
        />
      )}

      {/* KPI 요약 카드 */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
        <KpiCard
          label="총 비용 (USD)"
          value={`$${data.totalEstimatedUsd.toFixed(2)}`}
          subtitle={`${days}일 기간 추정`}
          status={costStatus}
          tooltip="선택 기간 내 LLM(Gemini) 누적 호출 비용입니다."
        />
        <KpiCard
          label="요청 수"
          value={data.totalRequestCount.toLocaleString()}
          subtitle="AI API 호출 횟수"
          tooltip="선택 기간 내 LLM API 호출 횟수 (실패 포함)"
        />
        <KpiCard
          label="토큰 입력"
          value={data.totalTokensIn.toLocaleString()}
          subtitle="입력 토큰 합계"
          tooltip="LLM에 전송된 누적 입력 토큰 수입니다."
        />
        <KpiCard
          label="토큰 출력"
          value={data.totalTokensOut.toLocaleString()}
          subtitle="출력 토큰 합계"
          tooltip="LLM이 생성한 누적 출력 토큰 수입니다."
        />
      </div>

      {/* 채널별 비용 테이블 */}
      <div>
        <h3 className="text-sm font-semibold mb-3">채널별 비용 상세</h3>
        <ChannelCostTable rows={data.rows} />
      </div>
    </div>
  );
}

function toCostSummary(detail: CostDetail): LlmCostSummary {
  const totals = detail.rows.reduce(
    (acc, row) => ({
      requestCount: acc.requestCount + row.requestCount,
      tokensIn: acc.tokensIn + row.tokensIn,
      tokensOut: acc.tokensOut + row.tokensOut,
      estimatedUsd: acc.estimatedUsd + row.estimatedUsd,
    }),
    { requestCount: 0, tokensIn: 0, tokensOut: 0, estimatedUsd: 0 },
  );

  return {
    from: detail.from,
    to: detail.to,
    inputCostPerMillionUsd: detail.inputCostPerMillionUsd,
    outputCostPerMillionUsd: detail.outputCostPerMillionUsd,
    totalRequestCount: totals.requestCount,
    totalTokensIn: totals.tokensIn,
    totalTokensOut: totals.tokensOut,
    totalEstimatedUsd: totals.estimatedUsd,
    rows: detail.rows,
  };
}
