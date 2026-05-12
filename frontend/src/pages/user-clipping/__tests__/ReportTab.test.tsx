import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ReportTab } from "../ReportTab";
import { createQueryClientWrapper } from "@/test/queryClient";
import type { UserMonthlyStatRow } from "@/types/insight";

vi.mock("@/services/userHistoryService", () => ({
  userHistoryService: {
    getUserMonthlyStats: vi.fn(),
  },
}));
vi.mock("@/services/newsIntelligenceService", () => ({
  newsIntelligenceService: {
    getUserKeywordTrend: vi.fn(),
  },
}));

import { userHistoryService } from "@/services/userHistoryService";
import { newsIntelligenceService } from "@/services/newsIntelligenceService";

function renderTab(approvedCategoryIds: string[] = ["cat-A"]) {
  return render(<ReportTab approvedCategoryIds={approvedCategoryIds} />, {
    wrapper: createQueryClientWrapper(),
  });
}

const baseStatRow = (overrides: Partial<UserMonthlyStatRow> = {}): UserMonthlyStatRow => ({
  id: "r1",
  categoryId: "cat-A",
  categoryName: "마케팅",
  statDate: "2026-04-01",
  itemsCollected: 0,
  itemsSummarized: 0,
  itemsSent: 0,
  topKeywords: [],
  avgImportanceScore: 0,
  ...overrides,
});

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(userHistoryService.getUserMonthlyStats).mockResolvedValue([]);
  vi.mocked(newsIntelligenceService.getUserKeywordTrend).mockResolvedValue({
    period: { from: "2026-04-01", to: "2026-04-30" },
    keywords: [],
  });
});

describe("ReportTab — 빈 상태 / 로딩 / 에러", () => {
  it("승인된 카테고리가 없으면 Empty State를 보여주고 API를 호출하지 않는다", () => {
    renderTab([]);
    expect(screen.getByText("구독 중인 주제가 없어요")).toBeInTheDocument();
    expect(userHistoryService.getUserMonthlyStats).not.toHaveBeenCalled();
  });

  it("로딩 중엔 스켈레톤 블록들을 보여준다", () => {
    vi.mocked(userHistoryService.getUserMonthlyStats).mockReturnValue(new Promise(() => {}));
    const { container } = renderTab();
    const pulses = container.querySelectorAll(".animate-pulse");
    expect(pulses.length).toBeGreaterThan(0);
  });

  it("stats 조회 실패 시 에러 카드와 재시도 버튼을 노출한다", async () => {
    vi.mocked(userHistoryService.getUserMonthlyStats).mockRejectedValue(new Error("boom"));
    renderTab();
    expect(await screen.findByText("월간 리포트를 불러오지 못했어요")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /다시 시도/ })).toBeInTheDocument();
  });

  it("재시도 버튼 클릭 시 refetch를 트리거한다 (스켈레톤으로 되돌아가지 않는다)", async () => {
    vi.mocked(userHistoryService.getUserMonthlyStats)
      .mockRejectedValueOnce(new Error("boom"))
      .mockResolvedValueOnce([baseStatRow({ itemsSent: 5 })]);
    renderTab();

    const retryBtn = await screen.findByRole("button", { name: /다시 시도/ });
    await userEvent.click(retryBtn);

    await waitFor(() => {
      // 성공 응답이 들어오면 "받은 뉴스" 지표 카드가 렌더된다.
      expect(screen.getByText("받은 뉴스")).toBeInTheDocument();
    });
  });
});

describe("ReportTab — 데이터 렌더링", () => {
  it("승인 카테고리가 있지만 이번 달 발송이 0이면 '이번 달 아직 발송된 뉴스가 없어요' 카드", async () => {
    vi.mocked(userHistoryService.getUserMonthlyStats).mockResolvedValue([
      baseStatRow({ itemsCollected: 100, itemsSummarized: 10, itemsSent: 0 }),
    ]);
    renderTab();
    expect(await screen.findByText("이번 달 아직 발송된 뉴스가 없어요")).toBeInTheDocument();
  });

  it("stats가 있으면 통계 카드와 주제별 분석 바를 렌더링한다", async () => {
    vi.mocked(userHistoryService.getUserMonthlyStats).mockResolvedValue([
      baseStatRow({
        categoryId: "cat-A",
        categoryName: "마케팅",
        itemsCollected: 50,
        itemsSummarized: 20,
        itemsSent: 10,
        topKeywords: ["AI"],
      }),
      baseStatRow({
        id: "r2",
        categoryId: "cat-A",
        categoryName: "마케팅",
        statDate: "2026-04-02",
        itemsSent: 5,
      }),
    ]);
    renderTab();

    // 카드 3종 렌더
    expect(await screen.findByText("받은 뉴스")).toBeInTheDocument();
    expect(screen.getByText("모은 기사")).toBeInTheDocument();
    expect(screen.getByText("AI 요약")).toBeInTheDocument();
    // 주제별 분석 — 같은 카테고리 2개 행이 1개 바로 합쳐진다
    expect(screen.getByText("주제별 분석")).toBeInTheDocument();
    const categoryLabels = screen.getAllByText("마케팅");
    expect(categoryLabels).toHaveLength(1);
  });

  it("임계값 이상 상승 키워드가 있으면 AI 배너가 뜨고 앵커 링크를 건다", async () => {
    vi.mocked(userHistoryService.getUserMonthlyStats).mockResolvedValue([]);
    vi.mocked(newsIntelligenceService.getUserKeywordTrend).mockResolvedValue({
      period: { from: "2026-04-01", to: "2026-04-30" },
      keywords: [
        { keyword: "AI", totalCount: 20, changeRate: 0.5, dailyCounts: [] },
        { keyword: "노이즈", totalCount: 2, changeRate: 3.0, dailyCounts: [] },
      ],
    });
    renderTab();

    const banner = await screen.findByText(/키워드가/);
    expect(banner).toBeInTheDocument();
    // 배너 문구 안에 선택된 키워드와 변화율이 포함된다
    expect(banner.textContent).toContain("AI");
    expect(screen.getAllByText(/\+50%/).length).toBeGreaterThan(0);
    // 앵커 링크 — 클릭하면 키워드 트렌드 섹션으로 스크롤
    const link = screen.getByRole("link", { name: /이번 달/ });
    expect(link).toHaveAttribute("href", "#keyword-trend-section");
  });

  it("임계값 미달이면 AI 배너가 안 뜬다", async () => {
    vi.mocked(userHistoryService.getUserMonthlyStats).mockResolvedValue([]);
    vi.mocked(newsIntelligenceService.getUserKeywordTrend).mockResolvedValue({
      period: { from: "2026-04-01", to: "2026-04-30" },
      keywords: [{ keyword: "slight", totalCount: 50, changeRate: 0.05, dailyCounts: [] }],
    });
    renderTab();
    // 스켈레톤이 빠지고 본문 렌더 대기
    await screen.findByText("받은 뉴스");
    expect(screen.queryByText(/키워드가/)).not.toBeInTheDocument();
  });
});

describe("ReportTab — CSV 다운로드", () => {
  it("선택된 월을 쿼리 파라미터로 가진 다운로드 링크를 렌더한다", async () => {
    vi.mocked(userHistoryService.getUserMonthlyStats).mockResolvedValue([]);
    renderTab();
    const link = await screen.findByRole("link", { name: /CSV 다운로드/ });
    expect(link).toHaveAttribute("href", expect.stringMatching(/\/api\/user\/reports\/monthly\.csv\?yearMonth=\d{4}-\d{2}/));
  });
});
