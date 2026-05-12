import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createQueryClientWrapper } from "@/test/queryClient";
import type { UserClippingRequest } from "@/types/user";
import type { KeywordTrendResponse, TopArticlesResponse } from "@/types/newsReport";
import type { UserMonthlyStatRow } from "@/types/insight";

vi.mock("@/services/userService", () => ({
  userService: {
    listClippingRequests: vi.fn(),
  },
}));
vi.mock("@/services/userHistoryService", () => ({
  userHistoryService: {
    getUserMonthlyStats: vi.fn(),
  },
}));
vi.mock("@/services/userIntelligenceService", () => ({
  userIntelligenceService: {
    getKeywordTrend: vi.fn(),
    getTopArticles: vi.fn(),
  },
}));

import { userService } from "@/services/userService";
import { userHistoryService } from "@/services/userHistoryService";
import { userIntelligenceService } from "@/services/userIntelligenceService";
import { UserNewsReportPage } from "../UserNewsReportPage";

function renderPage() {
  return render(<UserNewsReportPage />, { wrapper: createQueryClientWrapper() });
}

function approvedRequest(overrides: Partial<UserClippingRequest> = {}): UserClippingRequest {
  return {
    id: "req-1",
    requesterUserId: "u-1",
    requestName: "핀테크 뉴스",
    sourceName: "Example Economic Times",
    sourceUrl: "https://example.com",
    slackChannelId: "C123",
    personaName: "마케터",
    personaPrompt: "",
    status: "APPROVED",
    approvedCategoryId: "cat-1",
    approvedCategoryName: "핀테크",
    createdAt: "2026-04-01T00:00:00Z",
    updatedAt: "2026-04-01T00:00:00Z",
    deliveryState: "ACTIVE",
    collectingReady: true,
    totalSourceCount: 1,
    readySourceCount: 1,
    ...overrides,
  } as UserClippingRequest;
}

function statRow(overrides: Partial<UserMonthlyStatRow> = {}): UserMonthlyStatRow {
  return {
    id: "s1",
    categoryId: "cat-1",
    categoryName: "핀테크",
    statDate: "2026-04-01",
    itemsCollected: 0,
    itemsSummarized: 0,
    itemsSent: 0,
    topKeywords: [],
    avgImportanceScore: 0,
    ...overrides,
  };
}

function emptyTrend(): KeywordTrendResponse {
  return { period: { from: "2026-04-01", to: "2026-04-30" }, keywords: [] };
}

function emptyTopArticles(): TopArticlesResponse {
  return { items: [] };
}

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(userService.listClippingRequests).mockResolvedValue([]);
  vi.mocked(userHistoryService.getUserMonthlyStats).mockResolvedValue([]);
  vi.mocked(userIntelligenceService.getKeywordTrend).mockResolvedValue(emptyTrend());
  vi.mocked(userIntelligenceService.getTopArticles).mockResolvedValue(emptyTopArticles());
});

describe("UserNewsReportPage — 로딩 / 빈 상태", () => {
  it("요청 조회 중엔 스켈레톤을 보여준다", () => {
    vi.mocked(userService.listClippingRequests).mockReturnValue(new Promise(() => {}));
    const { container } = renderPage();
    expect(container.querySelectorAll(".animate-pulse").length).toBeGreaterThan(0);
  });

  it("승인된 요청이 없으면 '구독 중인 주제가 없어요' 빈 상태와 함께 통계 API는 호출되지 않는다", async () => {
    vi.mocked(userService.listClippingRequests).mockResolvedValue([
      approvedRequest({ id: "pending", status: "PENDING", approvedCategoryId: null }),
    ]);
    renderPage();
    expect(await screen.findByText("구독 중인 주제가 없어요")).toBeInTheDocument();
    expect(userHistoryService.getUserMonthlyStats).not.toHaveBeenCalled();
    expect(userIntelligenceService.getTopArticles).not.toHaveBeenCalled();
  });
});

