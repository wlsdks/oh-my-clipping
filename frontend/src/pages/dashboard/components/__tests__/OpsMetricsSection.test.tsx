import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { describe, it, expect, vi, beforeEach } from "vitest";

import type { PipelineRunsPage } from "@/types/pipeline";
import type { DeliveryLogsPage } from "@/types/delivery";
import type { OpsSummary } from "@/types/ops";

vi.mock("../../hooks/useOpsMetricsData");
vi.mock("@/services/costService", () => ({
  costService: {
    getOverview: vi.fn().mockResolvedValue({
      totalCostUsd: 3.5,
      budgetUsd: 10,
      budgetUsedPercent: 35,
      dailyBreakdown: [],
    }),
  },
}));

import { useOpsMetricsData } from "../../hooks/useOpsMetricsData";
import { OpsMetricsSection } from "../OpsMetricsSection";

const mockHook = vi.mocked(useOpsMetricsData);

function renderSection() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <MemoryRouter>
        <OpsMetricsSection />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

function baseForecast(): ReturnType<typeof useOpsMetricsData> {
  const emptyPipeline: PipelineRunsPage = { content: [], totalCount: 0, page: 0, size: 100 };
  const emptyDelivery: DeliveryLogsPage = { content: [], totalCount: 0, page: 0, size: 100 };
  const emptyOpsSummary: OpsSummary = {
    delivery: { total: 0, sent: 0, failed: 0 },
    pipeline: { total: 0, success: 0, failed: 0 },
  };
  return {
    forecast: {
      expectedRunCount: 4,
      expectedDigestCount: 12,
      nextRunAtKst: new Date(Date.now() + 30 * 60_000).toISOString(),
    },
    pipelineSummary: emptyPipeline,
    deliverySummary: emptyDelivery,
    engagement: undefined,
    opsSummary: emptyOpsSummary,
    isLoading: false,
    error: null,
  };
}

describe("OpsMetricsSection", () => {
  beforeEach(() => vi.clearAllMocks());

  it('forecast 배너 "오늘 예정" + 파이프라인/다이제스트 횟수 렌더', () => {
    mockHook.mockReturnValue(baseForecast());
    renderSection();
    expect(screen.getByText("오늘 예정")).toBeInTheDocument();
    expect(screen.getByText(/파이프라인 4회/)).toBeInTheDocument();
    expect(screen.getByText(/다이제스트 12건/)).toBeInTheDocument();
  });

  it('파이프라인 실패 0건 시 "✓ 이상 없음" 표시', () => {
    mockHook.mockReturnValue(baseForecast());
    renderSection();
    expect(screen.getByTestId("pipeline-ok")).toBeInTheDocument();
  });

  it('실패 2건 시 "2건 실패" 표시 (destructive)', () => {
    const data = baseForecast();
    // status 카운트는 이제 opsSummary 기반 — 서버 집계 값을 직접 세팅한다
    data.opsSummary = {
      delivery: { total: 0, sent: 0, failed: 0 },
      pipeline: { total: 3, success: 1, failed: 2 },
    };
    mockHook.mockReturnValue(data);
    renderSection();
    expect(screen.getByTestId("pipeline-failed")).toHaveTextContent("2건 실패");
  });
});
