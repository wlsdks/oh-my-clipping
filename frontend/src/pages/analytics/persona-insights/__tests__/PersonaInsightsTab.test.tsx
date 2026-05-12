import { render, screen, waitFor } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { PersonaInsightsTab } from "../../PersonaInsightsTab";
import { createQueryClientWrapper } from "@/test/queryClient";
import type {
  LiveSnapshotResponse,
  SignalsResponse,
} from "@/types/personaAnalytics";

vi.mock("@/services/personaAnalyticsService", () => ({
  personaAnalyticsService: {
    getLive: vi.fn(),
    getSignals: vi.fn(),
    getTrends: vi.fn(),
    getBatchRuns: vi.fn(),
    runBackfill: vi.fn(),
  },
}));

import { personaAnalyticsService } from "@/services/personaAnalyticsService";

function renderTab() {
  return render(
    <MemoryRouter>
      <PersonaInsightsTab />
    </MemoryRouter>,
    { wrapper: createQueryClientWrapper() },
  );
}

const liveHappy: LiveSnapshotResponse = {
  totals: {
    totalStyles: 18,
    presetCount: 12,
    customCount: 6,
    activeSubscriptions: 247,
    presetUsageRate: 0.78,
    customStyleRatio: 0.33,
    weekOverWeekDelta: null,
  },
  presetPortfolio: [
    {
      personaId: "p1",
      personaName: "테크 에디터",
      activeSubs: 42,
      weekOverWeekDelta: null,
      engagementRate: null,
      status: "HEALTHY",
      lastDeliveredAt: null,
    },
  ],
  customSummary: {
    totalCustomPersonas: 6,
    activeCustomSubscriptions: 12,
    newThisWeek: 0,
    recentPersonas: [],
  },
  asOf: "2026-04-13",
};

function signalsResponse(
  overrides: Partial<SignalsResponse> = {},
): SignalsResponse {
  return {
    asOfWeekIso: "2026-W16",
    asOfSnapshotDate: "2026-04-13",
    isWeekComplete: true,
    risks: [],
    growth: [],
    excluded: [],
    ...overrides,
  };
}

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(personaAnalyticsService.getBatchRuns).mockResolvedValue([]);
  vi.mocked(personaAnalyticsService.getTrends).mockResolvedValue({
    weeks: [],
    series: [],
  });
});

describe("PersonaInsightsTab", () => {
  it("위험 0 상태: 초록 '✓' 빈 상태 카드를 렌더링한다", async () => {
    vi.mocked(personaAnalyticsService.getLive).mockResolvedValue(liveHappy);
    vi.mocked(personaAnalyticsService.getSignals).mockResolvedValue(
      signalsResponse(),
    );

    renderTab();

    await waitFor(() => {
      expect(
        screen.getByText(/이번 주 주의가 필요한 페르소나가 없어요/),
      ).toBeInTheDocument();
    });
    // 성장 섹션도 기본 empty 문구.
    expect(
      screen.getByText(/이번 주는 눈에 띄는 성장 신호가 없어요/),
    ).toBeInTheDocument();
  });

  it("성장 카드가 있으면 해당 카드와 헤더 스트립 카운트를 렌더링한다", async () => {
    vi.mocked(personaAnalyticsService.getLive).mockResolvedValue(liveHappy);
    vi.mocked(personaAnalyticsService.getSignals).mockResolvedValue(
      signalsResponse({
        risks: [
          {
            personaId: "r1",
            personaName: "위험 페르소나",
            isPreset: true,
            riskType: "IDLE",
            persistentWeeks: 1,
            details: {
              type: "IDLE",
              consecutiveWeeks: 4,
              activeSubs: 0,
            },
          },
        ],
        growth: [
          {
            personaId: "g1",
            personaName: "성장 페르소나",
            isPreset: false,
            signalType: "FIRST_SUBSCRIPTION",
            persistentWeeks: 1,
            details: {
              type: "FIRST_SUBSCRIPTION",
              activeSubs: 3,
              daysSinceCreation: 5,
            },
          },
        ],
      }),
    );

    renderTab();

    await waitFor(() => {
      expect(screen.getByText("성장 페르소나")).toBeInTheDocument();
    });
    expect(screen.getByText("위험 페르소나")).toBeInTheDocument();

    const strip = screen.getByTestId("header-strip");
    // 주의 1 · 성장 1 · 유휴 1
    expect(strip.textContent).toContain("주의 1");
    expect(strip.textContent).toContain("성장 1");
    expect(strip.textContent).toContain("유휴 1");
  });

  it("분석 대상 외 페르소나를 포트폴리오 섹션 chip 으로 노출한다", async () => {
    vi.mocked(personaAnalyticsService.getLive).mockResolvedValue(liveHappy);
    vi.mocked(personaAnalyticsService.getSignals).mockResolvedValue(
      signalsResponse({
        excluded: [
          {
            personaId: "x1",
            personaName: "작은 커스텀",
            reason: "CHURN_BASELINE_BELOW_MIN",
          },
          {
            personaId: "x2",
            personaName: "발송 부족",
            reason: "ENGAGEMENT_DELIVERIES_BELOW_MIN",
          },
        ],
      }),
    );

    renderTab();

    await waitFor(() => {
      expect(screen.getByText(/분석 대상 외 2개/)).toBeInTheDocument();
    });
  });
});
