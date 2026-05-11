import { render, screen } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { WeeklyTrendsChart } from "../WeeklyTrendsChart";
import type { WeeklyTrendsResponse } from "@/types/personaAnalytics";

// personaAnalyticsService 전체를 모킹한다
vi.mock("@/services/personaAnalyticsService", () => ({
  personaAnalyticsService: {
    getTrends: vi.fn(),
    runBackfill: vi.fn(),
    getLive: vi.fn(),
    getBatchRuns: vi.fn(),
  },
}));

import { personaAnalyticsService } from "@/services/personaAnalyticsService";

const mockTrendsResponse: WeeklyTrendsResponse = {
  weeks: ["2026-W10", "2026-W11", "2026-W12"],
  series: [
    {
      personaId: "p1",
      personaName: "테크 에디터",
      isPreset: true,
      activeSubs: [10, 12, 15],
      engagedUsers: [5, 6, 8],
      deliveredCount: [20, 24, 30],
    },
    {
      personaId: "p2",
      personaName: "경제 분석",
      isPreset: false,
      activeSubs: [3, 4, 5],
      engagedUsers: [1, 2, 3],
      deliveredCount: [6, 8, 10],
    },
  ],
};

function createWrapper() {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return function Wrapper({ children }: { children: React.ReactNode }) {
    return <QueryClientProvider client={qc}>{children}</QueryClientProvider>;
  };
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe("WeeklyTrendsChart", () => {
  it("로딩 중에는 스켈레톤을 표시한다", () => {
    // getTrends 가 resolve 되지 않는 pending Promise 를 반환
    vi.mocked(personaAnalyticsService.getTrends).mockReturnValue(new Promise(() => {}));

    render(<WeeklyTrendsChart />, { wrapper: createWrapper() });

    expect(screen.getByTestId("trends-skeleton")).toBeInTheDocument();
  });

  it("데이터가 없으면 집계 CTA 를 표시한다", async () => {
    const emptyResponse: WeeklyTrendsResponse = { weeks: [], series: [] };
    vi.mocked(personaAnalyticsService.getTrends).mockResolvedValue(emptyResponse);

    render(<WeeklyTrendsChart />, { wrapper: createWrapper() });

    expect(await screen.findByTestId("trends-empty")).toBeInTheDocument();
    expect(screen.getByText(/아직 집계된 트렌드 데이터가 없어요/)).toBeInTheDocument();
    // BackfillButton CTA 가 존재한다
    expect(screen.getAllByRole("button").some((b) => b.textContent?.includes("데이터 집계"))).toBe(true);
  });

  it("데이터가 있으면 차트 컨테이너를 표시한다", async () => {
    vi.mocked(personaAnalyticsService.getTrends).mockResolvedValue(mockTrendsResponse);

    render(<WeeklyTrendsChart />, { wrapper: createWrapper() });

    expect(await screen.findByTestId("trends-chart-container")).toBeInTheDocument();
  });

  it("차트 헤더 제목이 보인다", async () => {
    vi.mocked(personaAnalyticsService.getTrends).mockResolvedValue(mockTrendsResponse);

    render(<WeeklyTrendsChart />, { wrapper: createWrapper() });

    expect(await screen.findByText("페르소나 주간 트렌드")).toBeInTheDocument();
  });
});