describe("UserNewsReportPage — CSV 다운로드 링크", () => {
  it("선택된 월이 CSV 다운로드 href에 실린다", async () => {
    renderPage();
    const link = await screen.findByRole("link", { name: /CSV 다운로드/ });
    const now = new Date();
    const ym = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}`;
    expect(link.getAttribute("href")).toBe(`/api/user/reports/monthly.csv?yearMonth=${ym}`);
  });
});

describe("UserNewsReportPage — 데이터 렌더링 (insights-first)", () => {
  beforeEach(() => {
    vi.mocked(userService.listClippingRequests).mockResolvedValue([approvedRequest()]);
  });

  it("상승 키워드가 있으면 AI 인사이트 배너를 최상단에 노출한다", async () => {
    vi.mocked(userIntelligenceService.getKeywordTrend).mockResolvedValue({
      period: { from: "2026-04-01", to: "2026-04-30" },
      keywords: [{ keyword: "AI", totalCount: 30, changeRate: 0.8, dailyCounts: [] }],
    });
    renderPage();
    // "이번 달 ... 키워드가 전월 대비 ... 했어요" — 배너의 고유 문구로 찾는다.
    const banner = await screen.findByText(/전월 대비/);
    expect(banner.textContent).toContain("AI");
    expect(banner.textContent).toContain("80%");
  });

  it("topArticles가 있으면 '놓치면 아쉬운 N월 핵심 기사' 섹션을 렌더한다", async () => {
    vi.mocked(userIntelligenceService.getTopArticles).mockResolvedValue({
      items: [
        {
          summaryId: "a1",
          title: "금리 인하 발표",
          sourceLink: "https://ex.com/a1",
          importanceScore: 0.9,
          keywords: ["금리", "인하"],
          sentiment: null,
          eventType: null,
          createdAt: "2026-04-10T00:00:00Z",
        },
      ],
    });
    renderPage();

    expect(await screen.findByText(/핵심 기사/)).toBeInTheDocument();
    const articleLink = screen.getByRole("link", { name: /금리 인하 발표/ });
    expect(articleLink).toHaveAttribute("href", "https://ex.com/a1");
    expect(articleLink).toHaveAttribute("target", "_blank");
  });

  it("allKeywords가 있으면 키워드 트렌드 TOP 10 섹션과 카운트를 표시한다", async () => {
    vi.mocked(userIntelligenceService.getKeywordTrend).mockResolvedValue({
      period: { from: "2026-04-01", to: "2026-04-30" },
      keywords: [
        { keyword: "AI", totalCount: 42, changeRate: 0.3, dailyCounts: [] },
        { keyword: "규제", totalCount: 10, changeRate: -0.6, dailyCounts: [] },
      ],
    });
    renderPage();

    expect(await screen.findByText(/화제일까요/)).toBeInTheDocument();
    expect(screen.getByText("AI")).toBeInTheDocument();
    expect(screen.getByText("42건")).toBeInTheDocument();
    // -0.6이면 "하락" 뱃지
    expect(screen.getByText("하락")).toBeInTheDocument();
  });

  it("통계(KPI)와 주제별 분석을 함께 렌더한다", async () => {
    vi.mocked(userHistoryService.getUserMonthlyStats).mockResolvedValue([
      statRow({
        itemsCollected: 50,
        itemsSummarized: 30,
        itemsSent: 20,
        topKeywords: ["AI"],
      }),
    ]);
    renderPage();
    expect(await screen.findByText("받은 뉴스")).toBeInTheDocument();
    expect(screen.getByText("모은 기사")).toBeInTheDocument();
    expect(screen.getByText("AI 요약")).toBeInTheDocument();
    expect(screen.getByText("주제별 분석")).toBeInTheDocument();
    // topKeywords 칩이 노출된다
    const chips = screen.getAllByText("AI");
    expect(chips.length).toBeGreaterThanOrEqual(1);
  });
});

describe("UserNewsReportPage — 월 선택 변경", () => {
  it("월 셀렉터를 다른 값으로 바꾸면 stats 조회가 새 yearMonth로 재호출된다", async () => {
    vi.mocked(userService.listClippingRequests).mockResolvedValue([approvedRequest()]);
    renderPage();

    // 초기 호출이 현재 월로 이뤄질 때까지 대기
    await waitFor(() => {
      expect(userHistoryService.getUserMonthlyStats).toHaveBeenCalled();
    });
    const initialCallCount = vi.mocked(userHistoryService.getUserMonthlyStats).mock.calls.length;

    // SelectTrigger를 열고 다른 옵션 선택 (두 번째 옵션 = 1개월 전)
    const trigger = screen.getByRole("combobox");
    await userEvent.click(trigger);
    const options = await screen.findAllByRole("option");
    // options[0]은 현재 월 — 선택은 이전 월로 바꾼다
    await userEvent.click(options[1]);

    await waitFor(() => {
      expect(vi.mocked(userHistoryService.getUserMonthlyStats).mock.calls.length).toBeGreaterThan(
        initialCallCount
      );
    });
  });
});
